# UI-соглашения аутентификации

Поведение задают [спецификации](../../../../openspec/README.md), включая
[FORM-007](../../../../openspec/specs/auth-forms/spec.md). Этот документ хранит
точные атрибуты элементов и сведения о подключении UI в выбранном модуле.

## Атрибуты

Значения `testid` означают `data-testid`. Они используются Page Object и component-тестами.
Значения `name` полей соответствуют назначению autocomplete.

```yaml
login: {id: login-input, name: username, autocomplete: username, testid: login-input}
password: {id: password-input, name: password, autocomplete: current-password, testid: password-input}
registerLogin: {id: register-login-input, name: username, autocomplete: username, testid: register-login-input}
registerPassword: {id: register-password-input, name: password, autocomplete: new-password, testid: register-password-input}
confirmPassword: {id: confirm-password-input, name: confirm-password, autocomplete: new-password, testid: confirm-password-input}
registerSubmit: {id: register-submit-button, testid: register-submit-button}
registerError: {id: register-error-message, testid: register-error-message}
headerSearch: {id: header-search-input, name: header-search, testid: header-search-input}
headerMenuSearch: {id: header-menu-search-input, name: header-menu-search, testid: header-menu-search-input}
```

## Дизайн-система и реализация

Общая дизайн-система встроена из [vendor/ds](../vendor/ds) относительно модуля
(из корня репозитория: `frontend/typescript/frontend-typescript-vue/vendor/ds/`).
Шаблоны находятся в `vendor/ds/templates/`, header подключён; пример формы —
`src/pages/LoginPage.vue`. Vue-обёртки используют общую дизайн-систему.

Клиентская валидация, обработка ошибок и хранение сессии реализованы в `src/lib/auth.ts`
и страницах `src/pages/`. Язык читается при монтировании; для смены без перезагрузки
используется событие документа `header:lang-change`. Детали реализации не являются
доказательством выполнения тестов.
