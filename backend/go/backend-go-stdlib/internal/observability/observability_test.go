package observability_test

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"dev.multistack/backend-go-stdlib/internal/observability"
)

func TestHealth(t *testing.T) {
	recorder := httptest.NewRecorder()
	observability.NewMux().ServeHTTP(recorder, httptest.NewRequest(http.MethodGet, "/actuator/health", nil))

	if recorder.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200 (body %q)", recorder.Code, recorder.Body.String())
	}
	var payload struct {
		Status string `json:"status"`
	}
	if err := json.Unmarshal(recorder.Body.Bytes(), &payload); err != nil {
		t.Fatalf("decode %q: %v", recorder.Body.String(), err)
	}
	if payload.Status != "UP" {
		t.Fatalf("status = %q, want UP", payload.Status)
	}
}

func TestUnknownPathIsNotFound(t *testing.T) {
	recorder := httptest.NewRecorder()
	observability.NewMux().ServeHTTP(recorder, httptest.NewRequest(http.MethodGet, "/actuator/foo", nil))

	if recorder.Code != http.StatusNotFound {
		t.Fatalf("status = %d, want 404", recorder.Code)
	}
}

func TestPrometheusIncludesHTTPHistogram(t *testing.T) {
	wrapped := observability.Middleware(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	health := httptest.NewRecorder()
	wrapped.ServeHTTP(health, httptest.NewRequest(http.MethodGet, "/api/health", nil))
	if health.Code != http.StatusOK {
		t.Fatalf("health status = %d", health.Code)
	}

	recorder := httptest.NewRecorder()
	observability.NewMux().ServeHTTP(recorder, httptest.NewRequest(http.MethodGet, "/actuator/prometheus", nil))

	if recorder.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200 (body %q)", recorder.Code, recorder.Body.String())
	}
	if !strings.Contains(recorder.Body.String(), "http_server_requests_seconds") {
		t.Fatalf("prometheus body missing http_server_requests_seconds:\n%s", recorder.Body.String())
	}
}
