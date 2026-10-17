package security_test

import (
	"strings"
	"testing"

	"dev.multistack/backend-go-stdlib/internal/security"
)

func TestHashPasswordRoundTrip(t *testing.T) {
	hash, err := security.HashPassword("password1")
	if err != nil {
		t.Fatalf("HashPassword: %v", err)
	}
	if hash == "password1" || !strings.HasPrefix(hash, "$2") {
		t.Fatalf("hash = %q, want a bcrypt hash", hash)
	}
	if !security.CheckPassword("password1", hash) {
		t.Fatal("CheckPassword rejected the correct password")
	}
	if security.CheckPassword("password2", hash) {
		t.Fatal("CheckPassword accepted a wrong password")
	}
}

func TestHashPasswordIsSalted(t *testing.T) {
	first, err := security.HashPassword("password1")
	if err != nil {
		t.Fatalf("HashPassword: %v", err)
	}
	second, err := security.HashPassword("password1")
	if err != nil {
		t.Fatalf("HashPassword: %v", err)
	}
	if first == second {
		t.Fatal("two hashes of the same password are identical, salt is missing")
	}
}

func TestCheckPasswordRejectsGarbageHash(t *testing.T) {
	if security.CheckPassword("password1", "not-a-bcrypt-hash") {
		t.Fatal("CheckPassword accepted a malformed hash")
	}
}

func TestHashPasswordBeyondBcryptInputLimit(t *testing.T) {
	// 100 characters is well inside the contract's 6-128 range but past bcrypt's 72-byte
	// input window, so it must still register and log in.
	long := strings.Repeat("p", 100)

	hash, err := security.HashPassword(long)
	if err != nil {
		t.Fatalf("HashPassword: %v", err)
	}
	if !security.CheckPassword(long, hash) {
		t.Fatal("a 100-character password does not round-trip")
	}
	if security.CheckPassword(strings.Repeat("q", 100), hash) {
		t.Fatal("CheckPassword accepted a different 100-character password")
	}
}

func TestCheckPasswordIgnoresBytesPastTheLimit(t *testing.T) {
	// Inherent to bcrypt and shared with bcryptjs and Python's bcrypt: only the first
	// 72 bytes take part in the hash. Asserted here so the behaviour is documented.
	prefix := strings.Repeat("p", 72)
	hash, err := security.HashPassword(prefix + "-original-tail")
	if err != nil {
		t.Fatalf("HashPassword: %v", err)
	}

	if !security.CheckPassword(prefix+"-a-completely-different-tail", hash) {
		t.Fatal("bytes past the 72nd must be ignored, as in the other backends")
	}
	if !security.CheckPassword(prefix, hash) {
		t.Fatal("the first 72 bytes alone must verify")
	}
	if security.CheckPassword(strings.Repeat("q", 72)+"-original-tail", hash) {
		t.Fatal("a difference inside the first 72 bytes must be rejected")
	}
}

func TestHashPasswordWithMultibyteRunes(t *testing.T) {
	cases := map[string]string{
		"exactly 72 bytes":       strings.Repeat("é", 36),
		"cut splits a rune":      strings.Repeat("a", 71) + strings.Repeat("é", 5),
		"long cyrillic password": strings.Repeat("пароль", 20),
		"emoji":                  strings.Repeat("🔐", 30),
	}

	for name, password := range cases {
		t.Run(name, func(t *testing.T) {
			hash, err := security.HashPassword(password)
			if err != nil {
				t.Fatalf("HashPassword: %v", err)
			}
			if !security.CheckPassword(password, hash) {
				t.Fatal("password does not round-trip")
			}
			if security.CheckPassword("something-else-entirely", hash) {
				t.Fatal("CheckPassword accepted an unrelated password")
			}
		})
	}
}
