// Command backend-go-stdlib serves the reference JSON API with net/http only —
// the framework-free style of the Selenoid hub.
package main

import (
	"context"
	_ "embed"
	"errors"
	"log"
	"net"
	"net/http"
	"os/signal"
	"syscall"
	"time"

	"dev.multistack/backend-go-stdlib/internal/api"
	"dev.multistack/backend-go-stdlib/internal/config"
	"dev.multistack/backend-go-stdlib/internal/observability"
	"dev.multistack/backend-go-stdlib/internal/security"
	"dev.multistack/backend-go-stdlib/internal/store"
)

// The schema travels inside the binary so the runtime image needs nothing but the binary.
//
//go:embed schema.sql
var schemaSQL string

const (
	databaseReadyTimeout = 60 * time.Second
	readHeaderTimeout    = 10 * time.Second
	shutdownTimeout      = 10 * time.Second
)

func main() {
	cfg := config.Load()

	pg, err := store.Open(cfg.DatabaseURL)
	if err != nil {
		log.Fatalf("%s: %v", cfg.ServiceName, err)
	}
	defer func() { _ = pg.Close() }()

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	if err := pg.WaitReady(ctx, databaseReadyTimeout); err != nil {
		log.Fatalf("%s: %v", cfg.ServiceName, err)
	}
	if err := pg.ApplySchema(ctx, schemaSQL); err != nil {
		log.Fatalf("%s: %v", cfg.ServiceName, err)
	}
	if err := store.Seed(ctx, pg, security.HashPassword); err != nil {
		log.Fatalf("%s: seed: %v", cfg.ServiceName, err)
	}

	handler := api.NewHandler(
		pg,
		security.NewTokenService(cfg.JWTSecret, cfg.JWTExpiration),
		cfg.ServiceName,
	)
	server := &http.Server{
		Addr:              net.JoinHostPort("0.0.0.0", cfg.ServerPort),
		Handler:           api.NewRouter(handler),
		ReadHeaderTimeout: readHeaderTimeout,
	}
	management := &http.Server{
		Addr:              net.JoinHostPort("0.0.0.0", cfg.ManagementPort),
		Handler:           observability.NewMux(),
		ReadHeaderTimeout: readHeaderTimeout,
	}

	go func() {
		log.Printf("%s management listening on %s", cfg.ServiceName, management.Addr)
		if err := management.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("%s: management: %v", cfg.ServiceName, err)
		}
	}()

	go func() {
		<-ctx.Done()
		shutdownCtx, cancel := context.WithTimeout(context.Background(), shutdownTimeout)
		defer cancel()
		if err := server.Shutdown(shutdownCtx); err != nil {
			log.Printf("%s: shutdown: %v", cfg.ServiceName, err)
		}
		if err := management.Shutdown(shutdownCtx); err != nil {
			log.Printf("%s: management shutdown: %v", cfg.ServiceName, err)
		}
	}()

	log.Printf("%s listening on %s", cfg.ServiceName, server.Addr)
	if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Fatalf("%s: %v", cfg.ServiceName, err)
	}
}
