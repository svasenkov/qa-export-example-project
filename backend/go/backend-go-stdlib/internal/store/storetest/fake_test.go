package storetest_test

import (
	"context"
	"errors"
	"testing"

	"dev.multistack/backend-go-stdlib/internal/store"
	"dev.multistack/backend-go-stdlib/internal/store/storetest"
)

// The fake must mirror the Postgres contract: a repeated username is
// ErrDuplicateUsername even without the handler's pre-check.
func TestFakeCreateUserDuplicate(t *testing.T) {
	fake := storetest.New().WithUser("taken", "hash")

	if _, err := fake.CreateUser(context.Background(), "taken", "hash2"); !errors.Is(err, store.ErrDuplicateUsername) {
		t.Fatalf("CreateUser error = %v, want ErrDuplicateUsername", err)
	}
	users := fake.Users
	if len(users) != 1 || users[0].PasswordHash != "hash" {
		t.Fatalf("users = %+v, want the original row untouched", users)
	}
}
