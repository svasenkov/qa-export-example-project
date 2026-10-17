import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  AUTH_TOKEN_KEY,
  deleteAccount,
  fetchProfile,
  formatMessage,
  getToken,
  logout,
  resolveAuthErrorMessage,
  saveSession,
  validateCredentials,
} from '../../lib/auth';
import { LOGIN_MESSAGES } from '../../lib/messages';

describe('validateCredentials', () => {
  it('requires both when empty', () => {
    expect(validateCredentials('', '', LOGIN_MESSAGES)).toBe(
      'Login and password are required (minimum 3 and 6 characters)',
    );
  });

  it('requires login', () => {
    expect(validateCredentials('', 'password1', LOGIN_MESSAGES)).toBe(
      'Login is required (minimum 3 characters)',
    );
  });

  it('enforces login minimum length', () => {
    expect(validateCredentials('ab', 'password1', LOGIN_MESSAGES)).toBe(
      'Login must be at least 3 characters',
    );
  });

  it('requires password', () => {
    expect(validateCredentials('user1', '', LOGIN_MESSAGES)).toBe(
      'Password is required (minimum 6 characters)',
    );
  });

  it('enforces password minimum length', () => {
    expect(validateCredentials('user1', '123', LOGIN_MESSAGES)).toBe(
      'Password must be at least 6 characters',
    );
  });

  it('passes for valid credentials', () => {
    expect(validateCredentials('user1', 'password1', LOGIN_MESSAGES)).toBeNull();
  });
});

describe('formatMessage', () => {
  it('substitutes placeholders and blanks unknown ones', () => {
    expect(formatMessage('min {minLogin} / {minPassword}', { minLogin: 3, minPassword: 6 })).toBe(
      'min 3 / 6',
    );
    expect(formatMessage('{missing} tail', {})).toBe(' tail');
  });
});

describe('resolveAuthErrorMessage', () => {
  it('prefers the network message for a transport failure', () => {
    const error = Object.assign(new Error(''), { network: true });

    expect(resolveAuthErrorMessage(error, LOGIN_MESSAGES, 'fallback')).toBe(
      'Network error. Check your connection and try again.',
    );
  });

  it('passes the API message through', () => {
    expect(resolveAuthErrorMessage(new Error('Wrong login or password'), LOGIN_MESSAGES, 'x')).toBe(
      'Wrong login or password',
    );
  });

  it('falls back when the error carries nothing usable', () => {
    expect(resolveAuthErrorMessage(undefined, LOGIN_MESSAGES, 'Wrong login or password')).toBe(
      'Wrong login or password',
    );
  });
});

describe('fetchProfile', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('refuses to call the API without a token', () => {
    expect(() => fetchProfile()).toThrow('Missing auth token');
  });
});

describe('session teardown', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('logout clears the stored token even when the API call fails', async () => {
    saveSession('token-123');
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')));

    await logout();

    expect(getToken()).toBeNull();
    expect(localStorage.getItem(AUTH_TOKEN_KEY)).toBeNull();
  });

  it('logout sends POST /auth/logout with the bearer token', async () => {
    saveSession('token-123');
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 204 });
    vi.stubGlobal('fetch', fetchMock);

    await logout();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/logout', {
      method: 'POST',
      headers: { Authorization: 'Bearer token-123' },
    });
    expect(getToken()).toBeNull();
  });

  it('deleteAccount sends DELETE /auth/me with the bearer token and clears the session', async () => {
    saveSession('token-123');
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 204 });
    vi.stubGlobal('fetch', fetchMock);

    await deleteAccount();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/me', {
      method: 'DELETE',
      headers: { Authorization: 'Bearer token-123' },
    });
    expect(getToken()).toBeNull();
  });

  it('deleteAccount skips the request when there is no session', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    await deleteAccount();

    expect(fetchMock).not.toHaveBeenCalled();
    expect(getToken()).toBeNull();
  });

  // Same policy as logout: a dead token must never keep the UI signed in.
  it('deleteAccount clears the stored token when the API rejects the call', async () => {
    saveSession('token-123');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 401 }));

    await deleteAccount();

    expect(getToken()).toBeNull();
  });

  it('deleteAccount clears the stored token when the network call fails', async () => {
    saveSession('token-123');
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')));

    await deleteAccount();

    expect(getToken()).toBeNull();
    expect(localStorage.getItem(AUTH_TOKEN_KEY)).toBeNull();
  });

  // Account deletion is not logout: the logout endpoint must never be touched.
  it('deleteAccount never calls the logout endpoint', async () => {
    saveSession('token-123');
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 204 });
    vi.stubGlobal('fetch', fetchMock);

    await deleteAccount();

    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual(['/api/auth/me']);
  });
});
