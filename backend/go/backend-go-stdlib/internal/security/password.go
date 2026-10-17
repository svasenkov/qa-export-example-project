package security

import "golang.org/x/crypto/bcrypt"

// bcryptMaxInputBytes is a property of the algorithm: bcrypt ignores everything past the
// 72nd byte of its input. Truncating explicitly keeps the contract's 128-character
// passwords usable instead of failing them, which is what Python's bcrypt and bcryptjs —
// used by the other reference backends — do implicitly.
const bcryptMaxInputBytes = 72

// HashPassword produces a bcrypt hash at the default cost, which is what
// Spring's BCryptPasswordEncoder and Python's bcrypt also use.
func HashPassword(password string) (string, error) {
	hash, err := bcrypt.GenerateFromPassword(bcryptInput(password), bcrypt.DefaultCost)
	if err != nil {
		return "", err
	}
	return string(hash), nil
}

// CheckPassword reports whether password matches the stored bcrypt hash. It truncates
// exactly like HashPassword, so a password longer than 72 bytes still round-trips.
func CheckPassword(password, hash string) bool {
	return bcrypt.CompareHashAndPassword([]byte(hash), bcryptInput(password)) == nil
}

// bcryptInput cuts by bytes, not runes, because bytes are what bcrypt counts. A cut that
// splits a multi-byte character is harmless: hashing and verification cut at the same offset.
func bcryptInput(password string) []byte {
	input := []byte(password)
	if len(input) > bcryptMaxInputBytes {
		return input[:bcryptMaxInputBytes]
	}
	return input
}
