# Точные сообщения форм

Нормативное дополнение к [правилам форм](spec.md). Значения уже содержат
минимумы 3 и 6; ключи соответствуют существующим словарям en/ru.
Сообщения API показываются дословно согласно FORM-005.

```yaml
en:
  errorBothRequired: 'Login and password are required (minimum 3 and 6 characters)'
  errorLoginRequired: 'Login is required (minimum 3 characters)'
  errorLoginMinLength: 'Login must be at least 3 characters'
  errorPasswordRequired: 'Password is required (minimum 6 characters)'
  errorPasswordMinLength: 'Password must be at least 6 characters'
  errorPasswordMismatch: 'Passwords do not match'
  errorNetwork: 'Network error. Check your connection and try again.'
  errorWrongCredentials: 'Wrong login or password'
  errorRegistrationFailed: 'Registration failed'
ru:
  errorBothRequired: 'Нужны логин и пароль (минимум 3 и 6 символов)'
  errorLoginRequired: 'Логин обязателен (минимум 3 символов)'
  errorLoginMinLength: 'Логин должен быть не короче 3 символов'
  errorPasswordRequired: 'Пароль обязателен (минимум 6 символов)'
  errorPasswordMinLength: 'Пароль должен быть не короче 6 символов'
  errorPasswordMismatch: 'Пароли не совпадают'
  errorNetwork: 'Ошибка сети. Проверьте соединение и попробуйте снова.'
  errorWrongCredentials: 'Неверный логин или пароль'
  errorRegistrationFailed: 'Регистрация не удалась'
```
