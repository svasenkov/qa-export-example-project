package store_test

import (
	"context"
	"testing"
)

// A NULL id reaches Scan and must surface as the "scan item" error, not a panic
// or a silently skipped row.
func TestPostgresScanErrorPropagates(t *testing.T) {
	pg, ctx := openPostgres(t)

	for _, statement := range []string{
		"DROP TABLE items",
		"CREATE TABLE items (id BIGINT, name VARCHAR(128) NOT NULL, description VARCHAR(512) NOT NULL)",
		"INSERT INTO items (id, name, description) VALUES (NULL, 'bad', 'row')",
	} {
		if err := pg.ApplySchema(ctx, statement); err != nil {
			t.Fatalf("%q: %v", statement, err)
		}
	}

	if _, err := pg.ListItems(ctx); err == nil {
		t.Fatal("ListItems on a NULL id returned nil error")
	}
}

// A cancelled context must surface as an error from ListItems, not be swallowed
// into a silent empty result. Mid-iteration driver failures are covered
// deterministically by the stub-driver test in postgres_internal_test.go —
// pgx reads the full result set inside QueryContext, so a real PostgreSQL
// cancellation arrives there rather than at rows.Err.
func TestPostgresIterationErrorPropagates(t *testing.T) {
	pg, ctx := openPostgres(t)

	iterCtx, cancel := context.WithCancel(ctx)
	cancel()
	if _, err := pg.ListItems(iterCtx); err == nil {
		t.Fatal("ListItems on a cancelled context returned nil error")
	}
}
