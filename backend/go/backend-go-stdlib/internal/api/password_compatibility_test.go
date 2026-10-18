package api_test

import (
	"context"
	"dev.multistack/backend-go-stdlib/internal/store/storetest"
	"golang.org/x/crypto/bcrypt"
	"net/http"
	"strings"
	"testing"
)

func TestLegacyLoginPreservesStoredHash(t *testing.T) {
	for _, size := range []int{71, 72} {
		password := strings.Repeat("a", size)
		hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.MinCost)
		if err != nil {
			t.Fatal(err)
		}
		h := newHarness(t, storetest.New().WithUser("legacy", string(hash)))
		reply := h.do(t, http.MethodPost, "/api/auth/login", `{"username":"legacy","password":"`+password+`"}`, nil)
		want := http.StatusOK
		if size == 72 {
			want = http.StatusUnauthorized
		}
		if reply.Code != want {
			t.Fatalf("legacy %d bytes: HTTP %d, want %d", size, reply.Code, want)
		}
		if size == 72 {
			requireMessage(t, reply, "Wrong login or password")
		}
		user, err := h.store.FindUserByUsername(context.Background(), "legacy")
		if err != nil || user.PasswordHash != string(hash) {
			t.Fatal("legacy user or hash changed")
		}
	}
}
