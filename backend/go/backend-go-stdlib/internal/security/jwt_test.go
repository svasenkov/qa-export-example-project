package security_test

import (
	"errors"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"

	"dev.multistack/backend-go-stdlib/internal/security"
)

const testSecret = "multistack-dev-secret-change-in-production-min-32-chars"

func TestTokenRoundTrip(t *testing.T) {
	tokens := security.NewTokenService(testSecret, time.Hour)

	raw, err := tokens.Create("user1")
	if err != nil {
		t.Fatalf("Create: %v", err)
	}
	username, err := tokens.Username(raw)
	if err != nil {
		t.Fatalf("Username: %v", err)
	}
	if username != "user1" {
		t.Fatalf("username = %q, want %q", username, "user1")
	}
}

func TestTokenClaims(t *testing.T) {
	tokens := security.NewTokenService(testSecret, time.Hour)
	raw, err := tokens.Create("user1")
	if err != nil {
		t.Fatalf("Create: %v", err)
	}

	parsed, err := jwt.Parse(raw, func(*jwt.Token) (any, error) { return []byte(testSecret), nil })
	if err != nil {
		t.Fatalf("Parse: %v", err)
	}
	if alg := parsed.Method.Alg(); alg != "HS256" {
		t.Fatalf("alg = %q, want HS256", alg)
	}

	claims, ok := parsed.Claims.(jwt.MapClaims)
	if !ok {
		t.Fatalf("claims type = %T", parsed.Claims)
	}
	issuedAt, err := claims.GetIssuedAt()
	if err != nil || issuedAt == nil {
		t.Fatalf("iat missing: %v", err)
	}
	expiresAt, err := claims.GetExpirationTime()
	if err != nil || expiresAt == nil {
		t.Fatalf("exp missing: %v", err)
	}
	if delta := expiresAt.Sub(issuedAt.Time); delta != time.Hour {
		t.Fatalf("exp - iat = %v, want 1h", delta)
	}
}

func TestTokenRejections(t *testing.T) {
	tokens := security.NewTokenService(testSecret, time.Hour)
	valid, err := tokens.Create("user1")
	if err != nil {
		t.Fatalf("Create: %v", err)
	}

	expiredService := security.NewTokenService(testSecret, -time.Minute)
	expired, err := expiredService.Create("user1")
	if err != nil {
		t.Fatalf("Create expired: %v", err)
	}

	foreign, err := security.NewTokenService("another-secret-that-is-long-enough-for-hs256", time.Hour).Create("user1")
	if err != nil {
		t.Fatalf("Create foreign: %v", err)
	}

	hs512, err := jwt.NewWithClaims(jwt.SigningMethodHS512, jwt.MapClaims{
		"sub": "user1",
		"exp": jwt.NewNumericDate(time.Now().Add(time.Hour)),
	}).SignedString([]byte(testSecret))
	if err != nil {
		t.Fatalf("Create hs512: %v", err)
	}

	noSubject, err := jwt.NewWithClaims(jwt.SigningMethodHS256, jwt.MapClaims{
		"exp": jwt.NewNumericDate(time.Now().Add(time.Hour)),
	}).SignedString([]byte(testSecret))
	if err != nil {
		t.Fatalf("Create without subject: %v", err)
	}

	cases := map[string]string{
		"empty":              "",
		"garbage":            "not.a.token",
		"expired":            expired,
		"signed elsewhere":   foreign,
		"unexpected alg":     hs512,
		"missing subject":    noSubject,
		"truncated payload":  valid[:len(valid)-4],
		"tampered signature": valid + "x",
	}

	for name, raw := range cases {
		t.Run(name, func(t *testing.T) {
			username, err := tokens.Username(raw)
			if err == nil {
				t.Fatalf("Username(%q) = %q, want an error", raw, username)
			}
			if !errors.Is(err, security.ErrInvalidToken) {
				t.Fatalf("error = %v, want ErrInvalidToken", err)
			}
		})
	}
}
