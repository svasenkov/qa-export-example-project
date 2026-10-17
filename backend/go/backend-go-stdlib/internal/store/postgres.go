package store

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5/pgconn"
	_ "github.com/jackc/pgx/v5/stdlib" // database/sql driver "pgx"
)

const uniqueViolationCode = "23505"

// Postgres is the production Store.
type Postgres struct {
	db *sql.DB
}

// Open prepares a connection pool; it does not connect yet.
func Open(dsn string) (*Postgres, error) {
	db, err := sql.Open("pgx", dsn)
	if err != nil {
		return nil, fmt.Errorf("open database: %w", err)
	}
	db.SetMaxOpenConns(10)
	db.SetMaxIdleConns(5)
	db.SetConnMaxLifetime(30 * time.Minute)
	return &Postgres{db: db}, nil
}

// Close releases the pool.
func (p *Postgres) Close() error {
	return p.db.Close()
}

// WaitReady pings until the server answers, so a container start that races Postgres
// does not crash-loop.
func (p *Postgres) WaitReady(ctx context.Context, timeout time.Duration) error {
	deadline := time.Now().Add(timeout)
	for attempt := 1; ; attempt++ {
		pingCtx, cancel := context.WithTimeout(ctx, 3*time.Second)
		err := p.db.PingContext(pingCtx)
		cancel()
		if err == nil {
			return nil
		}
		if time.Now().After(deadline) {
			return fmt.Errorf("database not ready after %d attempts: %w", attempt, err)
		}
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(time.Second):
		}
	}
}

// ApplySchema runs schema.sql; every statement is idempotent.
func (p *Postgres) ApplySchema(ctx context.Context, schema string) error {
	for _, statement := range SplitStatements(schema) {
		if _, err := p.db.ExecContext(ctx, statement); err != nil {
			return fmt.Errorf("apply schema: %w", err)
		}
	}
	return nil
}

// ListItems returns every item ordered by id, as the contract requires.
func (p *Postgres) ListItems(ctx context.Context) ([]Item, error) {
	rows, err := p.db.QueryContext(ctx, `SELECT id, name, description FROM items ORDER BY id`)
	if err != nil {
		return nil, fmt.Errorf("list items: %w", err)
	}
	defer func() { _ = rows.Close() }()

	items := make([]Item, 0, 8)
	for rows.Next() {
		var item Item
		if err := rows.Scan(&item.ID, &item.Name, &item.Description); err != nil {
			return nil, fmt.Errorf("scan item: %w", err)
		}
		items = append(items, item)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("list items: %w", err)
	}
	return items, nil
}

// CountItems reports how many items exist; the seeder only fills an empty table.
func (p *Postgres) CountItems(ctx context.Context) (int64, error) {
	var count int64
	if err := p.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM items`).Scan(&count); err != nil {
		return 0, fmt.Errorf("count items: %w", err)
	}
	return count, nil
}

// InsertItem appends one seed row.
func (p *Postgres) InsertItem(ctx context.Context, name, description string) error {
	_, err := p.db.ExecContext(
		ctx,
		`INSERT INTO items (name, description) VALUES ($1, $2)`,
		name, description,
	)
	if err != nil {
		return fmt.Errorf("insert item: %w", err)
	}
	return nil
}

// FindUserByUsername returns ErrUserNotFound when nobody matches.
func (p *Postgres) FindUserByUsername(ctx context.Context, username string) (User, error) {
	var user User
	err := p.db.QueryRowContext(
		ctx,
		`SELECT id, username, password_hash FROM users WHERE username = $1`,
		username,
	).Scan(&user.ID, &user.Username, &user.PasswordHash)
	if errors.Is(err, sql.ErrNoRows) {
		return User{}, ErrUserNotFound
	}
	if err != nil {
		return User{}, fmt.Errorf("find user: %w", err)
	}
	return user, nil
}

// CreateUser inserts a user and translates a lost unique-constraint race into
// ErrDuplicateUsername, so the caller can still answer 409 instead of 500.
func (p *Postgres) CreateUser(ctx context.Context, username, passwordHash string) (User, error) {
	user := User{Username: username, PasswordHash: passwordHash}
	err := p.db.QueryRowContext(
		ctx,
		`INSERT INTO users (username, password_hash) VALUES ($1, $2) RETURNING id`,
		username, passwordHash,
	).Scan(&user.ID)
	if isUniqueViolation(err) {
		return User{}, ErrDuplicateUsername
	}
	if err != nil {
		return User{}, fmt.Errorf("create user: %w", err)
	}
	return user, nil
}

// DeleteUser removes a user row. An absent row is not an error: the caller already proved
// the user existed, and losing a race with a concurrent delete must not become a 500.
func (p *Postgres) DeleteUser(ctx context.Context, username string) error {
	if _, err := p.db.ExecContext(ctx, `DELETE FROM users WHERE username = $1`, username); err != nil {
		return fmt.Errorf("delete user: %w", err)
	}
	return nil
}

func isUniqueViolation(err error) bool {
	var pgErr *pgconn.PgError
	return errors.As(err, &pgErr) && pgErr.Code == uniqueViolationCode
}
