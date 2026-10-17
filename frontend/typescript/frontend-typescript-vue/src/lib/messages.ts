import { dictionaries, type Lang } from '../i18n';
import type { AuthMessages } from './auth';

export function loginMessages(lang: Lang): AuthMessages {
  const { auth, login } = dictionaries[lang];
  return {
    ...auth,
    errorWrongCredentials: login.errorWrongCredentials,
  };
}

export function registerMessages(lang: Lang): AuthMessages {
  const { auth, register } = dictionaries[lang];
  return {
    ...auth,
    errorPasswordMismatch: register.errorPasswordMismatch,
    errorRegistrationFailed: register.errorRegistrationFailed,
  };
}

/** Default-en snapshots for auth unit tests and Selenide-facing English copy. */
export const LOGIN_MESSAGES = loginMessages('en');
export const REGISTER_MESSAGES = registerMessages('en');
export const DELETE_ACCOUNT_CONFIRM = dictionaries.en.home.deleteConfirm;
