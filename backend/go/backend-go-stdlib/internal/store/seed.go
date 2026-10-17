package store

import (
	"context"
	"errors"
	"fmt"
)

// SeedUser credentials are the same demo account every reference backend ships.
const (
	SeedUsername = "user1"
	SeedPassword = "password1"
)

// SeedItems must stay byte-for-byte identical to the other backends, em-dash included.
var SeedItems = []Item{
	{Name: "Alpha", Description: "First seeded item from PostgreSQL"},
	{Name: "Beta", Description: "Second seeded item for demo API"},
	{Name: "Gamma", Description: "Third item — multistack bootstrap"},
}

// Seed is idempotent: items are only written into an empty table and the demo user only
// when absent, so restarts never duplicate rows.
func Seed(ctx context.Context, s Store, hash func(string) (string, error)) error {
	count, err := s.CountItems(ctx)
	if err != nil {
		return err
	}
	if count == 0 {
		for _, item := range SeedItems {
			if err := s.InsertItem(ctx, item.Name, item.Description); err != nil {
				return err
			}
		}
	}

	switch _, err := s.FindUserByUsername(ctx, SeedUsername); {
	case err == nil:
		return nil
	case !errors.Is(err, ErrUserNotFound):
		return err
	}

	passwordHash, err := hash(SeedPassword)
	if err != nil {
		return fmt.Errorf("hash seed password: %w", err)
	}
	// A parallel replica may have inserted the same user meanwhile; that is not a failure.
	if _, err := s.CreateUser(ctx, SeedUsername, passwordHash); err != nil && !errors.Is(err, ErrDuplicateUsername) {
		return err
	}
	return nil
}
