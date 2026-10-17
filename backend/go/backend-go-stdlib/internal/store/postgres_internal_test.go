package store

import (
	"errors"
	"fmt"
	"testing"

	"github.com/jackc/pgx/v5/pgconn"
)

func TestSplitStatements(t *testing.T) {
	statements := SplitStatements(`
CREATE TABLE IF NOT EXISTS items (
    id BIGSERIAL PRIMARY KEY
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users (username);
`)

	if len(statements) != 2 {
		t.Fatalf("statements = %d, want 2: %q", len(statements), statements)
	}
	if statements[1] != "CREATE INDEX IF NOT EXISTS idx_users_username ON users (username)" {
		t.Fatalf("second statement = %q", statements[1])
	}
}

func TestSplitStatementsIgnoresBlanks(t *testing.T) {
	if statements := SplitStatements("  \n ; ;\n"); len(statements) != 0 {
		t.Fatalf("statements = %q, want none", statements)
	}
}

func TestIsUniqueViolation(t *testing.T) {
	cases := []struct {
		name string
		err  error
		want bool
	}{
		{name: "nil", err: nil},
		{name: "plain error", err: errors.New("boom")},
		{name: "other pg error", err: &pgconn.PgError{Code: "42P01"}},
		{name: "unique violation", err: &pgconn.PgError{Code: uniqueViolationCode}, want: true},
		{name: "wrapped unique violation", err: fmt.Errorf("create user: %w", &pgconn.PgError{Code: uniqueViolationCode}), want: true},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if got := isUniqueViolation(tc.err); got != tc.want {
				t.Fatalf("isUniqueViolation = %v, want %v", got, tc.want)
			}
		})
	}
}
