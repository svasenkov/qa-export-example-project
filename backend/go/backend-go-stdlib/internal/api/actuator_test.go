package api_test

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"dev.multistack/backend-go-stdlib/internal/observability"
	"dev.multistack/backend-go-stdlib/internal/store/storetest"
)

func TestActuatorPrometheusIsNotOnAPIPort(t *testing.T) {
	h := newHarness(t, storetest.New())

	recorder := h.do(t, http.MethodGet, "/actuator/prometheus", "", nil)

	if recorder.Code == http.StatusOK {
		t.Fatalf("status = %d, want not 200 (body %q)", recorder.Code, recorder.Body.String())
	}
}

func TestManagementPrometheusIncludesHTTPHistogram(t *testing.T) {
	h := newHarness(t, storetest.New())
	health := h.do(t, http.MethodGet, "/api/health", "", nil)
	requireStatus(t, health, http.StatusOK)

	recorder := httptest.NewRecorder()
	observability.NewMux().ServeHTTP(recorder, httptest.NewRequest(http.MethodGet, "/actuator/prometheus", nil))

	if recorder.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200 (body %q)", recorder.Code, recorder.Body.String())
	}
	if !strings.Contains(recorder.Body.String(), "http_server_requests_seconds") {
		t.Fatalf("prometheus body missing http_server_requests_seconds:\n%s", recorder.Body.String())
	}
}
