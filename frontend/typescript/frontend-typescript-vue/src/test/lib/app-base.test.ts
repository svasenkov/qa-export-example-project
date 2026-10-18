import { afterEach, describe, expect, it, vi } from 'vitest';

// vendor/ds/js/app-base.js is a shared runtime shim: it resolves the active
// stack pair from location and installs appPath/apiUrl on window. Every entry
// below re-executes the real module under a stubbed location.

interface FakeLocation {
  pathname: string;
  search?: string | (() => string);
}

interface BootWindow extends Window {
  BACKEND_ID?: string | null;
  UI_MOUNT?: string;
  APP_BASE?: string;
  API_BASE?: string;
  appPath?: (path: string) => string;
  apiUrl?: (path: string) => string;
}

const w = window as unknown as BootWindow;

async function boot(location: FakeLocation) {
  vi.stubGlobal('location', {
    get pathname() {
      return location.pathname;
    },
    get search() {
      const value = location.search ?? '';
      return typeof value === 'function' ? value() : value;
    },
  });
  vi.resetModules();
  await import('../../../vendor/ds/js/app-base.js');
}

afterEach(() => {
  vi.unstubAllGlobals();
  delete w.BACKEND_ID;
  delete w.UI_MOUNT;
  delete w.APP_BASE;
  delete w.API_BASE;
  delete w.appPath;
  delete w.apiUrl;
});

describe('app-base mount matrix', () => {
  it('resolves the canonical /stack/{backend}/{frontend}/ pair', async () => {
    await boot({ pathname: '/stack/backend-go-stdlib/frontend-typescript-vue/' });

    expect(w.BACKEND_ID).toBe('backend-go-stdlib');
    expect(w.UI_MOUNT).toBe('frontend-typescript-vue');
    expect(w.APP_BASE).toBe('/stack/backend-go-stdlib/frontend-typescript-vue');
    expect(w.API_BASE).toBe('/stack/backend-go-stdlib/api');
  });

  it('resolves the legacy /{backend}/{frontend}/ pair', async () => {
    await boot({ pathname: '/backend-go-stdlib/frontend-typescript-vue/login' });

    expect(w.BACKEND_ID).toBe('backend-go-stdlib');
    expect(w.APP_BASE).toBe('/backend-go-stdlib/frontend-typescript-vue');
    expect(w.API_BASE).toBe('/backend-go-stdlib/api');
  });

  it('resolves a product-only mount under /stack/', async () => {
    await boot({ pathname: '/stack/frontend-typescript-vue/' });

    expect(w.BACKEND_ID).toBeNull();
    expect(w.UI_MOUNT).toBe('frontend-typescript-vue');
    expect(w.APP_BASE).toBe('/stack/frontend-typescript-vue');
    expect(w.API_BASE).toBe('/stack/backend-go-stdlib/api');
  });

  it('resolves a legacy product-only mount', async () => {
    await boot({ pathname: '/frontend-typescript-vue/' });

    expect(w.BACKEND_ID).toBeNull();
    expect(w.UI_MOUNT).toBe('frontend-typescript-vue');
    expect(w.APP_BASE).toBe('/frontend-typescript-vue');
    expect(w.API_BASE).toBe('/api');
  });

  it('honours ?backend=&frontend= query overrides on the hub', async () => {
    await boot({
      pathname: '/stack/',
      search: '?backend=backend-quarkus&frontend=frontend-react',
    });

    expect(w.BACKEND_ID).toBe('backend-quarkus');
    expect(w.APP_BASE).toBe('/stack/backend-quarkus/frontend-react');
    expect(w.API_BASE).toBe('/stack/backend-quarkus/api');
  });

  it('rejects malformed query values and falls back to defaults', async () => {
    await boot({
      pathname: '/stack/',
      search: '?backend=zzz&frontend=zzz',
    });

    expect(w.APP_BASE).toBe('/stack/backend-go-stdlib/frontend-typescript-vue');
    expect(w.API_BASE).toBe('/stack/backend-go-stdlib/api');
  });

  it('treats an empty search string as no query on the hub', async () => {
    await boot({ pathname: '/stack/', search: '' });

    expect(w.APP_BASE).toBe('/stack/backend-go-stdlib/frontend-typescript-vue');
  });

  it('survives a search getter that throws', async () => {
    await boot({
      pathname: '/stack/',
      search: () => {
        throw new Error('opaque location');
      },
    });

    expect(w.APP_BASE).toBe('/stack/backend-go-stdlib/frontend-typescript-vue');
  });

  it('defaults to a local mount outside the stack matrix', async () => {
    await boot({ pathname: '/' });

    expect(w.BACKEND_ID).toBeNull();
    expect(w.APP_BASE).toBe('');
    expect(w.API_BASE).toBe('/api');
  });

  it('defaults to a local mount when pathname is empty', async () => {
    await boot({ pathname: '' });

    expect(w.BACKEND_ID).toBeNull();
    expect(w.APP_BASE).toBe('');
    expect(w.API_BASE).toBe('/api');
  });
});

describe('app-base path helpers', () => {
  it('appPath prefixes any route under the mount', async () => {
    await boot({ pathname: '/stack/backend-go-stdlib/frontend-typescript-vue/' });

    expect(w.appPath?.('/login')).toBe('/stack/backend-go-stdlib/frontend-typescript-vue/login');
    expect(w.appPath?.('register')).toBe('/stack/backend-go-stdlib/frontend-typescript-vue/register');
    expect(w.appPath?.('')).toBe('/stack/backend-go-stdlib/frontend-typescript-vue/');
    expect(w.appPath?.(null as unknown as string)).toBe(
      '/stack/backend-go-stdlib/frontend-typescript-vue/',
    );
  });

  it('apiUrl rewrites /api/* paths under the backend mount', async () => {
    await boot({ pathname: '/stack/backend-go-stdlib/frontend-typescript-vue/' });

    expect(w.apiUrl?.('/api/items')).toBe('/stack/backend-go-stdlib/api/items');
    expect(w.apiUrl?.('/api')).toBe('/stack/backend-go-stdlib/api');
    expect(w.apiUrl?.('/auth/me')).toBe('/stack/backend-go-stdlib/api/auth/me');
    expect(w.apiUrl?.('items')).toBe('/stack/backend-go-stdlib/api/items');
    expect(w.apiUrl?.('')).toBe('/stack/backend-go-stdlib/api/');
    expect(w.apiUrl?.(null as unknown as string)).toBe('/stack/backend-go-stdlib/api/');
  });
});
