package api_test

import (
	"bytes"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"

	"dev.multistack/backend-go-stdlib/internal/store/storetest"
)

func TestOpenAPISpecMatchesContractCopy(t *testing.T) {
	copyPath, ssotPath := openAPIPaths(t)
	expected, err := os.ReadFile(copyPath)
	if err != nil {
		t.Fatalf("read copy: %v", err)
	}
	ssot, err := os.ReadFile(ssotPath)
	if err != nil {
		t.Fatalf("read SSOT: %v", err)
	}
	if !bytes.Equal(expected, ssot) {
		t.Fatal("internal/api/openapi.yaml differs from _contract/openapi.yaml")
	}

	recorder := newHarness(t, storetest.New()).do(t, http.MethodGet, "/api/openapi.yaml", "", nil)

	requireStatus(t, recorder, http.StatusOK)
	if ct := recorder.Header().Get("Content-Type"); !strings.Contains(ct, "application/yaml") {
		t.Fatalf("Content-Type = %q, want application/yaml", ct)
	}
	if !bytes.Equal(expected, recorder.Body.Bytes()) {
		t.Fatal("GET /api/openapi.yaml body differs from the module copy")
	}
}

func TestOpenAPIDocsServesSwaggerUI(t *testing.T) {
	recorder := newHarness(t, storetest.New()).do(t, http.MethodGet, "/api/docs", "", nil)

	requireStatus(t, recorder, http.StatusOK)
	if ct := recorder.Header().Get("Content-Type"); !strings.Contains(ct, "text/html") {
		t.Fatalf("Content-Type = %q, want text/html", ct)
	}
	body := recorder.Body.String()
	if !strings.Contains(body, "SwaggerUIBundle") {
		t.Fatal("docs HTML is missing SwaggerUIBundle")
	}
	if !strings.Contains(body, "./openapi.yaml") {
		t.Fatal("docs HTML does not point at ./openapi.yaml")
	}
}

func openAPIPaths(t *testing.T) (copyPath, ssotPath string) {
	t.Helper()
	_, file, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("runtime.Caller")
	}
	dir := filepath.Dir(file)
	moduleRoot := filepath.Clean(filepath.Join(dir, "..", ".."))
	return filepath.Join(dir, "openapi.yaml"),
		filepath.Join(moduleRoot, "..", "..", "..", "_contract", "openapi.yaml")
}
