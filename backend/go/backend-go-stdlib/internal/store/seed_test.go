package store_test

import (
	"context"
	"errors"
	"testing"

	"dev.multistack/backend-go-stdlib/internal/store"
	"dev.multistack/backend-go-stdlib/internal/store/storetest"
)

func hashStub(password string) (string, error) { return "hash:" + password, nil }

func TestSeedFillsEmptyDatabase(t *testing.T) {
	fake := storetest.New()

	if err := store.Seed(context.Background(), fake, hashStub); err != nil {
		t.Fatalf("Seed: %v", err)
	}

	if len(fake.Items) != len(store.SeedItems) {
		t.Fatalf("items = %d, want %d", len(fake.Items), len(store.SeedItems))
	}
	for i, item := range store.SeedItems {
		if fake.Items[i].Name != item.Name || fake.Items[i].Description != item.Description {
			t.Fatalf("item %d = %+v, want %+v", i, fake.Items[i], item)
		}
	}
	if want := "Third item — multistack bootstrap"; fake.Items[2].Description != want {
		t.Fatalf("third description = %q, want %q (em-dash)", fake.Items[2].Description, want)
	}

	user, err := fake.FindUserByUsername(context.Background(), store.SeedUsername)
	if err != nil {
		t.Fatalf("seed user missing: %v", err)
	}
	if user.PasswordHash != "hash:"+store.SeedPassword {
		t.Fatalf("password hash = %q", user.PasswordHash)
	}
}

func TestSeedIsIdempotent(t *testing.T) {
	fake := storetest.New()
	ctx := context.Background()

	if err := store.Seed(ctx, fake, hashStub); err != nil {
		t.Fatalf("first Seed: %v", err)
	}
	if err := store.Seed(ctx, fake, hashStub); err != nil {
		t.Fatalf("second Seed: %v", err)
	}

	if len(fake.Items) != len(store.SeedItems) {
		t.Fatalf("items = %d after two runs, want %d", len(fake.Items), len(store.SeedItems))
	}
	if len(fake.Users) != 1 {
		t.Fatalf("users = %d after two runs, want 1", len(fake.Users))
	}
}

func TestSeedKeepsExistingItems(t *testing.T) {
	fake := storetest.New().WithItem("Custom", "Left alone")

	if err := store.Seed(context.Background(), fake, hashStub); err != nil {
		t.Fatalf("Seed: %v", err)
	}

	if len(fake.Items) != 1 {
		t.Fatalf("items = %d, want the table untouched", len(fake.Items))
	}
}

func TestSeedToleratesLostRace(t *testing.T) {
	fake := storetest.New()
	fake.CreateUserErr = store.ErrDuplicateUsername

	if err := store.Seed(context.Background(), fake, hashStub); err != nil {
		t.Fatalf("Seed: %v, want a concurrent insert to be tolerated", err)
	}
}

func TestSeedPropagatesErrors(t *testing.T) {
	failure := errors.New("db down")

	cases := map[string]func(*storetest.Fake){
		"count":  func(f *storetest.Fake) { f.CountItemsErr = failure },
		"insert": func(f *storetest.Fake) { f.InsertItemErr = failure },
		"find":   func(f *storetest.Fake) { f.FindUserErr = failure },
		"create": func(f *storetest.Fake) { f.CreateUserErr = failure },
	}

	for name, breakIt := range cases {
		t.Run(name, func(t *testing.T) {
			fake := storetest.New()
			breakIt(fake)

			if err := store.Seed(context.Background(), fake, hashStub); !errors.Is(err, failure) {
				t.Fatalf("Seed error = %v, want %v", err, failure)
			}
		})
	}
}

func TestSeedPropagatesHashFailure(t *testing.T) {
	failure := errors.New("bcrypt refused")
	hash := func(string) (string, error) { return "", failure }

	if err := store.Seed(context.Background(), storetest.New(), hash); !errors.Is(err, failure) {
		t.Fatalf("Seed error = %v, want %v", err, failure)
	}
}
