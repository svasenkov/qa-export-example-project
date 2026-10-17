// Package store is the only place that talks to PostgreSQL. Handlers depend on the
// Store interface, so they can be unit-tested without a live database.
package store

import (
	"context"
	"errors"
	"strings"
)

// Sentinel errors the HTTP layer maps to 401/409.
var (
	ErrUserNotFound      = errors.New("user not found")
	ErrDuplicateUsername = errors.New("duplicate username")
)

// Item is a row of the items table.
type Item struct {
	ID          int64
	Name        string
	Description string
}

// User is a row of the users table.
type User struct {
	ID           int64
	Username     string
	PasswordHash string
}

// Store is the persistence contract used by the handlers and by the seeder.
type Store interface {
	ListItems(ctx context.Context) ([]Item, error)
	CountItems(ctx context.Context) (int64, error)
	InsertItem(ctx context.Context, name, description string) error
	FindUserByUsername(ctx context.Context, username string) (User, error)
	CreateUser(ctx context.Context, username, passwordHash string) (User, error)
	DeleteUser(ctx context.Context, username string) error
}

// SplitStatements chops a schema file into individual statements, because the pgx
// extended protocol refuses more than one command per Exec.
func SplitStatements(schema string) []string {
	statements := make([]string, 0, 4)
	for _, chunk := range strings.Split(schema, ";") {
		if statement := strings.TrimSpace(chunk); statement != "" {
			statements = append(statements, statement)
		}
	}
	return statements
}
