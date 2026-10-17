package api

import (
	"net/http"
	"strings"
)

const (
	apiPrefix      = "/api"
	allowedMethods = "GET, POST, PUT, PATCH, DELETE, OPTIONS"
	exposedHeaders = "Authorization"
)

// CORS mirrors the reference configuration: every origin, no credentials (auth is a
// Bearer token, never an ambient cookie), Authorization exposed to the browser.
// It runs before the mux so preflight never hits the method-specific patterns.
func CORS(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !strings.HasPrefix(r.URL.Path, apiPrefix) {
			next.ServeHTTP(w, r)
			return
		}
		applyCORS(w.Header(), r)
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(w, r)
	})
}

func applyCORS(header http.Header, r *http.Request) {
	header.Set("Access-Control-Allow-Origin", "*")
	header.Set("Access-Control-Allow-Methods", allowedMethods)
	header.Set("Access-Control-Expose-Headers", exposedHeaders)
	header.Set("Access-Control-Max-Age", "600")
	requested := r.Header.Get("Access-Control-Request-Headers")
	if requested == "" {
		requested = "*"
	}
	header.Set("Access-Control-Allow-Headers", requested)
	header.Add("Vary", "Origin")
	header.Add("Vary", "Access-Control-Request-Headers")
}
