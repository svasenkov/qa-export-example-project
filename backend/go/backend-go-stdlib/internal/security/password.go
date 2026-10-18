package security

import (
	"crypto/sha256"
	"encoding/base64"
	"strings"

	"golang.org/x/crypto/bcrypt"
)

// The version is stored with the hash; the existing VARCHAR(255) needs no migration.
const passwordVersion = "$qa-sha256-bcrypt-v1$"

// HashPassword binds every byte of the password. Base64 keeps the bcrypt input
// below 72 bytes and avoids binary/NUL handling differences between implementations.
func HashPassword(password string) (string, error) {
	return hashPassword(password, bcrypt.DefaultCost)
}

// hashPassword takes the cost separately so tests can exercise the bcrypt failure path.
func hashPassword(password string, cost int) (string, error) {
	hash, err := bcrypt.GenerateFromPassword(passwordDigest(password), cost)
	if err != nil {
		return "", err
	}
	return passwordVersion + string(hash), nil
}

func CheckPassword(password, hash string) bool {
	if strings.HasPrefix(hash, passwordVersion) {
		return bcrypt.CompareHashAndPassword([]byte(strings.TrimPrefix(hash, passwordVersion)), passwordDigest(password)) == nil
	}
	// Legacy hashes cannot distinguish exactly 72 bytes from a truncated longer
	// password. Fail closed for both; never silently migrate an ambiguous login.
	if len([]byte(password)) >= 72 {
		return false
	}
	return bcrypt.CompareHashAndPassword([]byte(hash), []byte(password)) == nil
}

func passwordDigest(password string) []byte {
	sum := sha256.Sum256([]byte(password))
	return []byte(base64.StdEncoding.EncodeToString(sum[:]))
}
