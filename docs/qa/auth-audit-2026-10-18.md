# Аудит OpenSpec и тестовых слоёв — 18 октября 2026

## Вывод и границы

Проверены три модуля сборки: Go backend, Vue frontend и Java API/UI/E2E.
Основание — 19 требований и **46 именованных сценариев** из четырёх действующих
`openspec/specs/*/spec.md`, нормативные сообщения, UI-атрибуты и HTTP-контракт.
Требования и продуктовый код в этой работе не изменялись.

После дополнения тестов **46/46 сценариев имеют содержательные автоматические
проверки**. Это покрытие списка, а не процент строк/ветвей и не гарантия отсутствия
дефектов. **Соответствие подтверждено для 45/46 сценариев; AUTH-003a нарушен**
на классе длинных паролей. Падающие проверки оставлены включёнными. До аудита
оснований заявлять 100% не было: отсутствовали проверки обязательных наблюдений,
а один unit-тест закреплял поведение, противоречащее спецификации.

Нельзя переносить эти 100% на весь продукт: OpenSpec сейчас описывает auth/account,
но не полный набор требований к header, layout, PWA, каталогу, accessibility,
нагрузке или безопасности. Load в профиле недоступен. Ручные проверки не выполнялись
человеком. Скриншотное сравнение, Docker-упаковка и GitHub CI в этом запуске не проверялись.

## Найденный дефект продукта

**AUTH-003a, высокий приоритет: неверный длинный пароль допускает вход.**
`internal/security/password.go` обрезает вход до 72 байт как при сохранении, так и
при проверке. FORM-003 разрешает пароль до 128 символов. Если верные первые 72 байта
сохранены, изменение или удаление хвоста не мешает входу. Локальный API на реальной
PostgreSQL вернул HTTP 200 вместо 401; тестовый аккаунт удалён после воспроизведения.

Старый `TestCheckPasswordIgnoresBytesPastTheLimit` требовал принимать другой хвост.
Он заменён на `TestCheckPasswordRejectsDifferentSuffix`. Дополнительно проблему
обнаруживают `TestLoginRejectsWrongPasswordSuffix` и
`AuthApiTests#loginRejectsDifferentPasswordSuffix` (изменение последнего символа
пароля длиной 128). Неожиданное тело HTTP 200 не выводится: оно содержит JWT.

Исправление затрагивает формат хранения и совместимость существующих паролей.
Оно не подменено ослаблением AUTH-003, ограничением максимума до 72 или отключением
регрессий. Решение о миграции/пересоздании учебных аккаунтов необходимо до изменения
продукта; запрос пользователя на эту работу был об аудите и тестовом покрытии.

## Тест-дизайн и изменения

- Unit Go проверяют правила и обработчики с fake store. Они не выдаются за проверку
  живого HTTP или БД. Добавлена проверка повторного DELETE прежним JWT.
- Integration проверяют PostgreSQL-хранилище. Новый `TestPostgresConcurrentUsername`
  запускает два конкурирующих INSERT: ровно один успех и один duplicate; победитель
  читается из БД. Преобразование duplicate в HTTP 409 отдельно проверяет
  `TestRegisterLostUniqueRace`. Эти тесты дают разные доказательства.
- Component — основной уровень вариантов форм и состояний сессии. `AuthForms.test.ts`
  задаёт общую матрицу двух форм, точные независимые en/ru ожидания, границы,
  приоритет ошибок, отсутствие fetch/токена/навигации при отказе, trimming,
  серверный максимум, fallback ошибок, pending-ответы и атрибуты полей.
  Композиция с настоящей HomePage различает имя из auth-ответа и имя профиля.
  `session-mount.test.ts` проверяет отдельные storage/API namespaces и Bearer.
- API сохраняет внешний HTTP-контракт и настоящий жизненный цикл. Проверки
  `containsString` заменены на точные сообщения; добавлены schema assertions там,
  где они отсутствовали, пустое тело 204 и повторный DELETE. Cleanup lifecycle
  теперь выполняется в finally.
- UI — браузер с WireMock: видимость, шапка/навигация, layout, подмена ошибок API.
  Это отдельная цель от component DOM и от серверной аутентификации.
- E2E сокращены с 25 до 12 нескриншотных сценариев: 11 чисто клиентских валидаций
  перенесены в более сильные component-проверки; удалены повтор неизвестного имени
  (контракт уже проверяет API) и второй logout с другим способом подготовки.
  Сохранены обычные и минимальные login/register, реальные 401/409, reload,
  недействительная сессия, logout, отмена/подтверждение удаления и загрузка home.
  Удаление через UI теперь проверяет также отказ профиля прежнему JWT, а logout —
  очистку storage. Отмена в E2E подтверждает сохранение аккаунта; отсутствие DELETE
  непосредственно наблюдается в component, а не выводится из названия E2E.
- Восемь прежних одиночных component-проверок заменены общей матрицей вместо
  сохранения двух копий. Unit общих helper-функций оставлены: они локализуют ошибку
  алгоритма, тогда как component доказывает подключение формы и побочные эффекты.
- Описание `BackendWiringApiTests` больше не утверждает, что строка `source` сама
  доказывает реальную PostgreSQL. Доказательство БД дают отдельные integration.

Границы слоёв теперь существенно чище. Оставшееся пересечение осмысленное:
серверный ответ, отображение ответа и реальная доставка через все части системы
проверяют разные виды отказа. Процент тестов на каждом слое не используется как
самостоятельный критерий качества.

## Карта всех 46 сценариев

Пути сокращены только здесь: Go — `backend/go/backend-go-stdlib/internal/`,
Vue — `frontend/typescript/frontend-typescript-vue/src/test/`, Java —
`tests/java/tests-java-junit5-rest_assured-selenide/src/test/java/tests/`.
Имена в кавычках — названия тестов или параметризованных групп. Все строки ниже,
кроме явно отмеченной AUTH-003a, подтверждены выполненными проверками текущей работы.

### Authentication — 6

- AUTH-001a → Vue `pages/LoginPage.test.ts`, `mounts the login form…`; UI `LoginFormTests#loginFormIsMounted`.
- AUTH-001b → Vue `LoginPage.test.ts`, `redirects an already signed-in visitor…`: route `/` и вызов replace; пригодность отдельно проверяется HomePage.
- AUTH-002a → Vue `AuthForms.test.ts`, `saves only after response…` и `greets from profile…` (login); Java `api/AuthApiTests#loginWithValidCredentials`, `e2e/LoginTests#shouldLoginWithValidCredentials`.
- AUTH-002b → Vue `AuthForms.test.ts`, успешная отправка `abc`/6 символов; `e2e/LoginTests#shouldLoginWithMinimumLengthCredentials`.
- **AUTH-003a — FAIL** → Go `security/TestCheckPasswordRejectsDifferentSuffix`, `api/TestLoginRejectsWrongPasswordSuffix`; Java `api/AuthApiTests#loginRejectsDifferentPasswordSuffix`. Обычный неверный пароль и клиентский отказ проходят (`loginWithInvalidPassword`, `shouldShowErrorWhenPasswordIsWrong`, component `API message: verbatim`), но не компенсируют обнаруженный дефект.
- AUTH-003b → Go `api/TestLoginRejectsBadCredentials/unknown_user` и `wrong_password` проверяют одинаковый единственный message; Java `api/AuthApiTests#loginWithUnknownUsername`.

### Registration — 6

- REG-001a → Vue `pages/RegisterPage.test.ts`, `mounts the register form…`; UI `RegisterFormTests#registerFormIsMounted`.
- REG-001b → Vue `AuthForms.test.ts`, `REG-001 rejects confirmation "different" without request`, en/ru.
- REG-001c → та же группа, пустое и пробельное подтверждение; fetch отсутствует.
- REG-002a → Vue `AuthForms.test.ts`, `saves only after response…` и `greets from profile…` (register); Java `api/AuthApiTests#registerNewUser`, `e2e/RegisterTests#shouldRegisterNewUser`.
- REG-002b → Vue успешная отправка точных минимумов; Java `api/AuthApiTests#registerAcceptsMinimumLengthCredentials`, `e2e/RegisterTests#shouldRegisterWithMinimumLengthCredentials`.
- REG-003a → Go `api/TestRegisterDuplicateUsername`, `TestRegisterLostUniqueRace`, integration `store/TestPostgresConcurrentUsername`; Vue register `API message: verbatim`; Java `api/AuthApiTests#registerDuplicateUsername`, `e2e/RegisterTests#shouldShowErrorWhenUsernameIsTaken`.

### Account — 16

- ACC-001a → Vue `AuthForms.test.ts`, pending success обеих форм; `lib/session-mount.test.ts`, `stores and transports the token under…` для root и matrix mount.
- ACC-001b → Vue `AuthForms.test.ts`, `API message…`: storage остаётся пустым, route не меняется.
- ACC-002a → Java `e2e/SessionTests#sessionSurvivesReload`.
- ACC-002b → Go `api/TestMeRejectsBadTokens` (включая expired), `security/TestTokenRejections`; Vue `HomePage.test.ts`, profile failure 401; Java `e2e/SessionTests#invalidTokenClearsSession`.
- ACC-002c → Vue `HomePage.test.ts`, `ACC-002 clears session on profile failure` для 500 и offline.
- ACC-003a → Vue `HomePage.test.ts`, `ACC-003 waits for profile and uses its name` en/ru и `AuthForms.test.ts`, `greets from profile, never from the auth response`.
- ACC-004a → Vue `HomePage.test.ts`, logout `success`; Java `e2e/LogoutTests#shouldLogoutAfterFormLogin`.
- ACC-004b → Vue `HomePage.test.ts`, logout `http-error` и `offline`: очистка и переход.
- ACC-004c → та же группа проверяет сохранённый токен, route и приветствие до завершения управляемого Promise; нет sleep.
- ACC-004d → Java `api/AuthRoundTripApiTests#accountLifecycleRoundTrip`: GET с тем же JWT после logout успешен.
- ACC-004e → Go `api/TestLogout`, Java `api/AuthApiTests#logoutReturnsNoContent`: без токена, 204 и пустое тело.
- ACC-005a → Vue `HomePage.test.ts`, `cancelling the confirm keeps the session and sends no delete request`; Java `e2e/DeleteAccountTests#cancellingConfirmKeepsSession`.
- ACC-005b → Vue HomePage success удаления; Java `e2e/DeleteAccountTests#confirmingDeleteClearsSessionAndNavigatesToLogin`, включая серверное последствие.
- ACC-005c → Vue HomePage delete-account `http-error` и `offline`: storage очищается и route становится `/login`.
- ACC-006a → Go `api/TestDeleteAccount`; Java `api/AuthRoundTripApiTests#accountLifecycleRoundTrip`: повторные GET и DELETE дают 401.
- ACC-006b → Go `api/TestDeleteAccountWithoutToken`; Java `api/AuthApiTests#deleteWithoutToken`, `deleteWithGarbageToken`.

Дополнительные обязательства ACC-002: `config/TestLoadDefaults` проверяет 24 часа,
`TestLoadFromEnvironment` — JWT_EXPIRATION_MS, `security/TestTokenClaims` — exp/iat.

### Auth forms — 18

- FORM-001a → Vue `AuthForms.test.ts`, `blocks request: both empty` и `both whitespace`, две формы × en/ru.
- FORM-001b → там же `empty login wins over short password`.
- FORM-001c → там же `short login wins over empty password` и `short login wins over short password`; логин на один символ ниже минимума.
- FORM-001d → там же `password empty`.
- FORM-001e → там же `password one below minimum` (5 символов).
- FORM-001f → там же `saves only after response…`: 3/6, trimming всех полей, совпадающее подтверждение.
- FORM-002a → там же `sends excessive username/password to the server`: точный POST body и дословный HTTP 400 для 65/129; Go `TestCredentialValidationIsRejectedWith400` проверяет серверные статусы и тексты.
- FORM-003a → Go `api/TestCredentialValidationIsRejectedWith400/both_fields_blank` для обоих endpoints; Java `AuthApiTests#loginRejectsEmptyCredentials`, `registerRejectsEmptyCredentials`, точный текст и единственное поле message.
- FORM-003b → Go `security/TestCredentialsValidateBoundaries`, `api/TestCredentialValidationIsRejectedWith400`: нижняя/верхняя границы, порядок двух ошибок; API `loginRejects*`/`registerRejects*` проверяют опубликованный HTTP-контракт.
- FORM-003c → Go `security/TestCredentialsValidateBoundaries` (3/6 и 64/128), `api/TestMaximumLengthPasswordRoundTrips`; API min-length login/register.
- FORM-004a → Go `api/TestBodyThatIsNotAJSONObjectIsRejectedWith400`; Java `api/AuthApiTests#loginRejectsMalformedJson`, `registerRejectsMalformedJson`.
- FORM-005a → Vue `AuthForms.test.ts`, `localizes network failure without creating a session`, две формы × en/ru.
- FORM-005b → там же `API message: verbatim`; API-сообщение не переводится.
- FORM-005c → там же `API message: empty`; разные localized fallback для login/register.
- FORM-005d → там же `API message: missing/null/number/object`; ожидается `Request failed`.
- FORM-006a → там же en без zds-lang: точный текст, единственный p, id и aria-live=polite.
- FORM-006b → там же ru через localStorage до mount: все ошибки валидации и подтверждения.
- FORM-007a → там же `preserves field identity and autocomplete`: все пять полей двух форм, id/name/autocomplete.

## Выполненные проверки

Локальные артефакты: `/tmp/openspec-audit-20261018/`. Это отдельный каталог попытки;
старые Java reports/test-results/allure-results сохранены в `java-before/`.
Результаты последовательных запусков скопированы в отдельные каталоги.
Исходный commit записан в `revision.txt`; изменения остаются незакоммиченными,
хеши финальных исходников — `source-sha256.json`.

- Go unit: `env -u TEST_DATABASE_URL GOCACHE=/tmp/openspec-audit-20261018/go-cache go test -count=1 -json ./...` из backend. `go-regression.jsonl`: **57 pass, 2 fail, 4 skip**, счёт по верхнеуровневым Test-функциям. Оба fail — длинный пароль; skip — отдельные PostgreSQL-тесты.
- PostgreSQL integration: `TEST_DATABASE_URL='postgres://qa_audit@127.0.0.1:55438/scratch?sslmode=disable' GOCACHE=/tmp/openspec-audit-20261018/go-cache go test -count=1 -json ./internal/store -run '^TestPostgres'`. `go-integration.jsonl`: **4 pass, 0 skip**. Использована установленная PostgreSQL 14 в новом временном кластере; Compose закрепляет PostgreSQL 16, поэтому прогон не подтверждает именно её версию.
- Frontend: `npm test`, `npm run typecheck` из frontend. `vue-final.log`, `vue-typecheck-final.log`: **166 pass**, typecheck exit 0.
- Java API: `./gradlew test --rerun -Denv=ci -DincludeTags=api -DapiBaseUrl=http://127.0.0.1:18838/ -DenableAllureRestAssuredListener=false`. `java-api-final.log`, `java-api-final/test-results/`: **33 pass, 1 fail**, длинный пароль.
- Java E2E: `./gradlew test --rerun -Denv=ci -DincludeTags=e2e -DexcludeTags=screenshot -DbaseUrl=http://127.0.0.1:19838 -DapiBaseUrl=http://127.0.0.1:18838/ -DenableAllureRestAssuredListener=false`. `java-e2e-http1.log`, `java-e2e/test-results/`: **12 pass**, без пропусков.
- Java UI: `./gradlew test --rerun -Denv=mock -DincludeTags=ui -DexcludeTags=screenshot -DbaseUrl=http://127.0.0.1:19840 -DapiBaseUrl=http://127.0.0.1:19840/ -DenableAllureRestAssuredListener=false`. `java-ui.log`, `java-ui/test-results/`: **20 pass**, без пропусков; WireMock 3.9.1 с неизменёнными mappings проекта.
- OpenSpec: `npm run validate --prefix tools/openspec`: **4/4 pass**. Это структурная проверка, не доказательство поведения.
- Mutation-проба в изолированной копии: удаление return после login-валидации и await из logout вызвали **17 assertion failures** (14 validation, 3 logout); исходники продукта не изменялись. `mutation-retry.log`. Первую попытку остановил доступ Vite к symlink node_modules до запуска тестов (`mutation.log`); она не считается обнаружением мутаций.

Chrome закреплённый: 148.0.7778.178, headless macOS. Живой backend использует отдельную
БД `app_audit`, integration — `scratch`; внешние стенды не вызывались. Vite-прокси
описаны во временных `frontend.mjs` / `frontend-mock.mjs`. Docker не запущен;
Compose/gateway nginx не подменяются утверждением об успешном запуске контейнеров.

Первый Go-запуск ограничила запись в системный build cache; последующие используют
отдельный временный cache. PostgreSQL initdb потребовал разрешённого запуска вне
песочницы из-за shared memory. Две попытки E2E остановились на readiness: обычный
GET Vite отдавал 200, но HTTP/2 upgrade зависал (воспроизведено curl). В Gradle
readiness переключён на HTTP/1.1; таймаут и проверка статуса сохранены. После этого
UI/E2E выполнились. Неуспешные попытки сохранены отдельно.

## Что остаётся за пределами закрытого списка

OpenAPI не описывает 400 и error-envelope auth полностью; это уже отмечено в
`openspec/README.md`. Ещё одно расхождение: `ItemsResponse.minItems: 1`, тогда как
`TestItemsEmptyIsAnArrayNotNull` и frontend поддерживают пустой список. Для каталога
нет capability OpenSpec, поэтому нельзя самовольно объявить верным одну из сторон.
Нужен отдельный согласованный контракт каталога, а не тест, выдумывающий требование.

Manual-чеклисты про XSS, вторую вкладку, offline и layout не являются результатами
человека. JaCoCo Java измеряет только allowlist тестовых утилит, не Go/Vue продукт.
Полного измерения строк/ветвей продукта этот аудит не выполнял.
