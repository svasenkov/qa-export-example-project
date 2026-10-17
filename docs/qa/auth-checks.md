# Проверки входа, регистрации и сессии

Требования находятся в [OpenSpec](../../openspec/README.md). Здесь собраны связи
с проверками и ограничения доказательств, перенесённые из описания `login-flow` v0.9.
Это навигация по существующим тестам, **не отчёт об их выполнении**. Перед выводом
о покрытии читай актуальное тело теста и результаты конкретного запуска.
Правила учёта — в [testing.md](testing.md).

## Вход: AUTH-001–AUTH-003

API: `AuthApiTests#loginWithValidCredentials`, `loginWithInvalidPassword`,
`loginWithUnknownUsername`, `loginMinLengthUnknownUserIsUnauthorized` в
[AuthApiTests.java](../../tests/java/tests-java-junit5-rest_assured-selenide/src/test/java/tests/api/AuthApiTests.java).

E2E: `LoginTests#shouldLoginWithValidCredentials`, `shouldShowErrorWhenPasswordIsWrong`,
`shouldLoginWithMinimumLengthCredentials` в
[LoginTests.java](../../tests/java/tests-java-junit5-rest_assured-selenide/src/test/java/tests/e2e/LoginTests.java).
Component: [LoginPage.test.ts](../../frontend/typescript/frontend-typescript-vue/src/test/pages/LoginPage.test.ts).

Для неверного пароля нужна свежая сессия и существующий пользователь. Пример
учебной фикстуры — `user1 / password1`, запрос — `user1 / wrongpassword`:
длины допустимы, поэтому запрос достигает API. Источник демо-данных —
`internal/store/seed.go` backend-модуля; это не реальные учётные данные.

API-проверка HTTP 401 и E2E-проверка показанного текста доказывают разные утверждения.
Идентификатор прежнего UI-сценария `login-wrong-password-e2e` соответствует
AUTH-003a и относится к форме, а не отдельно к HTTP 401.
Разделение по уровням не запрещает сетевые assertions в браузерных тестах.

## Регистрация: REG-001–REG-003

API: `AuthApiTests#registerNewUser`, `registerAcceptsMinimumLengthCredentials`,
`registerDuplicateUsername`. E2E: `RegisterTests#shouldRegisterNewUser`,
`shouldRegisterWithMinimumLengthCredentials`, `shouldShowErrorWhenUsernameIsTaken` в
[RegisterTests.java](../../tests/java/tests-java-junit5-rest_assured-selenide/src/test/java/tests/e2e/RegisterTests.java).
Component: [RegisterPage.test.ts](../../frontend/typescript/frontend-typescript-vue/src/test/pages/RegisterPage.test.ts).

Проверка успешной регистрации включает создание сессии. Отказ HTTP 409 и его
показ в форме проверяются отдельно по смыслу, как и ошибка входа.

## Общие формы: FORM-001–FORM-007

API-проверки `AuthApiTests#loginRejects*` и `registerRejects*` проверяют HTTP 400,
имя поля, разделитель `; ` и сообщение невалидного JSON. Ответы проверяются
по `schemas/error.json`, успешные ответы — по `schemas/auth-response.json`
из ресурсов Java-модуля. Эти схемы не являются автоматически сгенерированной
копией OpenAPI.

E2E `LoginTests` и `RegisterTests` используют точные английские сообщения.
Component-проверки валидации утверждают показанный текст. В исходном описании
отмечен пробел: они не наблюдают отсутствие fetch для всех ошибок валидации,
а браузерные тесты не утверждают отсутствие запроса `/api/auth/*`.
При переносе это требование **не считается доказанным**. Проверять сообщение
недостаточно: английский `errorWrongCredentials` совпадает с текстом HTTP 401.

`TestBase` может прикладывать HAR через `attachHarLogs` / `HarCapture` для
диагностики; наличие HAR не является assertion. Для русского E2E язык нужно
установить на странице того же origin через localStorage `zds-lang=ru` до
открытия формы: язык читается при монтировании.

Серверные ограничения и сообщения находятся в `internal/security/credentials.go`,
декодирование запроса — в `internal/api/handler.go`. На клиенте минимум проверяет
`src/lib/auth.ts`, страницы прекращают отправку при ошибке. Эти ссылки на код
помогают диагностике, но не заменяют наблюдения теста.

## Сессия и удаление: ACC-001–ACC-006

API: [AuthRoundTripApiTests.java](../../tests/java/tests-java-junit5-rest_assured-selenide/src/test/java/tests/api/AuthRoundTripApiTests.java),
метод `accountLifecycleRoundTrip`. E2E: `SessionTests#sessionSurvivesReload`,
`invalidTokenClearsSession`, а также `LogoutTests` и `DeleteAccountTests` в
[каталоге E2E](../../tests/java/tests-java-junit5-rest_assured-selenide/src/test/java/tests/e2e).

Component: [HomePage.test.ts](../../frontend/typescript/frontend-typescript-vue/src/test/pages/HomePage.test.ts).
Проверка `cancelling the confirm keeps the session and sends no delete request`
наблюдает отсутствие DELETE на fetch mock. E2E
`DeleteAccountTests#cancellingConfirmKeepsSession` проверяет приветствие,
сохранённый токен и возможность войти через API, но не отсутствие DELETE
в реальной сети. Эти утверждения нельзя взаимозаменять.

[auth.test.ts](../../frontend/typescript/frontend-typescript-vue/src/test/lib/auth.test.ts)
проверяет очистку токена при завершении или отклонении запроса выхода.
Зависший запрос в перенесённом перечне проверок не покрыт; реализация ждёт ответ.
HTTP 204 выхода сам по себе не доказывает очистку localStorage или переход страницы.

Ключ токена выбирается через `authTokenStorageKey` в `appBase.ts` frontend-модуля;
Java `BasePage` вычисляет его по pathname через `AUTH_TOKEN_KEY_JS`.
Backend использует stateless JWT (`internal/security/jwt.go`), не cookie-сессию;
в прежнем описании CSRF отмечен как отключённый.

## Подготовка сессии в E2E

`BasePage.openPageWithLocalStorageAuthentication`: API-вход, синхронный `setItem`
на статическом ресурсе того же origin, затем открытие нужной страницы.
Это предусловие, оно не проверяет форму входа и её асинхронную запись токена.
`BrowserSessionHelper.resetPageState` очищает local/session storage и cookies;
работа hook не является проверкой отсутствия сессии.

## Ограничения mock-стенда

Конфигурация — [deploy/mock/mappings](../../deploy/mock/mappings).
Перенесённые ограничения требуют повторной проверки при изменении mappings:

- `auth-login.json` отвечает HTTP 200 на любой POST входа; mapping HTTP 401 отсутствует.
  Отрицательный сценарий AUTH-003 этим стендом не подтверждается.
- `auth-register.json` отвечает HTTP 201 на любой POST регистрации; mapping HTTP 409
  отсутствует. Занятое имя REG-003 этим стендом не подтверждается.
- `auth-logout.json` отвечает HTTP 204 на любой POST выхода.
- `auth-me-delete.json` отвечает HTTP 204 при `Authorization: Bearer .+` (priority 1),
  `auth-me-delete-anonymous.json` — HTTP 401 без Bearer (priority 10).
- GET профиля имеет аналогичную пару: `auth-me.json` и `auth-me-anonymous.json`.
  Mock не хранит состояние: после DELETE тот же Bearer по-прежнему получает HTTP 200.
  Последствия удаления и серверная действительность JWT после выхода проверяются
  на реальном backend, не на этом mock.

Проверки браузера с подменённым API относятся здесь к UI. Команды, стенды и
доступность модулей определяют их README и [профиль](../coverage-profile.md).

