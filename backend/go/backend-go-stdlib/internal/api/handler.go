// Package api implements the reference HTTP contract with net/http only.
package api

import (
	"context"
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"strings"

	"dev.multistack/backend-go-stdlib/internal/config"
	"dev.multistack/backend-go-stdlib/internal/security"
	"dev.multistack/backend-go-stdlib/internal/store"
)

// Error messages are part of the contract — do not reword.
const (
	messageBadCredentials = "Wrong login or password"
	messageUnauthorized   = "Unauthorized"
	messageDuplicateUser  = "Username already taken"
	messageInvalidJSON    = "Request body is not valid JSON"
	messageServerError    = "Internal server error"

	itemsSource  = "postgresql"
	bearerPrefix = "Bearer "
	authHeader   = "Authorization"
)

// contextKey keeps the authenticated username out of the exported context namespace.
type contextKey struct{}

var usernameKey contextKey

// Handler serves /api/**. It only knows the Store interface, never the driver.
type Handler struct {
	store       store.Store
	tokens      *security.TokenService
	serviceName string
}

// NewHandler wires the dependencies of the API layer.
func NewHandler(s store.Store, tokens *security.TokenService, serviceName string) *Handler {
	return &Handler{store: s, tokens: tokens, serviceName: serviceName}
}

// Health answers the liveness probe used by Docker and deploy/matrix.yaml.
func (h *Handler) Health(w http.ResponseWriter, _ *http.Request) {
	h.writeJSON(w, http.StatusOK, healthResponse{Status: "ok", Service: h.serviceName})
}

// Items returns every seeded item ordered by id.
func (h *Handler) Items(w http.ResponseWriter, r *http.Request) {
	rows, err := h.store.ListItems(r.Context())
	if err != nil {
		h.serverError(w, err)
		return
	}
	items := make([]itemDTO, 0, len(rows))
	for _, row := range rows {
		items = append(items, itemDTO{ID: row.ID, Name: row.Name, Description: row.Description})
	}
	h.writeJSON(w, http.StatusOK, itemsResponse{Items: items, Source: itemsSource})
}

// Register creates a user, answering 409 both on the pre-check and on a lost unique race.
func (h *Handler) Register(w http.ResponseWriter, r *http.Request) {
	username, password, ok := h.credentials(w, r)
	if !ok {
		return
	}

	switch _, err := h.store.FindUserByUsername(r.Context(), username); {
	case err == nil:
		h.writeJSON(w, http.StatusConflict, errorResponse{Message: messageDuplicateUser})
		return
	case !errors.Is(err, store.ErrUserNotFound):
		h.serverError(w, err)
		return
	}

	passwordHash, err := security.HashPassword(password)
	if err != nil {
		h.serverError(w, err)
		return
	}

	if _, err := h.store.CreateUser(r.Context(), username, passwordHash); err != nil {
		if errors.Is(err, store.ErrDuplicateUsername) {
			h.writeJSON(w, http.StatusConflict, errorResponse{Message: messageDuplicateUser})
			return
		}
		h.serverError(w, err)
		return
	}

	h.issueToken(w, http.StatusCreated, username)
}

// Login exchanges credentials for a token; unknown user and wrong password are
// deliberately indistinguishable.
func (h *Handler) Login(w http.ResponseWriter, r *http.Request) {
	username, password, ok := h.credentials(w, r)
	if !ok {
		return
	}

	user, err := h.store.FindUserByUsername(r.Context(), username)
	if errors.Is(err, store.ErrUserNotFound) {
		h.writeJSON(w, http.StatusUnauthorized, errorResponse{Message: messageBadCredentials})
		return
	}
	if err != nil {
		h.serverError(w, err)
		return
	}
	if !security.CheckPassword(password, user.PasswordHash) {
		h.writeJSON(w, http.StatusUnauthorized, errorResponse{Message: messageBadCredentials})
		return
	}

	h.issueToken(w, http.StatusOK, user.Username)
}

// Logout is stateless: the client simply drops the token.
func (h *Handler) Logout(w http.ResponseWriter, _ *http.Request) {
	w.WriteHeader(http.StatusNoContent)
}

// Me echoes the authenticated username; RequireAuth already verified it.
func (h *Handler) Me(w http.ResponseWriter, r *http.Request) {
	username, _ := r.Context().Value(usernameKey).(string)
	h.writeJSON(w, http.StatusOK, profileResponse{Username: username})
}

// DeleteAccount is the authenticated self-delete. Tokens are stateless, so a JWT issued
// earlier keeps verifying after deletion — but RequireAuth answers 401 once the row is gone.
func (h *Handler) DeleteAccount(w http.ResponseWriter, r *http.Request) {
	username, _ := r.Context().Value(usernameKey).(string)
	if err := h.store.DeleteUser(r.Context(), username); err != nil {
		h.serverError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// APIFallback answers an unmapped path or method under /api/ with 401 instead of 404: the
// reference authenticates the whole prefix before routing, so a request never reveals
// which routes exist. Only the /api/ catch-all pattern reaches here, so every path outside
// it keeps the ServeMux 404.
func (h *Handler) APIFallback(w http.ResponseWriter, _ *http.Request) {
	h.unauthorized(w)
}

// RequireAuth rejects anything without a valid Bearer token for an existing user.
func (h *Handler) RequireAuth(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		header := r.Header.Get(authHeader)
		if !strings.HasPrefix(header, bearerPrefix) {
			h.unauthorized(w)
			return
		}
		username, err := h.tokens.Username(strings.TrimPrefix(header, bearerPrefix))
		if err != nil {
			h.unauthorized(w)
			return
		}
		// A token can outlive its user; treat that as unauthenticated rather than 500.
		switch _, err := h.store.FindUserByUsername(r.Context(), username); {
		case errors.Is(err, store.ErrUserNotFound):
			h.unauthorized(w)
			return
		case err != nil:
			h.serverError(w, err)
			return
		}
		next.ServeHTTP(w, r.WithContext(context.WithValue(r.Context(), usernameKey, username)))
	})
}

func (h *Handler) issueToken(w http.ResponseWriter, status int, username string) {
	token, err := h.tokens.Create(username)
	if err != nil {
		h.serverError(w, err)
		return
	}
	h.writeJSON(w, status, authResponse{
		Token:       token,
		Username:    username,
		RedirectURL: config.PostAuthRedirect,
	})
}

func (h *Handler) unauthorized(w http.ResponseWriter) {
	h.writeJSON(w, http.StatusUnauthorized, errorResponse{Message: messageUnauthorized})
}

func (h *Handler) serverError(w http.ResponseWriter, err error) {
	log.Printf("%s: %v", h.serviceName, err)
	h.writeJSON(w, http.StatusInternalServerError, errorResponse{Message: messageServerError})
}

// credentials decodes and validates the body, writing 400 itself when it is not usable.
// Anything but a JSON object — empty, malformed, an array, a scalar — is messageInvalidJSON,
// so the decode target is a pointer: a literal `null` leaves it nil instead of passing for
// an empty object, which must still go through validation.
func (h *Handler) credentials(w http.ResponseWriter, r *http.Request) (username, password string, ok bool) {
	var body *security.Credentials
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil || body == nil {
		h.writeJSON(w, http.StatusBadRequest, errorResponse{Message: messageInvalidJSON})
		return "", "", false
	}

	username, password, message := body.Validate()
	if message != "" {
		h.writeJSON(w, http.StatusBadRequest, errorResponse{Message: message})
		return "", "", false
	}
	return username, password, true
}

// writeJSON marshals up front so the body carries no trailing newline, matching the
// other backends byte-for-byte.
func (h *Handler) writeJSON(w http.ResponseWriter, status int, payload any) {
	body, err := json.Marshal(payload)
	if err != nil {
		log.Printf("%s: marshal response: %v", h.serviceName, err)
		body = []byte(`{"message":"` + messageServerError + `"}`)
		status = http.StatusInternalServerError
	}
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	if _, err := w.Write(body); err != nil {
		log.Printf("%s: write response: %v", h.serviceName, err)
	}
}
