package api.model;

/** Request body for {@code POST /api/auth/register} — serialized by Rest Assured via Jackson. */
public record RegisterRequest(String username, String password) {
}
