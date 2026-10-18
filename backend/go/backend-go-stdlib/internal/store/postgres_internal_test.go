package store

import (
	"context"
	"database/sql"
	"database/sql/driver"
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

// The pgx driver parses the DSN lazily, so sql.Open never fails in production;
// the seam proves Open still propagates a driver-level rejection.
func TestOpenPropagatesDriverError(t *testing.T) {
	failure := errors.New("driver rejected the dsn")
	original := sqlOpen
	sqlOpen = func(_, _ string) (*sql.DB, error) { return nil, failure }
	t.Cleanup(func() { sqlOpen = original })

	if _, err := Open("postgres://irrelevant"); !errors.Is(err, failure) {
		t.Fatalf("Open error = %v, want %v", err, failure)
	}
}

var errStreamBroken = errors.New("stream broke mid-iteration")

// midFailRows yields one valid row and then fails: pgx buffers wire errors
// eagerly, so only a driver stub reaches the rows.Err branch deterministically.
type midFailRows struct{ emitted bool }

func (*midFailRows) Columns() []string { return []string{"id", "name", "description"} }
func (*midFailRows) Close() error      { return nil }
func (r *midFailRows) Next(dest []driver.Value) error {
	if r.emitted {
		return errStreamBroken
	}
	r.emitted = true
	dest[0], dest[1], dest[2] = int64(1), "only", "row"
	return nil
}

type stubConnector struct{}

func (stubConnector) Connect(context.Context) (driver.Conn, error) { return stubConn{}, nil }
func (stubConnector) Driver() driver.Driver                        { return stubDriver{} }

type stubDriver struct{}

func (stubDriver) Open(string) (driver.Conn, error) { return stubConn{}, nil }

type stubConn struct{}

func (stubConn) Prepare(string) (driver.Stmt, error) { return nil, errors.New("prepare unsupported") }
func (stubConn) Close() error                        { return nil }
func (stubConn) Begin() (driver.Tx, error)           { return nil, errors.New("tx unsupported") }
func (stubConn) QueryContext(context.Context, string, []driver.NamedValue) (driver.Rows, error) {
	return &midFailRows{}, nil
}

var _ driver.QueryerContext = stubConn{}

// A stream failure between rows must surface through rows.Err as "list items",
// not be swallowed by the loop exiting on a nil Next result.
func TestListItemsReportsMidIterationError(t *testing.T) {
	pg := &Postgres{db: sql.OpenDB(stubConnector{})}
	t.Cleanup(func() { _ = pg.Close() })

	items, err := pg.ListItems(context.Background())
	if !errors.Is(err, errStreamBroken) {
		t.Fatalf("ListItems error = %v, want wrapped %v", err, errStreamBroken)
	}
	if items != nil {
		t.Fatalf("ListItems items = %v, want nil", items)
	}
}
