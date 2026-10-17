# Page objects

**autotests-ai-multistack-app** — home page at app root. Resolved via `baseUrl` in `config/${env}.properties`.

| Page | Class | Open |
|------|-------|------|
| Home | `HomePage` | `open("/")` → `GET /` |
| Login | `LoginPage` | `open("/login")` |
| Register | `RegisterPage` | `open("/register")` |
| Header | `HeaderComponent` via `BasePage.header` | desktop `header-nav-*` · burger `header-menu-nav-*` |

Post-auth state (welcome message, logout) lives on `HomePage` at `/`.

## Stands

`ci.properties`: `baseUrl=http://localhost:9821/` (stand-gateway-ci) · `prod.properties`: the deployed host.
