---
id: login-flow
version: 0.9
acceptance:
  - User opens login page
  - Empty login → validation error visible
  - Empty password → validation error visible
  - Credentials below field minimums → client-side form error (exact i18n literals in formValidation)
  - Client-side validation failure → form error shown, no API call made
  - Known user + wrong password of valid length → API 401, form error visible, no session created
  - Valid credentials → redirect to home (/)
  - User can register on /register
  - Valid register → 201, session created immediately, lands on home welcome (register is a login)
  - Duplicate username on register → API 409, form error verbatim, no session created
  - Logged-in user can logout → back to login, `authToken` cleared (client-side; the JWT is not revoked server-side)
  - Logged-in user can delete account after confirm → back to login, session cleared
  - Cancelling delete confirm keeps the session
  - Logged-in session survives a page reload (token read from localStorage)
  - Invalid or expired stored token → session cleared on home, welcome hidden
  - Auth controls expose id + name (Chrome autofill / Issues audit)

pages:
  home: /
  login: /login
  register: /register

api:
  login: POST /api/auth/login
  register: POST /api/auth/register
  logout: POST /api/auth/logout
  profile: GET /api/auth/me
  deleteAccount: DELETE /api/auth/me
  health: GET /api/health

storage:
  authToken: localStorage key `authToken`, scoped `authToken:<backendId>` under a matrix mount (authTokenStorageKey in appBase.ts); e2e shortcut allowed per `docs/qa/`

# Form controls — SSOT for vanilla HTML, React PlaqueField, Vue PlaqueField.
# Every `input`/`select`/`textarea` must have `id` or `name` (prefer both).
# Auth `name` follows autocomplete tokens (not the testid string).
formControls:
  login:
    id: login-input
    name: username
    autocomplete: username
    testid: login-input
  password:
    id: password-input
    name: password
    autocomplete: current-password
    testid: password-input
  registerLogin:
    id: register-login-input
    name: username
    autocomplete: username
    testid: register-login-input
  registerPassword:
    id: register-password-input
    name: password
    autocomplete: new-password
    testid: register-password-input
  confirmPassword:
    id: confirm-password-input
    name: confirm-password
    autocomplete: new-password
    testid: confirm-password-input
  registerSubmit:
    id: register-submit-button
    testid: register-submit-button
  registerError:
    id: register-error-message
    testid: register-error-message
  headerSearch:
    id: header-search-input
    name: header-search
    testid: header-search-input
  headerMenuSearch:
    id: header-menu-search-input
    name: header-menu-search
    testid: header-menu-search-input

# Negative credentials — known user + wrong password of valid length.
# Coverage guidance: docs/qa/testing.md (negative scenarios and appropriate assertions).
# Project convention: the 401 JSON is checked on the api layer, the form text on
# e2e/component — two separate tests, not a hard rule about one test's contents.
wrongPassword:
  preconditions:
    fixture: user1 / password1  # seeded demo account — internal/store/seed.go SeedUsername/SeedPassword
    request: LoginRequest("user1", "wrongpassword")  # known user + wrong password; 13 chars ≥ MIN_PASSWORD_LENGTH=6 and PasswordMaxLength=128 → client + server validation pass, the call reaches the API
    session: fresh — no prior successful login; no `authToken`/`authToken:<backendId>` in localStorage before submit
  api:
    endpoint: POST /api/auth/login
    status: 401
    body: '{"message": "Wrong login or password"}'  # envelope schemas/error.json — {message} only, no extra fields
    unknownUser: an unknown username gets the same status 401 and the same body
    ownedBy: tests/api — AuthApiTests#loginWithInvalidPassword · loginWithUnknownUsername · loginMinLengthUnknownUserIsUnauthorized
  form:
    testid: error-message     # p#error-message, aria-live="polite"
    text: API `message` verbatim — shown in any UI lang
    fallback:                 # resolveAuthErrorMessage order (auth.ts)
      - err.network → i18n `errorNetwork`
      - non-empty err.message → verbatim — apiRequest throws body.message when it is a string;
        a missing or non-string body.message throws 'Request failed', which is non-empty and
        therefore also shown verbatim, NOT replaced by the i18n fallback
      - empty-string body.message (err.message === '') → i18n `errorWrongCredentials` —
        empty and missing body.message are different cases, do not merge them
    ownedBy: tests/e2e — LoginTests#shouldShowErrorWhenPasswordIsWrong (submitExpectingError) · component src/test/pages/LoginPage.test.ts
    goldenId: login-wrong-password-e2e  # the form story, not the 401
  session:
    authToken: absent — saveSession runs only after a 2xx (storage key `authToken`, or `authToken:<backendId>` under a matrix mount)
    redirect: none — stays on /login; getToken() finds no token, so no auto-redirect on remount
  stub:
    mockStand: deploy/mock/mappings/auth-login.json answers 200 to ANY POST /api/auth/login — no 401 mapping exists
    implication: a stubbed browser test lands in tests/ui and cannot cover wrong password today — it would need a new mapping
  convention:
    - api tests usually assert the HTTP contract (status, body), browser tests the
      user-visible result — a project split of where assertions live, not a ban
      on network checks inside a browser test

# Field bounds — identical constraints on login and register requests.
# Client validates the minimum only; the API enforces min+max (credentials.go Validate).
bounds:
  username: {min: 3, max: 64}   # internal/security/credentials.go UsernameMinLength/MaxLength
  password: {min: 6, max: 128}
  client: min only — MIN_LOGIN_LENGTH=3 / MIN_PASSWORD_LENGTH=6 (frontend src/lib/auth.ts); no client max — oversized input reaches the API and its 400 message is shown verbatim
  edge: exactly-min credentials are VALID, not an error — tests/api AuthApiTests#registerAcceptsMinimumLengthCredentials · tests/e2e LoginTests#shouldLoginWithMinimumLengthCredentials / RegisterTests#shouldRegisterWithMinimumLengthCredentials

# Client-side form validation — exact i18n literals the e2e constants assert.
# resolveAuthErrorMessage (auth.ts) passes a non-empty API body.message through verbatim in any UI lang.
formValidation:
  order:                        # first failing rule wins — validateCredentials in auth.ts; values are .trim()'ed first
    - both empty → errorBothRequired
    - login empty → errorLoginRequired
    - login below min → errorLoginMinLength
    - password empty → errorPasswordRequired
    - password below min → errorPasswordMinLength
    - 'register only: password != confirm → errorPasswordMismatch'  # checked after all of the above (RegisterPage.vue)
  confirm: register only — confirmPassword is .trim()'ed like the rest (RegisterPage.vue); the mismatch check runs after validateCredentials, so an empty or whitespace confirm with a valid password → errorPasswordMismatch
  en:                           # i18n/en.ts auth + register blocks, {minLogin}=3 {minPassword}=6 — same dictionary copied into every frontend cell
    errorBothRequired: 'Login and password are required (minimum 3 and 6 characters)'
    errorLoginRequired: 'Login is required (minimum 3 characters)'
    errorLoginMinLength: 'Login must be at least 3 characters'
    errorPasswordRequired: 'Password is required (minimum 6 characters)'
    errorPasswordMinLength: 'Password must be at least 6 characters'
    errorPasswordMismatch: 'Passwords do not match'
    errorNetwork: 'Network error. Check your connection and try again.'
    errorWrongCredentials: 'Wrong login or password'      # i18n login block — login fallback; byte-identical to the 401 body message, so the text alone does not prove the layer-source (noApiCall vs apiPassthrough)
    errorRegistrationFailed: 'Registration failed'        # i18n register block — register fallback
  ru:                           # i18n/ru.ts — same keys
    errorBothRequired: 'Нужны логин и пароль (минимум 3 и 6 символов)'
    errorLoginRequired: 'Логин обязателен (минимум 3 символов)'
    errorLoginMinLength: 'Логин должен быть не короче 3 символов'
    errorPasswordRequired: 'Пароль обязателен (минимум 6 символов)'
    errorPasswordMinLength: 'Пароль должен быть не короче 6 символов'
    errorPasswordMismatch: 'Пароли не совпадают'
    errorNetwork: 'Ошибка сети. Проверьте соединение и попробуйте снова.'
    errorWrongCredentials: 'Неверный логин или пароль'
    errorRegistrationFailed: 'Регистрация не удалась'
  apiPassthrough: a non-empty API body.message is shown verbatim in any lang — 'Username already taken' (409), 'Wrong login or password' (401); empty-string message → i18n fallback — errorRegistrationFailed 'Registration failed' / 'Регистрация не удалась' (register) or errorWrongCredentials 'Wrong login or password' / 'Неверный логин или пароль' (login); a missing or non-string body.message throws 'Request failed' instead and is shown verbatim, NOT via the i18n fallback
  noApiCall: client-side failures — required, min-length, mismatch — show the error without any API request; per source, handleSubmit returns before apiRequest (LoginPage.vue / RegisterPage.vue) — a local literal differs from apiPassthrough by the absent call (the text alone proves nothing — en errorWrongCredentials is byte-identical to the 401 body); explicit assertions: e2e LoginTests/RegisterTests and component src/test/pages/* assert the local literals — the component validation cases run with no fetch stub and no fetch spy, so they assert the shown message, not the zero call; browser network: no test reads it — TestBase can attach a HAR for diagnostics (attachHarLogs, helpers/HarCapture) but no assertion consumes it — so «no /api/auth/* request left the page» is not directly asserted anywhere; how a test would observe it is a harness runtime detail, not a contract requirement
  shown: single error node — login `#error-message`, register `#register-error-message`; p element, aria-live="polite"
  lang: UI lang = localStorage `zds-lang` (en|ru), default en (i18n/index.ts) — e2e asserts the en literals; ru e2e hook — `localStorage.setItem('zds-lang','ru')` on a same-origin page before open (lang is read once at mount — useI18n/readStoredLang); live switch — `header:lang-change` document event
  ownedBy: tests/e2e — LoginTests + RegisterTests string constants · component src/test/pages/LoginPage.test.ts / RegisterPage.test.ts

# Server-side validation envelope — Credentials.Validate(); NOT an auth failure, never 401.
beanValidation:
  status: 400
  body: '{"message": "<field> <message>[; <next-field> <message>]"}'  # internal/api/handler.go credentials() — envelope schemas/error.json
  defaults:                     # security.Message* literals (contract — do not reword)
    required: '{field} is required'                    # empty creds → 'username is required; password is required'
    length: '{field} must be {min}-{max} characters'   # e.g. short password → "password must be 6-128 characters"
  join: several field errors land in one message joined by "; " in field order (security.MessageSeparator)
  malformedJson: '{"message": "Request body is not valid JSON"}'  # decode failure → 400, not 401
  ownedBy: tests/api — AuthApiTests#loginRejects* / registerRejects* (status 400 + field name in message · "; " join · malformed literal)

# Register — full flow contract (was acceptance-only before 0.5).
register:
  positive:
    api: 'POST /api/auth/register → 201, body AuthResponse {token, username, redirectUrl: "/"} — schemas/auth-response.json'
    session: saveSession(response.token) — register IS a login; `authToken` written right after the 201 (RegisterPage.vue)
    redirect: navigate(response.redirectUrl || '/') → home welcome, same as validLogin.home
    ownedBy: tests/api AuthApiTests#registerNewUser · tests/e2e RegisterTests#shouldRegisterNewUser / shouldRegisterWithMinimumLengthCredentials
  duplicate:
    api: 'existing username → 409 {"message": "Username already taken"} — handler.register (FindUserByUsername + duplicate-race path); envelope schemas/error.json'
    form: the 409 message shown verbatim in `#register-error-message`, any lang (formValidation.apiPassthrough)
    session: no `authToken` written; stays on /register
    ownedBy: tests/api AuthApiTests#registerDuplicateUsername · tests/e2e RegisterTests#shouldShowErrorWhenUsernameIsTaken
  stub:
    mockStand: deploy/mock/mappings/auth-register.json answers 201 to ANY POST /api/auth/register — no 409 mapping exists
    implication: a stubbed register-positive lands in tests/ui; duplicate cannot be covered on the mock stand without a new mapping
  convention:
    - same convention as wrongPassword — api tests assert the HTTP contract,
      browser tests the user-visible result; not a ban on network checks

# Positive login — the session is written only after a 2xx.
validLogin:
  api: 'POST /api/auth/login → 200, body AuthResponse {token, username, redirectUrl: "/"} — schemas/auth-response.json'
  session: saveSession(response.token) → localStorage `authToken` (or `authToken:<backendId>` under a matrix mount) — runs only after the 2xx (LoginPage.vue)
  home:
    redirect: navigate(response.redirectUrl || '/') — lands on /
    welcome: welcome-panel unhidden, `#welcome-message` = 'Welcome, {username}!' (en) / 'Добро пожаловать, {username}!' (ru) — the username comes from GET /api/auth/me with the fresh Bearer token, not from the login body (HomePage.vue)
    degraded: /me failure → clearSession() → welcome-panel stays hidden
  ownedBy: tests/api AuthApiTests#loginWithValidCredentials · tests/e2e LoginTests#shouldLoginWithValidCredentials ('Welcome, user1!')

# Session lifecycle — one place for when `authToken` is written, read and cleared.
session:
  transport: Bearer JWT in `Authorization` — backend STATELESS (internal/security/jwt.go), no cookie session, CSRF off; clearing cookies does not end a session
  storage: localStorage only — key from `storage.authToken`; e2e derives it from the pathname (BasePage AUTH_TOKEN_KEY_JS)
  lifetime: JWT exp — default 86400000 ms = 24h, env JWT_EXPIRATION_MS (internal/security/jwt.go)
  write: saveSession(response.token) only after a 2xx login (validLogin.session) or 201 register (register.positive.session) — async, after the response resolves; never on 4xx
  read:
    login: getToken() on mount → navigate('/', replace) — a stored token skips the form (LoginPage.vue)
    home: getToken() → GET /api/auth/me with Bearer → welcome (validLogin.home)
    reload: session survives a reload — the token stays in localStorage and home re-reads it
  clear:
    logout: auth.ts logout() awaits POST /api/auth/logout — the .catch swallows any failure — then removeItem(authToken); HomePage awaits logout() before navigating to /login, so a hung request delays both the token removal and the navigation; e2e owns the cleared `authToken` and the /login landing; the 204 itself stays an api-layer fact (session.logout.api)
    deleteAccount: auth.ts deleteAccount() awaits DELETE /api/auth/me, then removeItem(authToken) even if the call failed; HomePage navigates to /login only after window.confirm
    invalidToken: /me failure on home → clearSession(), welcome-panel stays hidden (validLogin.home.degraded)
  logout:
    api: 'POST /api/auth/logout → 204, empty body — no auth required, no server state (handler.logout)'
    tokenAfter: still valid server-side until exp — GET /api/auth/me with the same token → 200; by design, not a bug
  deleteAccount:
    api: 'DELETE /api/auth/me (Bearer) → 204; no/invalid token → 401'
    tokenAfter: still verifies cryptographically, but the row is gone → GET /api/auth/me and DELETE /api/auth/me → 401 (handler.deleteAccount)
    cancel: window.confirm false → no request, token kept; covered facts: component HomePage.test.ts 'cancelling the confirm keeps the session and sends no delete request' asserts `deleteAccountCalls()` is empty — zero DELETE /api/auth/me calls on the fetch mock; e2e DeleteAccountTests#cancellingConfirmKeepsSession covers welcome stays, `authToken` kept, the account still logs in through the API — it does not observe the real network, so the wire-level absence of the DELETE is asserted only against the component mock (same limitation as formValidation.noApiCall)
  e2eShortcut:
    method: BasePage.openPageWithLocalStorageAuthentication — API login, setItem(authToken) on a static same-origin asset, then open the page
    scope: precondition only; it does not test the login form, and its token write is synchronous (no async race)
    reset: BrowserSessionHelper.resetPageState clears local/session storage and cookies — a hook, not an assertion of "no session"
  stub:
    mockStand: deploy/mock/mappings — auth-logout.json answers 204 to ANY POST /api/auth/logout; auth-me-delete.json answers 204 to DELETE /api/auth/me when `Authorization` matches `Bearer .+` (priority 1), auth-me-delete-anonymous.json answers 401 without a Bearer (priority 10); GET /api/auth/me mirrors the pair — auth-me.json 200 Bearer, auth-me-anonymous.json 401
    implication: logout/delete happy paths are stubbed, but the mock is stateless — after a stubbed DELETE /me the same Bearer still gets 200 on GET /me, so session.deleteAccount.tokenAfter and logout.tokenAfter prove nothing on the mock stand; post-delete state stays a real-backend api fact
  ownedBy: tests/api AuthRoundTripApiTests#accountLifecycleRoundTrip · tests/e2e SessionTests#sessionSurvivesReload / invalidTokenClearsSession · LogoutTests · DeleteAccountTests · component test/lib/auth.test.ts (logout clears the token when the API call resolves or rejects; a hung request is not covered — logout awaits the call)

implementation:
  # Emitted cell — modules live in this repo.
  backend: backend/go/backend-go-stdlib/
  frontend: frontend/typescript/frontend-typescript-vue/
  tests: tests/java/tests-java-junit5-rest_assured-selenide/
  # Native cells (android/ios) — not emitted here; upstream canon:
  # projects/autotests-ai-multistack-home/autotests-ai-multistack-app/

design-system:
  embedHeader: true
  reference: frontend/typescript/frontend-typescript-vue/src/pages/LoginPage.vue
  uiSource: frontend/typescript/frontend-typescript-vue/vendor/ds/
  plaqueField: frontend/typescript/frontend-typescript-vue/vendor/ds/  # upstream: design-system-home/react-ui/PlaqueField.tsx
  templates: frontend/typescript/frontend-typescript-vue/vendor/ds/templates/
