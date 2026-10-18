package security

import (
	"strings"
	"testing"
)

func TestHashPasswordPropagatesBcryptError(t *testing.T) {
	if _, err := hashPassword("password1", 33); err == nil {
		t.Fatal("hashPassword with an out-of-range cost returned nil error")
	}
}

func TestHashPasswordVersionedRoundTrip(t *testing.T) {
	hash, err := HashPassword("password1")
	if err != nil {
		t.Fatalf("HashPassword: %v", err)
	}
	if !strings.HasPrefix(hash, passwordVersion) {
		t.Fatalf("hash = %q, want the %q version prefix", hash, passwordVersion)
	}
	if !CheckPassword("password1", hash) {
		t.Fatal("CheckPassword rejected the password it just hashed")
	}
}
