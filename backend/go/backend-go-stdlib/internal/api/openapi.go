package api

import (
	_ "embed"
	"net/http"
)

// Module copy of _contract/openapi.yaml and the Swagger UI that points at it.
// Not swag/codegen — the yaml is SSOT.
//
//go:embed openapi.yaml
var openAPISpec []byte

//go:embed openapi-docs.html
var openAPIDocs []byte

// OpenAPISpec answers GET /api/openapi.yaml with the embedded contract copy.
func (h *Handler) OpenAPISpec(w http.ResponseWriter, _ *http.Request) {
	h.writeRaw(w, "application/yaml", openAPISpec)
}

// OpenAPIDocs answers GET /api/docs with Swagger UI pointed at ./openapi.yaml.
func (h *Handler) OpenAPIDocs(w http.ResponseWriter, _ *http.Request) {
	h.writeRaw(w, "text/html; charset=utf-8", openAPIDocs)
}

func (h *Handler) writeRaw(w http.ResponseWriter, contentType string, body []byte) {
	w.Header().Set("Content-Type", contentType)
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(body)
}
