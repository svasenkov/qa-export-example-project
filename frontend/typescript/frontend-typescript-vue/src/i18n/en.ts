import type { Dictionary } from './types';

/** Copied from frontend-typescript-react/src/i18n/en.ts — not a shared lib. */
export const en: Dictionary = {
  nav: {
    home: 'Home',
    login: 'Login',
    register: 'Register',
    stack: 'Stack',
  },
  home: {
    title: 'Multistack',
    blurb: 'TypeScript Vue SPA — items loaded from {api}.',
    session: 'Session',
    welcome: 'Welcome, {username}!',
    logout: 'Logout',
    deleteAccount: 'Delete account',
    deleteConfirm: 'Delete this account? This cannot be undone.',
    health: 'Health',
    healthChecking: '→ Checking health…',
    healthOk: '→ {status} | service: {service} | frontend: {frontend}',
    healthError: '✗ health: {message}',
    items: 'Items',
    itemsLoading: '→ Loading items…',
    itemsEmpty: 'No items found.',
    itemsError: '✗ items: {message}',
  },
  login: {
    title: 'Login Form',
    loginLabel: 'Login',
    passwordLabel: 'Password',
    submit: 'Login',
    noAccount: 'No account?',
    registerLink: 'Register',
    errorWrongCredentials: 'Wrong login or password',
  },
  register: {
    title: 'Register',
    loginLabel: 'Login',
    passwordLabel: 'Password',
    confirmLabel: 'Confirm',
    submit: 'Register',
    haveAccount: 'Already have an account?',
    loginLink: 'Login',
    errorPasswordMismatch: 'Passwords do not match',
    errorRegistrationFailed: 'Registration failed',
  },
  auth: {
    errorBothRequired:
      'Login and password are required (minimum {minLogin} and {minPassword} characters)',
    errorLoginRequired: 'Login is required (minimum {minLogin} characters)',
    errorLoginMinLength: 'Login must be at least {minLogin} characters',
    errorPasswordRequired: 'Password is required (minimum {minPassword} characters)',
    errorPasswordMinLength: 'Password must be at least {minPassword} characters',
    errorNetwork: 'Network error. Check your connection and try again.',
  },
};
