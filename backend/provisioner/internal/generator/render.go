package generator

import (
	"bytes"
	"encoding/json"
	"encoding/xml"
	"errors"
	"fmt"
	"io/fs"
	"path"
	"sort"
	"strings"
	"text/template"
	"unicode"

	"go.yaml.in/yaml/v3"

	"devforge/provisioner/internal/model"
)

const (
	templateSuffix       = ".tmpl"
	legacyTemplateSuffix = ".template"
	maxProjectBytes      = 50 << 20

	OriginBase     = "base"
	OriginDevForge = "devforge"
)

type File struct {
	Path    string
	Content []byte
	Mode    fs.FileMode
	Origin  string
}

type Project struct {
	Files []File
}

type renderData struct {
	ServiceName  string
	ClassName    string
	PackageName  string
	PackagePath  string
	Description  string
	Vars         map[string]string
	Features     map[string][]string
	Dependencies []Dependency
}

type layer struct {
	fsys   fs.FS
	origin string
}

func Render(fsys fs.FS, req model.GenerationRequest) (*Project, error) {
	manifest, err := loadManifest(fsys)
	if err != nil {
		return nil, err
	}
	sel, err := resolveSelection(manifest, req.Features)
	if err != nil {
		return nil, err
	}
	requestVars := map[string]string{}
	for _, v := range manifest.Variables {
		if v.Default != "" {
			requestVars[v.Name] = v.Default
		}
	}
	for k, v := range req.Variables {
		requestVars[k] = v
	}
	req.Variables = requestVars
	vars := variables(req)

	files := map[string]File{}
	if manifest.SchemaVersion >= 2 {
		base, err := fs.Sub(fsys, "base")
		if err != nil {
			return nil, err
		}
		layers := []layer{{base, OriginBase}}
		for _, o := range sel.options {
			dir := path.Join("features", o.Option.ID)
			if info, err := fs.Stat(fsys, dir); err == nil && info.IsDir() {
				sub, err := fs.Sub(fsys, dir)
				if err != nil {
					return nil, err
				}
				layers = append(layers, layer{sub, "feature:" + o.Option.ID})
			}
		}
		data := renderData{
			ServiceName:  vars["SERVICE_NAME"],
			ClassName:    vars["CLASS_NAME"],
			PackageName:  vars["PACKAGE_NAME"],
			PackagePath:  vars["PACKAGE_PATH"],
			Description:  vars["DESCRIPTION"],
			Vars:         req.Variables,
			Features:     sel.features,
			Dependencies: sel.dependencies,
		}
		if data.Vars == nil {
			data.Vars = map[string]string{}
		}
		funcs := templateFuncs(sel)
		for _, l := range layers {
			if err := renderLayer(l, vars, files, false, func(name string, content []byte) ([]byte, error) {
				return executeTemplate(name, content, funcs, data)
			}); err != nil {
				return nil, err
			}
		}
	} else {
		if err := renderLayer(layer{fsys, OriginBase}, vars, files, true, func(_ string, content []byte) ([]byte, error) {
			return substitute(content, vars), nil
		}); err != nil {
			return nil, err
		}
	}

	manifestFile, err := devForgeManifest(req, sel)
	if err != nil {
		return nil, err
	}
	files[manifestFile.Path] = manifestFile

	project := &Project{Files: make([]File, 0, len(files))}
	total := 0
	for _, f := range files {
		total += len(f.Content)
		project.Files = append(project.Files, f)
	}
	if total > maxProjectBytes {
		return nil, fmt.Errorf("generated project exceeds %d bytes", maxProjectBytes)
	}
	sort.Slice(project.Files, func(i, j int) bool { return project.Files[i].Path < project.Files[j].Path })
	return project, nil
}

type transform func(name string, content []byte) ([]byte, error)

func renderLayer(l layer, vars map[string]string, files map[string]File, legacy bool, render transform) error {
	return fs.WalkDir(l.fsys, ".", func(p string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if d.Type()&fs.ModeSymlink != 0 {
			return fmt.Errorf("symlinks are not allowed in templates: %s", p)
		}
		if d.IsDir() {
			return nil
		}
		content, err := fs.ReadFile(l.fsys, p)
		if err != nil {
			return err
		}
		target := resolvePathTokens(p, vars)
		switch {
		case legacy:
			target = strings.TrimSuffix(target, legacyTemplateSuffix)
			if content, err = render(p, content); err != nil {
				return err
			}
		case strings.HasSuffix(p, templateSuffix):
			target = strings.TrimSuffix(target, templateSuffix)
			if content, err = render(p, content); err != nil {
				return err
			}
			if len(bytes.TrimSpace(content)) == 0 {
				return nil
			}
		}
		if !fs.ValidPath(target) {
			return fmt.Errorf("template path %q resolves outside the project", p)
		}
		mode := fs.FileMode(0o644)
		if info, err := d.Info(); err == nil && info.Mode()&0o111 != 0 {
			mode = 0o755
		}
		files[target] = File{Path: target, Content: content, Mode: mode, Origin: l.origin}
		return nil
	})
}

func variables(req model.GenerationRequest) map[string]string {
	vars := map[string]string{}
	for k, v := range req.Variables {
		vars[k] = v
	}
	vars["SERVICE_NAME"] = req.ServiceName
	vars["CLASS_NAME"] = req.ClassName
	vars["PACKAGE_NAME"] = req.PackageName
	vars["PACKAGE_PATH"] = strings.ReplaceAll(req.PackageName, ".", "/")
	vars["DESCRIPTION"] = req.Description
	return vars
}

func resolvePathTokens(p string, vars map[string]string) string {
	res := strings.ReplaceAll(p, "__PACKAGE_PATH__", vars["PACKAGE_PATH"])
	res = strings.ReplaceAll(res, "__CLASS_NAME__", vars["CLASS_NAME"])
	return strings.ReplaceAll(res, "__SERVICE_NAME__", vars["SERVICE_NAME"])
}

func substitute(content []byte, vars map[string]string) []byte {
	out := string(content)
	for key, val := range vars {
		out = strings.ReplaceAll(out, "{{"+key+"}}", val)
	}
	return []byte(out)
}

func executeTemplate(name string, content []byte, funcs template.FuncMap, data any) ([]byte, error) {
	tmpl, err := template.New(name).Delims("[[", "]]").Option("missingkey=error").Funcs(funcs).Parse(string(content))
	if err != nil {
		return nil, fmt.Errorf("parsing %s: %w", name, err)
	}
	var buf bytes.Buffer
	if err := tmpl.Execute(&buf, data); err != nil {
		return nil, fmt.Errorf("rendering %s: %w", name, err)
	}
	return buf.Bytes(), nil
}

func templateFuncs(sel selection) template.FuncMap {
	return template.FuncMap{
		"has": sel.has,
		"selected": func(group string) string {
			if opts := sel.features[group]; len(opts) > 0 {
				return opts[0]
			}
			return ""
		},
		"lower":  strings.ToLower,
		"upper":  strings.ToUpper,
		"pascal": pascal,
		"camel": func(s string) string {
			p := []rune(pascal(s))
			if len(p) > 0 {
				p[0] = unicode.ToLower(p[0])
			}
			return string(p)
		},
		"kebab": func(s string) string { return strings.Join(words(s), "-") },
		"snake": func(s string) string { return strings.Join(words(s), "_") },
		"quote": func(s string) (string, error) {
			b, err := json.Marshal(s)
			return string(b), err
		},
		"xml": func(s string) (string, error) {
			var b strings.Builder
			err := xml.EscapeText(&b, []byte(s))
			return b.String(), err
		},
		"gradleConfiguration": gradleConfiguration,
	}
}

func words(s string) []string {
	parts := strings.FieldsFunc(strings.ToLower(s), func(r rune) bool {
		return !unicode.IsLetter(r) && !unicode.IsDigit(r)
	})
	return parts
}

func pascal(s string) string {
	var b strings.Builder
	for _, w := range words(s) {
		r := []rune(w)
		r[0] = unicode.ToUpper(r[0])
		b.WriteString(string(r))
	}
	return b.String()
}

func gradleConfiguration(scope string) (string, error) {
	switch scope {
	case "", "compile":
		return "implementation", nil
	case "runtime":
		return "runtimeOnly", nil
	case "provided":
		return "compileOnly", nil
	case "test":
		return "testImplementation", nil
	case "test-runtime":
		return "testRuntimeOnly", nil
	case "annotation-processor":
		return "annotationProcessor", nil
	case "development":
		return "developmentOnly", nil
	default:
		return "", errors.New("unsupported dependency scope " + scope)
	}
}

type devForgeFile struct {
	Template  string              `yaml:"template"`
	Version   string              `yaml:"version"`
	Variables map[string]string   `yaml:"variables"`
	Features  map[string][]string `yaml:"features,omitempty"`
}

func devForgeManifest(req model.GenerationRequest, sel selection) (File, error) {
	vars := map[string]string{
		"SERVICE_NAME": req.ServiceName,
		"PACKAGE_NAME": req.PackageName,
		"CLASS_NAME":   req.ClassName,
	}
	for k, v := range req.Variables {
		vars[k] = v
	}
	content, err := yaml.Marshal(devForgeFile{
		Template:  req.TemplateName,
		Version:   req.TemplateVersion,
		Variables: vars,
		Features:  sel.features,
	})
	if err != nil {
		return File{}, err
	}
	return File{Path: ".devforge.yaml", Content: content, Mode: 0o644, Origin: OriginDevForge}, nil
}
