package main

import (
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"

	"go.yaml.in/yaml/v3"
)

type ciEntry struct {
	Template  string   `json:"template"`
	BuildTool string   `json:"buildTool"`
	Profile   string   `json:"profile"`
	Features  []string `json:"features"`
}

type ciManifest struct {
	BuildTool string `yaml:"buildTool"`
	CI        []struct {
		Name     string              `yaml:"name"`
		Features map[string][]string `yaml:"features"`
	} `yaml:"ci"`
}

func writeCIMatrix(templatesRoot string, w io.Writer) error {
	dirs, err := os.ReadDir(templatesRoot)
	if err != nil {
		return err
	}
	entries := []ciEntry{}
	for _, d := range dirs {
		if !d.IsDir() {
			continue
		}
		data, err := os.ReadFile(filepath.Join(templatesRoot, d.Name(), "template.yaml"))
		if err != nil {
			return fmt.Errorf("%s: %w", d.Name(), err)
		}
		var m ciManifest
		if err := yaml.Unmarshal(data, &m); err != nil {
			return fmt.Errorf("%s/template.yaml: %w", d.Name(), err)
		}
		if m.BuildTool == "" {
			return fmt.Errorf("%s/template.yaml has no buildTool", d.Name())
		}
		if len(m.CI) == 0 {
			entries = append(entries, ciEntry{Template: d.Name(), BuildTool: m.BuildTool, Profile: "default", Features: []string{}})
			continue
		}
		for _, p := range m.CI {
			features := []string{}
			for group, options := range p.Features {
				for _, o := range options {
					features = append(features, group+"="+o)
				}
			}
			sort.Strings(features)
			entries = append(entries, ciEntry{Template: d.Name(), BuildTool: m.BuildTool, Profile: p.Name, Features: features})
		}
	}
	return json.NewEncoder(w).Encode(entries)
}
