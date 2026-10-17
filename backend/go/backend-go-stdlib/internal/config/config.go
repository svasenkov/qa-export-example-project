// Package config resolves runtime settings from the same environment variables the
// other reference backends read.
package config

import (
	"net"
	"net/url"
	"os"
	"strconv"
	"time"
)

const (
	// ServiceName is echoed by /api/health and must match health_service in deploy/matrix.yaml.
	ServiceName = "backend-go-stdlib"

	// PostAuthRedirect is where the frontends navigate after login/register.
	PostAuthRedirect = "/"

	defaultDatabaseName   = "multistack_app_go_stdlib"
	defaultServerPort     = "8080"
	defaultManagementPort = "8081"
	defaultJWTSecret      = "multistack-dev-secret-change-in-production-min-32-chars"
	defaultExpirationMS   = int64(86_400_000)
)

// Config holds everything main needs to wire the service.
type Config struct {
	ServiceName    string
	ServerPort     string
	ManagementPort string
	DatabaseURL    string
	JWTSecret      string
	JWTExpiration  time.Duration
}

// Load reads the environment and falls back to the shared reference defaults.
func Load() Config {
	return Config{
		ServiceName:    ServiceName,
		ServerPort:     env("SERVER_PORT", defaultServerPort),
		ManagementPort: env("MANAGEMENT_PORT", defaultManagementPort),
		DatabaseURL:    DatabaseURL(),
		JWTSecret:      env("JWT_SECRET", defaultJWTSecret),
		JWTExpiration:  JWTExpiration(),
	}
}

// DatabaseURL prefers an explicit DATABASE_URL and otherwise assembles the DSN from
// the DB_* parts, escaping user and password.
func DatabaseURL() string {
	if raw := os.Getenv("DATABASE_URL"); raw != "" {
		return raw
	}
	dsn := url.URL{
		Scheme:   "postgres",
		User:     url.UserPassword(env("DB_USER", "multistack"), env("DB_PASSWORD", "multistack")),
		Host:     net.JoinHostPort(env("DB_HOST", "localhost"), env("DB_PORT", "5432")),
		Path:     "/" + env("DB_NAME", defaultDatabaseName),
		RawQuery: "sslmode=disable",
	}
	return dsn.String()
}

// JWTExpiration reads JWT_EXPIRATION_MS; anything unparsable or non-positive keeps the default.
func JWTExpiration() time.Duration {
	ms := defaultExpirationMS
	if raw := os.Getenv("JWT_EXPIRATION_MS"); raw != "" {
		if parsed, err := strconv.ParseInt(raw, 10, 64); err == nil && parsed > 0 {
			ms = parsed
		}
	}
	return time.Duration(ms) * time.Millisecond
}

func env(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}
