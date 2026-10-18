// Startup runs in-process: test seams replace the process exit and the real
// deadlines, while sockets, signals and PostgreSQL behave exactly as shipped.
package main

import (
	"context"
	"database/sql"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"net/url"
	"os"
	"runtime"
	"strings"
	"sync"
	"syscall"
	"testing"
	"time"

	"dev.multistack/backend-go-stdlib/internal/store"

	_ "github.com/jackc/pgx/v5/stdlib"
)

// stubFatalf turns process-fatal exits into a recorded call plus Goexit, so the
// invoking goroutine unwinds exactly as os.Exit would leave it — deferreds run.
func stubFatalf(t *testing.T) chan string {
	t.Helper()
	calls := make(chan string, 8)
	original := logFatalf
	logFatalf = func(format string, args ...any) {
		calls <- fmt.Sprintf(format, args...)
		runtime.Goexit()
	}
	t.Cleanup(func() { logFatalf = original })
	return calls
}

func expectFatalf(t *testing.T, calls chan string, want string) {
	t.Helper()
	select {
	case msg := <-calls:
		if !strings.Contains(msg, want) {
			t.Fatalf("fatal message = %q, want it to contain %q", msg, want)
		}
	case <-time.After(20 * time.Second):
		t.Fatalf("no fatal exit observed, want one containing %q", want)
	}
}

func expectCleanExit(t *testing.T, done chan struct{}, calls chan string) {
	t.Helper()
	select {
	case <-done:
	case <-time.After(20 * time.Second):
		t.Fatal("main did not return")
	}
	select {
	case msg := <-calls:
		t.Fatalf("unexpected fatal exit: %q", msg)
	default:
	}
}

func freePort(t *testing.T) string {
	t.Helper()
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("reserve port: %v", err)
	}
	defer func() { _ = listener.Close() }()
	return fmt.Sprintf("%d", listener.Addr().(*net.TCPAddr).Port)
}

// holdPort keeps a wildcard listener with SO_REUSEADDR off: Go marks every
// listener reusable, so without this the server would happily rebind the port.
func holdPort(t *testing.T) string {
	t.Helper()
	lc := net.ListenConfig{Control: func(_, _ string, c syscall.RawConn) error {
		var sockErr error
		_ = c.Control(func(fd uintptr) {
			sockErr = syscall.SetsockoptInt(int(fd), syscall.SOL_SOCKET, syscall.SO_REUSEADDR, 0)
		})
		return sockErr
	}}
	listener, err := lc.Listen(context.Background(), "tcp", "0.0.0.0:0")
	if err != nil {
		t.Fatalf("block port: %v", err)
	}
	t.Cleanup(func() { _ = listener.Close() })
	return fmt.Sprintf("%d", listener.Addr().(*net.TCPAddr).Port)
}

// openPartialRequest leaves a half-written request on the wire, pinning the
// connection in StateActive so Shutdown must wait out the deadline instead of
// closing idle connections instantly and returning nil.
func openPartialRequest(t *testing.T, port string) {
	t.Helper()
	conn, err := net.Dial("tcp", "127.0.0.1:"+port)
	if err != nil {
		t.Fatalf("dial: %v", err)
	}
	if _, err := conn.Write([]byte("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n")); err != nil {
		t.Fatalf("write partial request: %v", err)
	}
	t.Cleanup(func() { _ = conn.Close() })
}

func runMain(t *testing.T, env map[string]string) chan struct{} {
	t.Helper()
	for key, value := range env {
		t.Setenv(key, value)
	}
	done := make(chan struct{})
	go func() {
		defer close(done)
		main()
	}()
	return done
}

func waitHealthy(t *testing.T, base string) string {
	t.Helper()
	return waitStatusOK(t, base+"/api/health")
}

// waitStatusOK polls an endpoint until it answers 200, so startup waits are
// driven by real sockets instead of sleeps.
func waitStatusOK(t *testing.T, endpoint string) string {
	t.Helper()
	deadline := time.Now().Add(20 * time.Second)
	for time.Now().Before(deadline) {
		resp, err := http.Get(endpoint)
		if err == nil {
			body, _ := io.ReadAll(resp.Body)
			_ = resp.Body.Close()
			if resp.StatusCode == http.StatusOK {
				return string(body)
			}
		}
		time.Sleep(50 * time.Millisecond)
	}
	t.Fatalf("%s never became healthy", endpoint)
	return ""
}

// adminDB opens a session on the scratch cluster for fixture DDL. It needs the
// same TEST_DATABASE_URL superuser the integration tests use.
func adminDB(t *testing.T) *sql.DB {
	t.Helper()
	dsn := os.Getenv("TEST_DATABASE_URL")
	if dsn == "" {
		t.Skip("set TEST_DATABASE_URL to a scratch database to run the PostgreSQL tests")
	}
	db, err := sql.Open("pgx", dsn)
	if err != nil {
		t.Fatalf("open admin: %v", err)
	}
	t.Cleanup(func() { _ = db.Close() })
	return db
}

// scratchDatabase creates an isolated database so concurrent binaries never share
// tables; teardown drops it with FORCE to outlive the pool.
func scratchDatabase(t *testing.T, admin *sql.DB, name string) string {
	t.Helper()
	ctx := t.Context()
	if _, err := admin.ExecContext(ctx, "CREATE DATABASE "+name); err != nil {
		t.Fatalf("create %s: %v", name, err)
	}
	t.Cleanup(func() {
		cleanup, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		_, _ = admin.ExecContext(cleanup, "DROP DATABASE IF EXISTS "+name+" WITH (FORCE)")
	})
	base, err := url.Parse(os.Getenv("TEST_DATABASE_URL"))
	if err != nil {
		t.Fatalf("TEST_DATABASE_URL: %v", err)
	}
	base.Path = "/" + name
	return base.String()
}

// asRole rewrites a DSN to authenticate as the given role (trust auth — no password).
func asRole(t *testing.T, dsn, role string) string {
	t.Helper()
	parsed, err := url.Parse(dsn)
	if err != nil {
		t.Fatalf("dsn: %v", err)
	}
	parsed.User = url.User(role)
	return parsed.String()
}

// execIn runs admin DDL inside the freshly created database.
func execIn(t *testing.T, adminDSN, database string, statements ...string) {
	t.Helper()
	parsed, err := url.Parse(adminDSN)
	if err != nil {
		t.Fatalf("TEST_DATABASE_URL: %v", err)
	}
	parsed.Path = "/" + database
	db, err := sql.Open("pgx", parsed.String())
	if err != nil {
		t.Fatalf("open %s: %v", database, err)
	}
	defer func() { _ = db.Close() }()
	for _, statement := range statements {
		if _, err := db.ExecContext(t.Context(), statement); err != nil {
			t.Fatalf("%q: %v", statement, err)
		}
	}
}

func terminate(t *testing.T, done chan struct{}) {
	t.Helper()
	if err := syscall.Kill(syscall.Getpid(), syscall.SIGTERM); err != nil {
		t.Fatalf("SIGTERM: %v", err)
	}
	expectCleanExit(t, done, nil)
}

func TestStartupServesUntilSignal(t *testing.T) {
	admin := adminDB(t)
	dsn := scratchDatabase(t, admin, fmt.Sprintf("qa_boot_%d", time.Now().UnixNano()))
	fatalf := stubFatalf(t)
	port, mgmt := freePort(t), freePort(t)

	done := runMain(t, map[string]string{
		"DATABASE_URL":    dsn,
		"SERVER_PORT":     port,
		"MANAGEMENT_PORT": mgmt,
	})

	base := "http://127.0.0.1:" + port
	if body := waitHealthy(t, base); body != `{"status":"ok","service":"backend-go-stdlib"}` {
		t.Fatalf("health = %s", body)
	}
	// The real store was seeded at startup: items answer through PostgreSQL.
	resp, err := http.Get(base + "/api/items")
	if err != nil {
		t.Fatalf("items: %v", err)
	}
	_ = resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("items status = %d", resp.StatusCode)
	}
	mgmtResp, err := http.Get("http://127.0.0.1:" + mgmt + "/actuator/health")
	if err != nil {
		t.Fatalf("management health: %v", err)
	}
	_ = mgmtResp.Body.Close()
	if mgmtResp.StatusCode != http.StatusOK {
		t.Fatalf("management status = %d", mgmtResp.StatusCode)
	}

	if err := syscall.Kill(syscall.Getpid(), syscall.SIGTERM); err != nil {
		t.Fatalf("SIGTERM: %v", err)
	}
	expectCleanExit(t, done, fatalf)
}

func TestStartupShutdownDeadlineIsLogged(t *testing.T) {
	admin := adminDB(t)
	dsn := scratchDatabase(t, admin, fmt.Sprintf("qa_boot_%d", time.Now().UnixNano()))
	fatalf := stubFatalf(t)
	original := shutdownTimeout
	shutdownTimeout = time.Nanosecond
	t.Cleanup(func() { shutdownTimeout = original })

	var logsMu sync.Mutex
	var logs strings.Builder
	origWriter := log.Writer()
	log.SetOutput(&lockedWriter{mu: &logsMu, w: &logs})
	t.Cleanup(func() { log.SetOutput(origWriter) })

	port, mgmt := freePort(t), freePort(t)
	done := runMain(t, map[string]string{
		"DATABASE_URL":    dsn,
		"SERVER_PORT":     port,
		"MANAGEMENT_PORT": mgmt,
	})
	waitHealthy(t, "http://127.0.0.1:"+port)
	waitStatusOK(t, "http://127.0.0.1:"+mgmt+"/actuator/health")
	// In-flight connections on both listeners force Shutdown into its wait loop,
	// where the expired deadline is what produces the logged errors.
	openPartialRequest(t, port)
	openPartialRequest(t, mgmt)
	time.Sleep(100 * time.Millisecond)

	if err := syscall.Kill(syscall.Getpid(), syscall.SIGTERM); err != nil {
		t.Fatalf("SIGTERM: %v", err)
	}
	expectCleanExit(t, done, fatalf)

	deadline := time.Now().Add(10 * time.Second)
	for time.Now().Before(deadline) {
		logsMu.Lock()
		out := logs.String()
		logsMu.Unlock()
		if strings.Contains(out, "shutdown:") && strings.Contains(out, "management shutdown:") {
			return
		}
		time.Sleep(50 * time.Millisecond)
	}
	t.Fatalf("shutdown errors never logged:\n%s", logs.String())
}

type lockedWriter struct {
	mu *sync.Mutex
	w  *strings.Builder
}

func (l *lockedWriter) Write(p []byte) (int, error) {
	l.mu.Lock()
	defer l.mu.Unlock()
	return l.w.Write(p)
}

func TestStartupFailsWhenStoreCannotOpen(t *testing.T) {
	fatalf := stubFatalf(t)
	failure := fmt.Errorf("driver missing")
	original := openStore
	openStore = func(string) (*store.Postgres, error) { return nil, failure }
	t.Cleanup(func() { openStore = original })

	done := runMain(t, map[string]string{"DATABASE_URL": "postgres://unused"})
	expectFatalf(t, fatalf, failure.Error())
	expectCleanExit(t, done, fatalf)
}

func TestStartupFailsWhenDatabaseNeverReady(t *testing.T) {
	fatalf := stubFatalf(t)
	original := databaseReadyTimeout
	databaseReadyTimeout = 100 * time.Millisecond
	t.Cleanup(func() { databaseReadyTimeout = original })

	done := runMain(t, map[string]string{
		// Nothing listens on port 1, so every ping fails until the deadline.
		"DATABASE_URL": "postgres://127.0.0.1:1/unreachable?sslmode=disable",
	})
	expectFatalf(t, fatalf, "database not ready")
	expectCleanExit(t, done, fatalf)
}

func TestStartupFailsWithoutSchemaPrivileges(t *testing.T) {
	admin := adminDB(t)
	suffix := fmt.Sprintf("%d", time.Now().UnixNano())
	role := "qa_ro_" + suffix
	database := "qa_boot_" + suffix
	if _, err := admin.ExecContext(t.Context(), "CREATE ROLE "+role+" LOGIN"); err != nil {
		t.Fatalf("create role: %v", err)
	}
	t.Cleanup(func() {
		cleanup, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		_, _ = admin.ExecContext(cleanup, "DROP ROLE IF EXISTS "+role)
	})
	dsn := scratchDatabase(t, admin, database)
	execIn(t, os.Getenv("TEST_DATABASE_URL"), database,
		"REVOKE CREATE ON SCHEMA public FROM PUBLIC",
		"GRANT CONNECT ON DATABASE "+database+" TO "+role,
		"GRANT USAGE ON SCHEMA public TO "+role,
	)

	fatalf := stubFatalf(t)
	done := runMain(t, map[string]string{
		"DATABASE_URL":    asRole(t, dsn, role),
		"SERVER_PORT":     freePort(t),
		"MANAGEMENT_PORT": freePort(t),
	})
	expectFatalf(t, fatalf, "apply schema")
	expectCleanExit(t, done, fatalf)
}

func TestStartupFailsWhenSeedCannotWrite(t *testing.T) {
	admin := adminDB(t)
	suffix := fmt.Sprintf("%d", time.Now().UnixNano())
	role := "qa_ro_" + suffix
	database := "qa_boot_" + suffix
	if _, err := admin.ExecContext(t.Context(), "CREATE ROLE "+role+" LOGIN"); err != nil {
		t.Fatalf("create role: %v", err)
	}
	t.Cleanup(func() {
		cleanup, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		_, _ = admin.ExecContext(cleanup, "DROP ROLE IF EXISTS "+role)
	})
	dsn := scratchDatabase(t, admin, database)
	execIn(t, os.Getenv("TEST_DATABASE_URL"), database,
		"GRANT CONNECT ON DATABASE "+database+" TO "+role,
		"GRANT USAGE, CREATE ON SCHEMA public TO "+role,
		// Seed reads the view fine (0 rows) but the insert is refused by PostgreSQL.
		"CREATE VIEW items AS SELECT NULL::bigint AS id, NULL::varchar(128) AS name, NULL::varchar(512) AS description WHERE false",
		"GRANT SELECT ON items TO "+role,
	)

	fatalf := stubFatalf(t)
	done := runMain(t, map[string]string{
		"DATABASE_URL":    asRole(t, dsn, role),
		"SERVER_PORT":     freePort(t),
		"MANAGEMENT_PORT": freePort(t),
	})
	expectFatalf(t, fatalf, "seed:")
	expectCleanExit(t, done, fatalf)
}

func TestStartupFailsWhenManagementPortTaken(t *testing.T) {
	admin := adminDB(t)
	dsn := scratchDatabase(t, admin, fmt.Sprintf("qa_boot_%d", time.Now().UnixNano()))
	fatalf := stubFatalf(t)
	// Hold the management port so its ListenAndServe fails inside the goroutine.
	mgmt := holdPort(t)

	done := runMain(t, map[string]string{
		"DATABASE_URL":    dsn,
		"SERVER_PORT":     freePort(t),
		"MANAGEMENT_PORT": mgmt,
	})
	expectFatalf(t, fatalf, "management:")
	terminate(t, done)
}

func TestStartupFailsWhenServerPortTaken(t *testing.T) {
	admin := adminDB(t)
	dsn := scratchDatabase(t, admin, fmt.Sprintf("qa_boot_%d", time.Now().UnixNano()))
	fatalf := stubFatalf(t)

	done := runMain(t, map[string]string{
		"DATABASE_URL":    dsn,
		"SERVER_PORT":     holdPort(t),
		"MANAGEMENT_PORT": freePort(t),
	})
	expectFatalf(t, fatalf, "backend-go-stdlib:")
	expectCleanExit(t, done, fatalf)
}
