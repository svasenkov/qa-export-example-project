package security

import (
	"errors"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

// ErrInvalidToken covers every rejection reason: bad signature, wrong algorithm,
// expired, malformed, or missing subject.
var ErrInvalidToken = errors.New("invalid token")

// TokenService issues and verifies the HS256 tokens described in the module README.
type TokenService struct {
	secret     []byte
	expiration time.Duration
}

// NewTokenService binds a signing secret to a token lifetime.
func NewTokenService(secret string, expiration time.Duration) *TokenService {
	return &TokenService{secret: []byte(secret), expiration: expiration}
}

// Create signs a token with sub/iat/exp claims.
func (s *TokenService) Create(username string) (string, error) {
	now := time.Now()
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, jwt.MapClaims{
		"sub": username,
		"iat": jwt.NewNumericDate(now),
		"exp": jwt.NewNumericDate(now.Add(s.expiration)),
	})
	return token.SignedString(s.secret)
}

// Username verifies the token and returns its subject.
func (s *TokenService) Username(raw string) (string, error) {
	parsed, err := jwt.Parse(
		raw,
		func(*jwt.Token) (any, error) { return s.secret, nil },
		jwt.WithValidMethods([]string{jwt.SigningMethodHS256.Alg()}),
	)
	if err != nil {
		return "", errors.Join(ErrInvalidToken, err)
	}
	subject, err := parsed.Claims.GetSubject()
	if err != nil || subject == "" {
		return "", ErrInvalidToken
	}
	return subject, nil
}
