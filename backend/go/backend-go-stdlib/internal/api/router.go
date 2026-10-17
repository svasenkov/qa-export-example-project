package api

import (
	"log"
	"net/http"
	"time"

	"dev.multistack/backend-go-stdlib/internal/observability"
)

// NewRouter mounts the API contract on a plain ServeMux. Frontends stay on
// separate nginx containers; Swagger UI is under /api/docs so the gateway /api/
// proxy reaches it. Method-aware patterns need Go 1.22+.
func NewRouter(h *Handler) http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /api/health", h.Health)
	mux.HandleFunc("GET /api/items", h.Items)
	mux.HandleFunc("GET /api/openapi.yaml", h.OpenAPISpec)
	mux.HandleFunc("GET /api/docs", h.OpenAPIDocs)
	mux.HandleFunc("POST /api/auth/register", h.Register)
	mux.HandleFunc("POST /api/auth/login", h.Login)
	mux.HandleFunc("POST /api/auth/logout", h.Logout)
	mux.Handle("GET /api/auth/me", h.RequireAuth(http.HandlerFunc(h.Me)))
	mux.Handle("DELETE /api/auth/me", h.RequireAuth(http.HandlerFunc(h.DeleteAccount)))
	// Pattern precedence keeps every mapped route ahead of this catch-all, which therefore
	// only sees the paths and methods that no route claims.
	mux.HandleFunc("/api/", h.APIFallback)

	return observability.Middleware(logging(CORS(mux)))
}

// logging is the framework-free stand-in for gin.Logger.
func logging(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		started := time.Now()
		recorder := &statusRecorder{ResponseWriter: w, status: http.StatusOK}
		next.ServeHTTP(recorder, r)
		log.Printf("%d %s %s %s", recorder.status, r.Method, r.URL.Path, time.Since(started).Round(time.Microsecond))
	})
}

type statusRecorder struct {
	http.ResponseWriter
	status int
}

func (s *statusRecorder) WriteHeader(status int) {
	s.status = status
	s.ResponseWriter.WriteHeader(status)
}
