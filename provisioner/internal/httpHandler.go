package httpHandler

import (
	"encoding/json"
	"log"
	"net/http"

	"devforge/provisioner/internal/generator"
	"devforge/provisioner/internal/model"
)

type Handler struct {
	gen             *generator.ProjectGenerator
	outputWorkspace string
}

func NewHandler(gen *generator.ProjectGenerator, workspace string) *Handler {
	return &Handler{
		gen:             gen,
		outputWorkspace: workspace,
	}
}

func (h *Handler) Generate(w http.ResponseWriter, r *http.Request) {
	var req model.GenerationRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}

	outPath, err := h.gen.Generate(req, h.outputWorkspace)
	if err != nil {
		log.Printf("Generation error for %s: %v", req.ServiceName, err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	log.Printf("Successfully generated service [%s] at: %s", req.ServiceName, outPath)

	w.Header().Set("Content-Type", "application/json")
	err = json.NewEncoder(w).Encode(model.GenerationResponse{
		ServiceName: req.ServiceName,
		OutputPath:  outPath,
		Status:      "GENERATED",
	})
	if err != nil {
		return
	}
}

func (h *Handler) Health(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	_, err := w.Write([]byte(`{"status":"UP"}`))
	if err != nil {
		return
	}
}
