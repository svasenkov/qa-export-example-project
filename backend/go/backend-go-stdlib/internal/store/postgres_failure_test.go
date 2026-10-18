package store_test

import (
	"context"
	"dev.multistack/backend-go-stdlib/internal/store"
	"testing"
	"time"
)

func TestPostgresInvalidDSNFailsReadiness(t *testing.T) {
	pg, err := store.Open("://invalid")
	if err != nil {
		t.Fatal(err)
	}
	defer pg.Close()
	if err := pg.WaitReady(context.Background(), 0); err == nil {
		t.Fatal("invalid DSN became ready")
	}
}

func TestPostgresClosedPoolErrors(t *testing.T) {
	pg, err := store.Open("postgres://localhost/unused")
	if err != nil {
		t.Fatal(err)
	}
	if err := pg.Close(); err != nil {
		t.Fatal(err)
	}
	ctx := context.Background()
	if err := pg.WaitReady(ctx, 0); err == nil {
		t.Fatal("closed pool became ready")
	}
	cancelled, cancel := context.WithCancel(ctx)
	cancel()
	if err := pg.WaitReady(cancelled, time.Hour); err != context.Canceled {
		t.Fatalf("want cancellation, got %v", err)
	}
	if err := pg.ApplySchema(ctx, "CREATE TABLE absent(id int)"); err == nil {
		t.Fatal("schema error swallowed")
	}
	if _, err := pg.ListItems(ctx); err == nil {
		t.Fatal("list error swallowed")
	}
	if _, err := pg.CountItems(ctx); err == nil {
		t.Fatal("count error swallowed")
	}
	if err := pg.InsertItem(ctx, "name", "description"); err == nil {
		t.Fatal("insert error swallowed")
	}
	if _, err := pg.FindUserByUsername(ctx, "alice"); err == nil {
		t.Fatal("lookup error swallowed")
	}
	if _, err := pg.CreateUser(ctx, "alice", "hash"); err == nil {
		t.Fatal("create error swallowed")
	}
	if err := pg.DeleteUser(ctx, "alice"); err == nil {
		t.Fatal("delete error swallowed")
	}
}
