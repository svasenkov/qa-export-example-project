import { apiUrl, authTokenStorageKey } from './appBase';

/** Backend-scoped localStorage key — see `authTokenStorageKey`. */
export const AUTH_TOKEN_KEY = authTokenStorageKey();
export const MIN_LOGIN_LENGTH = 3;
export const MIN_PASSWORD_LENGTH = 6;

export interface AuthMessages {
  errorBothRequired: string;
  errorLoginRequired: string;
  errorLoginMinLength: string;
  errorPasswordRequired: string;
  errorPasswordMinLength: string;
  errorNetwork: string;
  errorPasswordMismatch?: string;
  errorWrongCredentials?: string;
  errorRegistrationFailed?: string;
}

export interface AuthResponse {
  token: string;
  username: string;
  redirectUrl?: string;
}

export interface UserProfile {
  username: string;
}

interface NetworkError extends Error {
  network?: boolean;
}

function readLocalStorage(name: string): string | null {
  try {
    return localStorage.getItem(name);
  } catch {
    return null;
  }
}

function writeLocalStorage(name: string, value: string): void {
  try {
    localStorage.setItem(name, value);
  } catch {
    /* ignore quota / disabled storage */
  }
}

function removeFromLocalStorage(name: string): void {
  try {
    localStorage.removeItem(name);
  } catch {
    /* ignore */
  }
}

export function formatMessage(
  template: string,
  values: Record<string, string | number>,
): string {
  return template.replace(/\{(\w+)\}/g, (_, key: string) =>
    values[key] !== undefined ? String(values[key]) : '',
  );
}

export function validateCredentials(
  login: string,
  password: string,
  messages: AuthMessages,
): string | null {
  if (!login && !password) {
    return formatMessage(messages.errorBothRequired, {
      minLogin: MIN_LOGIN_LENGTH,
      minPassword: MIN_PASSWORD_LENGTH,
    });
  }
  if (!login) {
    return formatMessage(messages.errorLoginRequired, { minLogin: MIN_LOGIN_LENGTH });
  }
  if (login.length < MIN_LOGIN_LENGTH) {
    return formatMessage(messages.errorLoginMinLength, { minLogin: MIN_LOGIN_LENGTH });
  }
  if (!password) {
    return formatMessage(messages.errorPasswordRequired, { minPassword: MIN_PASSWORD_LENGTH });
  }
  if (password.length < MIN_PASSWORD_LENGTH) {
    return formatMessage(messages.errorPasswordMinLength, { minPassword: MIN_PASSWORD_LENGTH });
  }
  return null;
}

function createNetworkError(): NetworkError {
  const error: NetworkError = new Error('');
  error.network = true;
  return error;
}

export function resolveAuthErrorMessage(
  error: unknown,
  messages: AuthMessages,
  fallbackMessage: string,
): string {
  const err = error as NetworkError | undefined;
  if (err?.network) {
    return messages.errorNetwork;
  }
  if (err?.message) {
    return err.message;
  }
  return fallbackMessage;
}

async function apiRequest<T>(path: string, options?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(apiUrl(path), {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...(options?.headers ?? {}),
      },
    });
  } catch {
    throw createNetworkError();
  }

  const body = (await response.json().catch(() => ({}))) as Record<string, unknown>;
  if (!response.ok) {
    const message = typeof body.message === 'string' ? body.message : 'Request failed';
    throw new Error(message);
  }
  return body as T;
}

export function login(username: string, password: string): Promise<AuthResponse> {
  return apiRequest<AuthResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
}

export function register(username: string, password: string): Promise<AuthResponse> {
  return apiRequest<AuthResponse>('/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
}

export function fetchProfile(): Promise<UserProfile> {
  const token = readLocalStorage(AUTH_TOKEN_KEY);
  if (!token) {
    throw new Error('Missing auth token');
  }
  return apiRequest<UserProfile>('/auth/me', {
    method: 'GET',
    headers: { Authorization: 'Bearer ' + token },
  });
}

export async function logout(): Promise<void> {
  const token = readLocalStorage(AUTH_TOKEN_KEY);
  if (token) {
    await fetch(apiUrl('/auth/logout'), {
      method: 'POST',
      headers: { Authorization: 'Bearer ' + token },
    }).catch(() => {});
  }
  removeFromLocalStorage(AUTH_TOKEN_KEY);
}

// Account deletion, not logout: the row is gone server-side and the token stops
// verifying. Local cleanup follows logout's policy — the session is dropped even
// when the call fails, so a dead token can never keep the UI signed in.
export async function deleteAccount(): Promise<void> {
  const token = readLocalStorage(AUTH_TOKEN_KEY);
  if (token) {
    await fetch(apiUrl('/auth/me'), {
      method: 'DELETE',
      headers: { Authorization: 'Bearer ' + token },
    }).catch(() => {});
  }
  removeFromLocalStorage(AUTH_TOKEN_KEY);
}

export function saveSession(token: string): void {
  writeLocalStorage(AUTH_TOKEN_KEY, token);
}

export function getToken(): string | null {
  return readLocalStorage(AUTH_TOKEN_KEY);
}

export function clearSession(): void {
  removeFromLocalStorage(AUTH_TOKEN_KEY);
}
