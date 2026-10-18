package api

import (
	"errors"
	"log"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"dev.multistack/backend-go-stdlib/internal/security"
	"dev.multistack/backend-go-stdlib/internal/store/storetest"
)

// failingTokens exercises the issueToken error path: signing itself never fails
// for HS256, so the failure is injected through the handler's narrow interface.
type failingTokens struct {
	err error
}

func (f failingTokens) Create(string) (string, error) { return "", f.err }
func (f failingTokens) Username(string) (string, error) {
	return "", security.ErrInvalidToken
}

func TestRegisterPropagatesHashFailure(t *testing.T) {
	failure := errors.New("bcrypt unavailable")
	original := hashPassword
	hashPassword = func(string) (string, error) { return "", failure }
	t.Cleanup(func() { hashPassword = original })

	h := &Handler{store: storetest.New(), serviceName: "test"}
	recorder := httptest.NewRecorder()
	h.Register(recorder, httptest.NewRequest(
		http.MethodPost, "/api/auth/register",
		strings.NewReader(`{"username":"newbie","password":"password1"}`)))

	if recorder.Code != http.StatusInternalServerError {
		t.Fatalf("status = %d, want 500", recorder.Code)
	}
	if body := recorder.Body.String(); body != `{"message":"Internal server error"}` {
		t.Fatalf("body = %s", body)
	}
}

func TestIssueTokenPropagatesSignFailure(t *testing.T) {
	hash, err := security.HashPassword("password1")
	if err != nil {
		t.Fatalf("HashPassword: %v", err)
	}
	fake := storetest.New().WithUser("user1", string(hash))
	h := &Handler{
		store:       fake,
		tokens:      failingTokens{err: errors.New("signing backend down")},
		serviceName: "test",
	}

	recorder := httptest.NewRecorder()
	h.Login(recorder, httptest.NewRequest(
		http.MethodPost, "/api/auth/login",
		strings.NewReader(`{"username":"user1","password":"password1"}`)))

	if recorder.Code != http.StatusInternalServerError {
		t.Fatalf("status = %d, want 500", recorder.Code)
	}
	if body := recorder.Body.String(); body != `{"message":"Internal server error"}` {
		t.Fatalf("body = %s", body)
	}
}

func TestWriteJSONMarshalFailureFallsBackToPlainError(t *testing.T) {
	failure := errors.New("payload is not encodable")
	original := marshalJSON
	marshalJSON = func(any) ([]byte, error) { return nil, failure }
	t.Cleanup(func() { marshalJSON = original })

	recorder := httptest.NewRecorder()
	(&Handler{serviceName: "test"}).writeJSON(recorder, http.StatusOK, healthResponse{Status: "ok"})

	if recorder.Code != http.StatusInternalServerError {
		t.Fatalf("status = %d, want 500 on marshal failure", recorder.Code)
	}
	if body := recorder.Body.String(); body != `{"message":"Internal server error"}` {
		t.Fatalf("body = %s, want the plain-text fallback", body)
	}
	if ct := recorder.Header().Get("Content-Type"); ct != "application/json; charset=utf-8" {
		t.Fatalf("content type = %q", ct)
	}
}

// failingWriter fails the final Write; the handler can only log and continue.
type failingWriter struct {
	header   http.Header
	wrote    []byte
	writeErr error
}

func (w *failingWriter) Header() http.Header {
	if w.header == nil {
		w.header = http.Header{}
	}
	return w.header
}

func (w *failingWriter) WriteHeader(int) {}

func (w *failingWriter) Write(p []byte) (int, error) {
	w.wrote = append(w.wrote, p...)
	return 0, w.writeErr
}

func TestWriteJSONWriteFailureIsLoggedNotPanicked(t *testing.T) {
	var logs strings.Builder
	h := &Handler{serviceName: "test"}
	origWriter := log.Writer()
	log.SetOutput(&logs)
	t.Cleanup(func() { log.SetOutput(origWriter) })

	writer := &failingWriter{writeErr: errors.New("client disconnected")}
	h.writeJSON(writer, http.StatusOK, healthResponse{Status: "ok", Service: "test"})

	if !strings.Contains(string(writer.wrote), `"status":"ok"`) {
		t.Fatalf("response writer saw %q, want the marshalled payload attempt", writer.wrote)
	}
	if !strings.Contains(logs.String(), "write response") {
		t.Fatalf("expected a write failure log, got %q", logs.String())
	}
}
