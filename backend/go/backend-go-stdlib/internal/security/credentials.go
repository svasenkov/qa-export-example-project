// Package security holds credential validation, password hashing and JWT handling.
package security

import (
	"strings"
	"unicode/utf8"
)

// Credential bounds shared with every other reference backend.
const (
	UsernameMinLength = 3
	UsernameMaxLength = 64
	PasswordMinLength = 6
	PasswordMaxLength = 128
)

// Validation messages are part of the contract — do not reword.
const (
	MessageUsernameRequired = "username is required"
	MessagePasswordRequired = "password is required"
	MessageUsernameLength   = "username must be 3-64 characters"
	MessagePasswordLength   = "password must be 6-128 characters"

	// MessageSeparator joins the messages of every violating field, exactly like the
	// reference handler that collects the whole bean-validation binding result.
	MessageSeparator = "; "
)

// Credentials is the raw /api/auth request body. The fields stay `any` so that a missing
// field and a field of the wrong JSON type both yield the same "is required" message as
// the Python and JVM backends.
type Credentials struct {
	Username any `json:"username"`
	Password any `json:"password"`
}

// Validate returns the trusted string values, or a non-empty message naming every field
// that violates the contract, joined with MessageSeparator. Lengths are counted in runes,
// matching Python's len() on str.
func (c Credentials) Validate() (username string, password string, message string) {
	username, usernameMessage := checkField(c.Username, UsernameMinLength, UsernameMaxLength,
		MessageUsernameRequired, MessageUsernameLength)
	password, passwordMessage := checkField(c.Password, PasswordMinLength, PasswordMaxLength,
		MessagePasswordRequired, MessagePasswordLength)

	violations := make([]string, 0, 2)
	for _, violation := range []string{usernameMessage, passwordMessage} {
		if violation != "" {
			violations = append(violations, violation)
		}
	}
	if len(violations) > 0 {
		return "", "", strings.Join(violations, MessageSeparator)
	}
	return username, password, ""
}

// checkField reports at most one violation per field: "is required" outranks the length
// bound, so a blank value never yields two messages for the same field.
func checkField(raw any, minLength, maxLength int, required, length string) (value string, message string) {
	value, ok := raw.(string)
	if !ok || value == "" {
		return "", required
	}
	if n := utf8.RuneCountInString(value); n < minLength || n > maxLength {
		return "", length
	}
	return value, ""
}
