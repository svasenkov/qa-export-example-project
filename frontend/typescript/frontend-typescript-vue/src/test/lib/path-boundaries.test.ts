import { afterEach, expect, it, vi } from 'vitest';
import { appPath, apiUrl } from '../../lib/appBase';
import { getToken } from '../../lib/auth';

afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.resetModules(); });
it.each([['health', '/api/health'], ['/api/health', '/api/health'], ['/api', '/api'], ['', '/api/']])('API path %s resolves within the same origin', (input, expected) => {
 expect(apiUrl(input)).toBe(expected);
});
it('relative application paths and default home remain within the mount', () => {
 expect(appPath('login')).toBe('/login'); expect(appPath()).toBe('/'); expect(appPath('')).toBe('/');
});
it('disabled storage yields no session', () => {
 vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('disabled'); });
 expect(getToken()).toBeNull();
});
it('bare frontend mount resolves independently of a backend', async () => {
 vi.resetModules(); vi.stubGlobal('window', { location: { pathname: '/stack/frontend-typescript-vue/login' } });
 const paths = await import('../../lib/appBase');
 expect(paths.APP_BASE).toBe('/stack/frontend-typescript-vue'); expect(paths.API_BASE).toBe('/api');
 expect(paths.appPath('register')).toBe('/stack/frontend-typescript-vue/register');
});
it('server import has no window dependency', async () => {
 vi.resetModules(); vi.stubGlobal('window', undefined);
 const paths = await import('../../lib/appBase');
 expect(paths.APP_BASE).toBe(''); expect(paths.authTokenStorageKey()).toBe('authToken');
});
