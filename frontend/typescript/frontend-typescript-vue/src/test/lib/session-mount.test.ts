import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

describe('ACC-001 session storage by mount', () => {
  beforeEach(() => { localStorage.clear(); vi.resetModules(); });
  afterEach(() => { window.history.replaceState({}, '', '/'); vi.resetModules(); vi.unstubAllGlobals(); });

  it.each([
    ['/', 'authToken', '/api'],
    ['/stack/backend-go-stdlib/frontend-typescript-vue/', 'authToken:backend-go-stdlib', '/stack/backend-go-stdlib/api'],
  ])('stores and transports the token under %s', async (path, key, api) => {
    window.history.replaceState({}, '', path);
    const { saveSession, getToken, fetchProfile, deleteAccount } = await import('../../lib/auth');
    localStorage.setItem('authToken:backend-other', 'other-session');
    saveSession('current-token');
    expect(localStorage.getItem(key)).toBe('current-token');
    expect(getToken()).toBe('current-token');
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => ({ username: 'abc' }) });
    vi.stubGlobal('fetch', fetchMock);
    await fetchProfile();
    expect(fetchMock).toHaveBeenLastCalledWith(`${api}/auth/me`, {
      method: 'GET', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer current-token' },
    });
    await deleteAccount();
    expect(fetchMock).toHaveBeenLastCalledWith(`${api}/auth/me`, {
      method: 'DELETE', headers: { Authorization: 'Bearer current-token' },
    });
    expect(localStorage.getItem(key)).toBeNull();
    expect(localStorage.getItem('authToken:backend-other')).toBe('other-session');
  });
});
