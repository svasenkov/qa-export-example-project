package api.model;

/** Request body for {@code POST /api/auth/login} — serialized by Rest Assured via Jackson. */
public record LoginRequest(String username, String password) {
}
