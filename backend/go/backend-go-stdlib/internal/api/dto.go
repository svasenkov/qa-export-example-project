package api

// Response bodies are structs rather than maps so the JSON field order matches the other
// reference backends exactly.

type healthResponse struct {
	Status  string `json:"status"`
	Service string `json:"service"`
}

type itemDTO struct {
	ID          int64  `json:"id"`
	Name        string `json:"name"`
	Description string `json:"description"`
}

type itemsResponse struct {
	Items  []itemDTO `json:"items"`
	Source string    `json:"source"`
}

type authResponse struct {
	Token       string `json:"token"`
	Username    string `json:"username"`
	RedirectURL string `json:"redirectUrl"`
}

type profileResponse struct {
	Username string `json:"username"`
}

type errorResponse struct {
	Message string `json:"message"`
}
