package store_test

import (
	"context"
	"errors"
	"fmt"
	"net/url"
	"os"
	"testing"
	"time"

	"dev.multistack/backend-go-stdlib/internal/store"
)

// openPostgres skips unless TEST_DATABASE_URL points at a scratch database, so the
// default `go test ./...` stays database-free.
func openPostgres(t *testing.T) (*store.Postgres, context.Context) {
	t.Helper()
	dsn := os.Getenv("TEST_DATABASE_URL")
	if dsn == "" {
		t.Skip("set TEST_DATABASE_URL to a scratch database to run the PostgreSQL tests")
	}

	// Every test owns its schema, including on assertion failure. Never truncate
	// application tables or rely on leftovers from a previous attempt.
	admin, err := store.Open(dsn)
	if err != nil {
		t.Fatalf("Open admin: %v", err)
	}
	t.Cleanup(func() { _ = admin.Close() })
	ctx := t.Context()
	if err := admin.WaitReady(ctx, 30*time.Second); err != nil {
		t.Fatalf("WaitReady: %v", err)
	}
	schemaName := fmt.Sprintf("qa_%d", time.Now().UnixNano())
	if err := admin.ApplySchema(ctx, "CREATE SCHEMA "+schemaName); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		cleanup, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		if err := admin.ApplySchema(cleanup, "DROP SCHEMA "+schemaName+" CASCADE"); err != nil {
			t.Errorf("cleanup schema: %v", err)
		}
	})
	parsed, err := url.Parse(dsn)
	if err != nil {
		t.Fatal(err)
	}
	query := parsed.Query()
	query.Set("search_path", schemaName)
	parsed.RawQuery = query.Encode()
	pg, err := store.Open(parsed.String())
	if err != nil {
		t.Fatalf("Open isolated schema: %v", err)
	}
	t.Cleanup(func() { _ = pg.Close() })
	schema, err := os.ReadFile("../../schema.sql")
	if err != nil {
		t.Fatalf("read schema.sql: %v", err)
	}
	if err := pg.ApplySchema(ctx, string(schema)); err != nil {
		t.Fatalf("ApplySchema: %v", err)
	}
	return pg, ctx
}

func TestPostgresItems(t *testing.T) {
	pg, ctx := openPostgres(t)

	before, err := pg.CountItems(ctx)
	if err != nil {
		t.Fatalf("CountItems: %v", err)
	}

	name := fmt.Sprintf("Item-%d", time.Now().UnixNano())
	if err := pg.InsertItem(ctx, name, "inserted by the integration test"); err != nil {
		t.Fatalf("InsertItem: %v", err)
	}

	after, err := pg.CountItems(ctx)
	if err != nil {
		t.Fatalf("CountItems: %v", err)
	}
	if after != before+1 {
		t.Fatalf("count = %d, want %d", after, before+1)
	}

	items, err := pg.ListItems(ctx)
	if err != nil {
		t.Fatalf("ListItems: %v", err)
	}
	if int64(len(items)) != after {
		t.Fatalf("listed %d items, want %d", len(items), after)
	}
	for i := 1; i < len(items); i++ {
		if items[i-1].ID >= items[i].ID {
			t.Fatalf("items are not ordered by id: %v", items)
		}
	}
	if last := items[len(items)-1]; last.Name != name {
		t.Fatalf("last item = %+v, want the freshly inserted %q", last, name)
	}
}

func TestPostgresUsers(t *testing.T) {
	pg, ctx := openPostgres(t)
	username := fmt.Sprintf("integration-%d", time.Now().UnixNano())

	created, err := pg.CreateUser(ctx, username, "hash")
	if err != nil {
		t.Fatalf("CreateUser: %v", err)
	}
	if created.ID == 0 {
		t.Fatal("CreateUser returned an unsaved user")
	}

	found, err := pg.FindUserByUsername(ctx, username)
	if err != nil {
		t.Fatalf("FindUserByUsername: %v", err)
	}
	if found.ID != created.ID || found.PasswordHash != "hash" {
		t.Fatalf("found = %+v, want %+v", found, created)
	}

	// The unique constraint, not the handler's pre-check, must produce this error.
	if _, err := pg.CreateUser(ctx, username, "hash"); !errors.Is(err, store.ErrDuplicateUsername) {
		t.Fatalf("second CreateUser error = %v, want ErrDuplicateUsername", err)
	}

	if _, err := pg.FindUserByUsername(ctx, username+"-missing"); !errors.Is(err, store.ErrUserNotFound) {
		t.Fatalf("FindUserByUsername error = %v, want ErrUserNotFound", err)
	}

	if err := pg.DeleteUser(ctx, username); err != nil {
		t.Fatalf("DeleteUser: %v", err)
	}
	if _, err := pg.FindUserByUsername(ctx, username); !errors.Is(err, store.ErrUserNotFound) {
		t.Fatalf("FindUserByUsername after delete = %v, want ErrUserNotFound", err)
	}
	// Deleting an already-deleted user is a no-op, not an error.
	if err := pg.DeleteUser(ctx, username); err != nil {
		t.Fatalf("second DeleteUser: %v", err)
	}
}

func TestPostgresSeedIsIdempotent(t *testing.T) {
	pg, ctx := openPostgres(t)

	hash := func(password string) (string, error) { return "hash:" + password, nil }
	if err := store.Seed(ctx, pg, hash); err != nil {
		t.Fatalf("first Seed: %v", err)
	}
	first, err := pg.CountItems(ctx)
	if err != nil {
		t.Fatalf("CountItems: %v", err)
	}
	if err := store.Seed(ctx, pg, hash); err != nil {
		t.Fatalf("second Seed: %v", err)
	}
	second, err := pg.CountItems(ctx)
	if err != nil {
		t.Fatalf("CountItems: %v", err)
	}
	if first != second {
		t.Fatalf("items grew from %d to %d across two seeds", first, second)
	}
	if _, err := pg.FindUserByUsername(ctx, store.SeedUsername); err != nil {
		t.Fatalf("seed user missing: %v", err)
	}
}

// REG-003: exercise the real unique constraint under competing inserts; the
// handler's mapping of ErrDuplicateUsername to 409 is covered in handler_test.go.
func TestPostgresConcurrentUsername(t *testing.T) {
	pg, ctx := openPostgres(t)
	username := fmt.Sprintf("race-%d", time.Now().UnixNano())
	t.Cleanup(func() {
		if err := pg.DeleteUser(context.Background(), username); err != nil {
			t.Errorf("cleanup concurrent user: %v", err)
		}
	})
	start := make(chan struct{})
	results := make(chan error, 2)
	for range 2 {
		go func() {
			<-start
			_, err := pg.CreateUser(ctx, username, "hash")
			results <- err
		}()
	}
	close(start)
	successes, conflicts := 0, 0
	for range 2 {
		err := <-results
		switch {
		case err == nil:
			successes++
		case errors.Is(err, store.ErrDuplicateUsername):
			conflicts++
		default:
			t.Errorf("unexpected insert error: %v", err)
		}
	}
	if successes != 1 || conflicts != 1 {
		t.Fatalf("inserts: %d succeeded, %d conflicted; want one of each", successes, conflicts)
	}
	if _, err := pg.FindUserByUsername(ctx, username); err != nil {
		t.Fatalf("winner not persisted: %v", err)
	}
}
