export type Lang = 'en' | 'ru';

export interface AuthCopy {
  errorBothRequired: string;
  errorLoginRequired: string;
  errorLoginMinLength: string;
  errorPasswordRequired: string;
  errorPasswordMinLength: string;
  errorNetwork: string;
}

export interface Dictionary {
  nav: {
    home: string;
    login: string;
    register: string;
    stack: string;
  };
  home: {
    title: string;
    blurb: string;
    session: string;
    welcome: string;
    logout: string;
    deleteAccount: string;
    deleteConfirm: string;
    health: string;
    healthChecking: string;
    healthOk: string;
    healthError: string;
    items: string;
    itemsLoading: string;
    itemsEmpty: string;
    itemsError: string;
  };
  login: {
    title: string;
    loginLabel: string;
    passwordLabel: string;
    submit: string;
    noAccount: string;
    registerLink: string;
    errorWrongCredentials: string;
  };
  register: {
    title: string;
    loginLabel: string;
    passwordLabel: string;
    confirmLabel: string;
    submit: string;
    haveAccount: string;
    loginLink: string;
    errorPasswordMismatch: string;
    errorRegistrationFailed: string;
  };
  auth: AuthCopy;
}
