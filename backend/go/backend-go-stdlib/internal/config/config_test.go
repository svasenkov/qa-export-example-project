package config_test

import (
	"testing"
	"time"

	"dev.multistack/backend-go-stdlib/internal/config"
)

func clearEnv(t *testing.T) {
	t.Helper()
	for _, key := range []string{
		"DATABASE_URL", "DB_HOST", "DB_PORT", "DB_NAME", "DB_USER", "DB_PASSWORD",
		"SERVER_PORT", "MANAGEMENT_PORT", "JWT_SECRET", "JWT_EXPIRATION_MS",
	} {
		t.Setenv(key, "")
	}
}

func TestLoadDefaults(t *testing.T) {
	clearEnv(t)

	cfg := config.Load()

	if cfg.ServiceName != "backend-go-stdlib" {
		t.Fatalf("ServiceName = %q", cfg.ServiceName)
	}
	if cfg.ServerPort != "8080" {
		t.Fatalf("ServerPort = %q, want 8080", cfg.ServerPort)
	}
	if cfg.ManagementPort != "8081" {
		t.Fatalf("ManagementPort = %q, want 8081", cfg.ManagementPort)
	}
	want := "postgres://multistack:multistack@localhost:5432/multistack_app_go_stdlib?sslmode=disable"
	if cfg.DatabaseURL != want {
		t.Fatalf("DatabaseURL = %q, want %q", cfg.DatabaseURL, want)
	}
	if cfg.JWTSecret != "multistack-dev-secret-change-in-production-min-32-chars" {
		t.Fatalf("JWTSecret = %q", cfg.JWTSecret)
	}
	if cfg.JWTExpiration != 24*time.Hour {
		t.Fatalf("JWTExpiration = %v, want 24h", cfg.JWTExpiration)
	}
}

func TestLoadFromEnvironment(t *testing.T) {
	clearEnv(t)
	t.Setenv("DB_HOST", "postgres")
	t.Setenv("DB_PORT", "55440")
	t.Setenv("DB_NAME", "other_db")
	t.Setenv("DB_USER", "someone")
	t.Setenv("DB_PASSWORD", "p@ss word")
	t.Setenv("SERVER_PORT", "18830")
	t.Setenv("MANAGEMENT_PORT", "18081")
	t.Setenv("JWT_SECRET", "custom")
	t.Setenv("JWT_EXPIRATION_MS", "1000")

	cfg := config.Load()

	if cfg.ServerPort != "18830" || cfg.ManagementPort != "18081" || cfg.JWTSecret != "custom" {
		t.Fatalf("cfg = %+v", cfg)
	}
	if cfg.JWTExpiration != time.Second {
		t.Fatalf("JWTExpiration = %v, want 1s", cfg.JWTExpiration)
	}
	// Credentials with URL-unsafe characters must survive DSN assembly.
	want := "postgres://someone:p%40ss%20word@postgres:55440/other_db?sslmode=disable"
	if cfg.DatabaseURL != want {
		t.Fatalf("DatabaseURL = %q, want %q", cfg.DatabaseURL, want)
	}
}

func TestDatabaseURLOverride(t *testing.T) {
	clearEnv(t)
	t.Setenv("DB_HOST", "ignored")
	t.Setenv("DATABASE_URL", "postgres://u:p@db:5432/explicit")

	if got := config.DatabaseURL(); got != "postgres://u:p@db:5432/explicit" {
		t.Fatalf("DatabaseURL = %q", got)
	}
}

func TestJWTExpirationFallsBackOnBadValues(t *testing.T) {
	for _, raw := range []string{"not-a-number", "0", "-5"} {
		t.Run(raw, func(t *testing.T) {
			t.Setenv("JWT_EXPIRATION_MS", raw)

			if got := config.JWTExpiration(); got != 24*time.Hour {
				t.Fatalf("JWTExpiration = %v, want the 24h default", got)
			}
		})
	}
}
