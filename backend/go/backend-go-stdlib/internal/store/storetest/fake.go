// Package storetest provides an in-memory store.Store so handlers, and the seeder can be
// unit-tested without PostgreSQL.
package storetest

import (
	"context"
	"slices"

	"dev.multistack/backend-go-stdlib/internal/store"
)

// Fake records rows in memory. Any of the *Err fields short-circuits the matching method,
// which is how tests exercise the 500 paths.
type Fake struct {
	Items []store.Item
	Users []store.User

	ListItemsErr  error
	CountItemsErr error
	InsertItemErr error
	FindUserErr   error
	CreateUserErr error
	DeleteUserErr error

	nextItemID int64
	nextUserID int64
}

// New returns an empty Fake.
func New() *Fake {
	return &Fake{}
}

// WithItem appends a pre-existing item and returns the Fake for chaining.
func (f *Fake) WithItem(name, description string) *Fake {
	f.nextItemID++
	f.Items = append(f.Items, store.Item{ID: f.nextItemID, Name: name, Description: description})
	return f
}

// WithUser appends a pre-existing user and returns the Fake for chaining.
func (f *Fake) WithUser(username, passwordHash string) *Fake {
	f.nextUserID++
	f.Users = append(f.Users, store.User{ID: f.nextUserID, Username: username, PasswordHash: passwordHash})
	return f
}

// ListItems implements store.Store.
func (f *Fake) ListItems(context.Context) ([]store.Item, error) {
	if f.ListItemsErr != nil {
		return nil, f.ListItemsErr
	}
	return slices.Clone(f.Items), nil
}

// CountItems implements store.Store.
func (f *Fake) CountItems(context.Context) (int64, error) {
	if f.CountItemsErr != nil {
		return 0, f.CountItemsErr
	}
	return int64(len(f.Items)), nil
}

// InsertItem implements store.Store.
func (f *Fake) InsertItem(_ context.Context, name, description string) error {
	if f.InsertItemErr != nil {
		return f.InsertItemErr
	}
	f.WithItem(name, description)
	return nil
}

// FindUserByUsername implements store.Store.
func (f *Fake) FindUserByUsername(_ context.Context, username string) (store.User, error) {
	if f.FindUserErr != nil {
		return store.User{}, f.FindUserErr
	}
	for _, user := range f.Users {
		if user.Username == username {
			return user, nil
		}
	}
	return store.User{}, store.ErrUserNotFound
}

// CreateUser implements store.Store.
func (f *Fake) CreateUser(_ context.Context, username, passwordHash string) (store.User, error) {
	if f.CreateUserErr != nil {
		return store.User{}, f.CreateUserErr
	}
	for _, user := range f.Users {
		if user.Username == username {
			return store.User{}, store.ErrDuplicateUsername
		}
	}
	f.WithUser(username, passwordHash)
	return f.Users[len(f.Users)-1], nil
}

// DeleteUser implements store.Store.
func (f *Fake) DeleteUser(_ context.Context, username string) error {
	if f.DeleteUserErr != nil {
		return f.DeleteUserErr
	}
	f.Users = slices.DeleteFunc(f.Users, func(user store.User) bool {
		return user.Username == username
	})
	return nil
}
