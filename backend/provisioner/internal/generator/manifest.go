package generator

import (
	"errors"
	"fmt"
	"io/fs"
	"regexp"
	"slices"

	"go.yaml.in/yaml/v3"
)

const manifestFile = "template.yaml"

const (
	KindChoice = "choice"
	KindMulti  = "multi"
)

// Feature group and option ids double as overlay directory names (features/<optionId>/).
var featureID = regexp.MustCompile(`^[a-z0-9][a-z0-9-]{0,49}$`)

// Manifest is the subset of template.yaml the generator needs; the backend owns full validation.
type Manifest struct {
	SchemaVersion int            `yaml:"schemaVersion"`
	Variables     []Variable     `yaml:"variables"`
	Features      []FeatureGroup `yaml:"features"`
}

type Variable struct {
	Name    string `yaml:"name"`
	Default string `yaml:"default"`
}

type FeatureGroup struct {
	ID      string          `yaml:"id"`
	Kind    string          `yaml:"kind"`
	Options []FeatureOption `yaml:"options"`
}

type FeatureOption struct {
	ID           string       `yaml:"id"`
	Dependencies []Dependency `yaml:"dependencies"`
}

// Dependency is build-tool neutral: Maven/Gradle use Group+Artifact, npm-style ecosystems leave
// Group empty. Scope uses Maven vocabulary (compile, runtime, test, provided) or "dev".
type Dependency struct {
	Group    string `yaml:"group"`
	Artifact string `yaml:"artifact"`
	Version  string `yaml:"version"`
	Scope    string `yaml:"scope"`
}

type selectedOption struct {
	Group  string
	Option FeatureOption
}

type selection struct {
	options      []selectedOption
	features     map[string][]string
	dependencies []Dependency
}

func (s selection) has(id string) bool {
	if _, ok := s.features[id]; ok {
		return true
	}
	for _, o := range s.options {
		if o.Option.ID == id {
			return true
		}
	}
	return false
}

func loadManifest(fsys fs.FS) (Manifest, error) {
	data, err := fs.ReadFile(fsys, manifestFile)
	if errors.Is(err, fs.ErrNotExist) {
		return Manifest{SchemaVersion: 1}, nil
	}
	if err != nil {
		return Manifest{}, fmt.Errorf("reading %s: %w", manifestFile, err)
	}
	var m Manifest
	if err := yaml.Unmarshal(data, &m); err != nil {
		return Manifest{}, fmt.Errorf("parsing %s: %w", manifestFile, err)
	}
	if m.SchemaVersion == 0 {
		m.SchemaVersion = 1
	}
	for _, g := range m.Features {
		if !featureID.MatchString(g.ID) {
			return Manifest{}, fmt.Errorf("invalid feature group id %q", g.ID)
		}
		for _, o := range g.Options {
			if !featureID.MatchString(o.ID) {
				return Manifest{}, fmt.Errorf("invalid feature option id %q", o.ID)
			}
		}
	}
	return m, nil
}

// resolveSelection maps the requested features onto the manifest, in manifest order so overlays
// and dependencies are applied deterministically regardless of JSON map ordering.
func resolveSelection(m Manifest, requested map[string][]string) (selection, error) {
	for group := range requested {
		if !slices.ContainsFunc(m.Features, func(g FeatureGroup) bool { return g.ID == group }) {
			return selection{}, fmt.Errorf("unknown feature group %q", group)
		}
	}
	sel := selection{features: map[string][]string{}}
	seenDeps := map[string]bool{}
	for _, g := range m.Features {
		chosen := requested[g.ID]
		for _, id := range chosen {
			if !slices.ContainsFunc(g.Options, func(o FeatureOption) bool { return o.ID == id }) {
				return selection{}, fmt.Errorf("unknown option %q in feature group %q", id, g.ID)
			}
		}
		var picked []string
		for _, o := range g.Options {
			if !slices.Contains(chosen, o.ID) {
				continue
			}
			picked = append(picked, o.ID)
			sel.options = append(sel.options, selectedOption{Group: g.ID, Option: o})
			for _, d := range o.Dependencies {
				key := d.Group + ":" + d.Artifact
				if !seenDeps[key] {
					seenDeps[key] = true
					sel.dependencies = append(sel.dependencies, d)
				}
			}
		}
		if g.Kind == KindChoice && len(picked) > 1 {
			return selection{}, fmt.Errorf("feature group %q accepts a single option", g.ID)
		}
		if len(picked) > 0 {
			sel.features[g.ID] = picked
		}
	}
	return sel, nil
}
