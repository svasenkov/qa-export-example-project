package api_test

import (
	"net/http"
	"testing"
)

func TestCORSPreflight(t *testing.T) {
	h := seededHarness(t)

	recorder := h.do(t, http.MethodOptions, "/api/auth/login", "", map[string]string{
		"Origin":                         "https://autotests.ai",
		"Access-Control-Request-Method":  "POST",
		"Access-Control-Request-Headers": "authorization,content-type",
	})

	if recorder.Code != http.StatusNoContent {
		t.Fatalf("status = %d, want 204", recorder.Code)
	}
	header := recorder.Header()
	if got := header.Get("Access-Control-Allow-Origin"); got != "*" {
		t.Fatalf("Allow-Origin = %q, want *", got)
	}
	if got := header.Get("Access-Control-Allow-Methods"); got != "GET, POST, PUT, PATCH, DELETE, OPTIONS" {
		t.Fatalf("Allow-Methods = %q", got)
	}
	if got := header.Get("Access-Control-Allow-Headers"); got != "authorization,content-type" {
		t.Fatalf("Allow-Headers = %q, want the requested headers echoed", got)
	}
	if got := header.Get("Access-Control-Expose-Headers"); got != "Authorization" {
		t.Fatalf("Expose-Headers = %q", got)
	}
	// Bearer tokens only: an ambient cookie credential must never be allowed.
	if got := header.Get("Access-Control-Allow-Credentials"); got != "" {
		t.Fatalf("Allow-Credentials = %q, want unset", got)
	}
}

func TestCORSOnSimpleRequest(t *testing.T) {
	h := seededHarness(t)

	recorder := h.do(t, http.MethodGet, "/api/health", "", map[string]string{
		"Origin": "https://autotests.ai",
	})

	if recorder.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200", recorder.Code)
	}
	if got := recorder.Header().Get("Access-Control-Allow-Origin"); got != "*" {
		t.Fatalf("Allow-Origin = %q, want *", got)
	}
	if got := recorder.Header().Get("Access-Control-Allow-Headers"); got != "*" {
		t.Fatalf("Allow-Headers = %q, want * when nothing specific was requested", got)
	}
}
