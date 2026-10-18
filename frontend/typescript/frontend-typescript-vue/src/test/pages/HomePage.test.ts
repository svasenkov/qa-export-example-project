import { afterEach, beforeEach, describe, expect, it, type Mock, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/vue';
import userEvent from '@testing-library/user-event';
import { nextTick } from 'vue';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import { HEADER_LANG_CHANGE, ru } from '../../i18n';
import HomePage from '../../pages/HomePage.vue';

async function dispatchLang(lang: string): Promise<void> {
  document.dispatchEvent(new CustomEvent(HEADER_LANG_CHANGE, { detail: { lang } }));
  await nextTick();
}

function jsonResponse(body: unknown, ok = true, status = 200): Response {
  return {
    ok,
    status,
    json: async () => body,
  } as Response;
}

let router: Router;

async function renderHome() {
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: HomePage },
      { path: '/login', component: { template: '<div />' } },
    ],
  });
  await router.push('/');
  await router.isReady();
  return render(HomePage, { global: { plugins: [router] } });
}

function stubDefaultApis(
  overrides?: (
    url: string,
    init?: RequestInit,
  ) => Response | Promise<Response> | null,
) {
  vi.stubGlobal(
    'fetch',
    vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const override = overrides?.(url, init);
      if (override) return Promise.resolve(override);

      if (url.includes('/api/health')) {
        return Promise.resolve(jsonResponse({ status: 'UP', service: 'backend-go-stdlib' }));
      }
      if (url.includes('/api/items')) {
        return Promise.resolve(
          jsonResponse({ items: [{ id: 1, name: 'Alpha', description: 'First item' }] }),
        );
      }
      if (url.includes('/api/auth/me') && init?.method === 'DELETE') {
        return Promise.resolve(jsonResponse({}, true, 204));
      }
      if (url.includes('/api/auth/me')) {
        return Promise.resolve(jsonResponse({ username: 'user1' }));
      }
      if (url.includes('/api/auth/logout')) {
        return Promise.resolve(jsonResponse({}, true, 204));
      }
      return Promise.reject(new Error(`unexpected request: ${url}`));
    }),
  );
}

type FetchMock = Mock<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>;

function deleteAccountCalls() {
  return (globalThis.fetch as unknown as FetchMock).mock.calls.filter(
    ([input, init]) => String(input).includes('/api/auth/me') && init?.method === 'DELETE',
  );
}

describe('HomePage', () => {
  beforeEach(() => {
    localStorage.clear();
    stubDefaultApis();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders the reference layout with health and items from the API', async () => {
    await renderHome();

    expect(screen.getByTestId('multistack-layout')).toBeInTheDocument();

    await waitFor(() =>
      expect(screen.getByTestId('health-status')).toHaveTextContent(
        '→ UP | service: backend-go-stdlib | frontend: frontend-typescript-vue',
      ),
    );

    expect(await screen.findByTestId('item-row')).toHaveTextContent('Alpha');
  });

  it('keeps the welcome panel hidden without a session token', async () => {
    await renderHome();

    await waitFor(() => expect(screen.getByTestId('item-row')).toBeInTheDocument());
    expect(screen.getByTestId('welcome-panel')).not.toBeVisible();
  });

  it('shows welcome, logout and delete account when profile loads for a session token', async () => {
    localStorage.setItem('authToken', 'valid-token');
    await renderHome();

    await waitFor(() =>
      expect(screen.getByTestId('welcome-message')).toHaveTextContent('Welcome, user1!'),
    );
    expect(screen.getByTestId('welcome-panel')).toBeVisible();
    expect(screen.getByTestId('logout-button')).toHaveTextContent('Logout');
    expect(screen.getByTestId('delete-account-button')).toHaveTextContent('Delete account');
  });

  it('logs out and navigates to login', async () => {
    const user = userEvent.setup();
    localStorage.setItem('authToken', 'valid-token');
    await renderHome();

    await user.click(await screen.findByTestId('logout-button'));

    await waitFor(() => expect(router.currentRoute.value.path).toBe('/login'));
    expect(localStorage.getItem('authToken')).toBeNull();
  });

  it('deletes the account, clears the session and navigates to login', async () => {
    const user = userEvent.setup();
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    localStorage.setItem('authToken', 'valid-token');
    await renderHome();

    await user.click(await screen.findByTestId('delete-account-button'));

    expect(confirmSpy).toHaveBeenCalledWith('Delete this account? This cannot be undone.');
    expect(deleteAccountCalls()).toHaveLength(1);
    expect(deleteAccountCalls()[0][1]).toMatchObject({
      method: 'DELETE',
      headers: { Authorization: 'Bearer valid-token' },
    });
    await waitFor(() => expect(router.currentRoute.value.path).toBe('/login'));
    expect(localStorage.getItem('authToken')).toBeNull();
    confirmSpy.mockRestore();
  });

  it('cancelling the confirm keeps the session and sends no delete request', async () => {
    const user = userEvent.setup();
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);
    localStorage.setItem('authToken', 'valid-token');
    await renderHome();

    await user.click(await screen.findByTestId('delete-account-button'));

    expect(confirmSpy).toHaveBeenCalledOnce();
    expect(deleteAccountCalls()).toHaveLength(0);
    expect(router.currentRoute.value.path).toBe('/');
    expect(localStorage.getItem('authToken')).toBe('valid-token');
    confirmSpy.mockRestore();
  });

  // Mirrors logout: the local session goes even when the API refuses the token.
  it('clears the session when the delete call is rejected', async () => {
    const user = userEvent.setup();
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    localStorage.setItem('authToken', 'valid-token');
    stubDefaultApis((url, init) => {
      if (url.includes('/api/auth/me') && init?.method === 'DELETE') {
        return jsonResponse({ message: 'Unauthorized' }, false, 401);
      }
      return null;
    });

    await renderHome();

    await user.click(await screen.findByTestId('delete-account-button'));

    await waitFor(() => expect(router.currentRoute.value.path).toBe('/login'));
    expect(localStorage.getItem('authToken')).toBeNull();
    confirmSpy.mockRestore();
  });

  it('clears an invalid session and keeps the welcome panel hidden', async () => {
    localStorage.setItem('authToken', 'bad-token');
    stubDefaultApis((url, init) => {
      if (url.includes('/api/auth/me') && init?.method !== 'DELETE') {
        return jsonResponse({ message: 'Unauthorized' }, false, 401);
      }
      return null;
    });

    await renderHome();

    await waitFor(() => expect(localStorage.getItem('authToken')).toBeNull());
    expect(screen.getByTestId('welcome-panel')).not.toBeVisible();
  });

  it('shows health error state when health API fails', async () => {
    stubDefaultApis((url) => {
      if (url.includes('/api/health')) {
        return jsonResponse({ message: 'down' }, false, 500);
      }
      return null;
    });

    await renderHome();

    await waitFor(() =>
      expect(screen.getByTestId('health-status')).toHaveTextContent('✗ health: HTTP 500'),
    );

    await dispatchLang('ru');
    expect(screen.getByTestId('health-status')).toHaveTextContent('✗ статус: HTTP 500');
  });

  it('shows items error state when items API fails', async () => {
    stubDefaultApis((url) => {
      if (url.includes('/api/items')) {
        return jsonResponse({ message: 'boom' }, false, 500);
      }
      return null;
    });

    await renderHome();

    await waitFor(() =>
      expect(screen.getByTestId('items-list')).toHaveTextContent('✗ items: HTTP 500'),
    );
  });

  it('shows empty items state when API returns no rows', async () => {
    stubDefaultApis((url) => {
      if (url.includes('/api/items')) {
        return jsonResponse({ items: [] });
      }
      return null;
    });

    await renderHome();

    await waitFor(() =>
      expect(screen.getByTestId('items-list')).toHaveTextContent('No items found.'),
    );
  });

  it('shows empty items state when the payload carries no items field', async () => {
    stubDefaultApis((url) => {
      if (url.includes('/api/items')) {
        return jsonResponse({});
      }
      return null;
    });

    await renderHome();

    await waitFor(() =>
      expect(screen.getByTestId('items-list')).toHaveTextContent('No items found.'),
    );
  });

  // Callbacks landing after teardown must not resurrect state on a dead view.
  it('ignores late resolutions that land after unmount', async () => {
    localStorage.setItem('authToken', 'valid-token');
    const pending: Promise<Response>[] = [];
    stubDefaultApis((url) => {
      if (
        url.includes('/api/health') ||
        url.includes('/api/items') ||
        url.includes('/api/auth/me')
      ) {
        const delayed = new Promise<Response>((resolve) =>
          setTimeout(() => resolve(jsonResponse({ status: 'UP', service: 'svc', items: [], username: 'late' })), 30),
        );
        pending.push(delayed);
        return delayed;
      }
      return null;
    });

    const { unmount } = await renderHome();
    const healthEl = screen.getByTestId('health-status');
    unmount();
    await Promise.all(pending.map((p) => p.then(() => new Promise((r) => setTimeout(r, 50)))));

    // The unmounted view keeps its pre-resolution copy — nothing re-renders.
    expect(healthEl).toHaveTextContent('Checking health');
    expect(localStorage.getItem('authToken')).toBe('valid-token');
  });

  it('ignores late rejections that land after unmount', async () => {
    localStorage.setItem('authToken', 'valid-token');
    const pending: Promise<Response>[] = [];
    stubDefaultApis((url) => {
      if (
        url.includes('/api/health') ||
        url.includes('/api/items') ||
        url.includes('/api/auth/me')
      ) {
        const delayed = new Promise<Response>((_, reject) =>
          setTimeout(() => reject(new Error('late failure')), 30),
        );
        pending.push(delayed);
        return delayed;
      }
      return null;
    });

    const { unmount } = await renderHome();
    const healthEl = screen.getByTestId('health-status');
    unmount();
    await Promise.allSettled(pending);
    await new Promise((r) => setTimeout(r, 50));

    expect(healthEl).toHaveTextContent('Checking health');
    // A rejected profile lookup after unmount must not clear the session.
    expect(localStorage.getItem('authToken')).toBe('valid-token');
  });

  it('retranslates chrome on header:lang-change and keeps API payloads', async () => {
    localStorage.setItem('authToken', 'valid-token');
    await renderHome();

    await waitFor(() =>
      expect(screen.getByTestId('welcome-message')).toHaveTextContent('Welcome, user1!'),
    );
    expect(screen.getByTestId('item-row')).toHaveTextContent('Alpha');

    await dispatchLang('ru');

    expect(screen.getByTestId('welcome-message')).toHaveTextContent('Добро пожаловать, user1!');
    expect(screen.getByTestId('logout-button')).toHaveTextContent(ru.home.logout);
    expect(screen.getByTestId('delete-account-button')).toHaveTextContent(ru.home.deleteAccount);
    expect(screen.getByTestId('health-status')).toHaveTextContent(
      '→ UP | сервис: backend-go-stdlib | фронтенд: frontend-typescript-vue',
    );
    expect(screen.getByTestId('item-row')).toHaveTextContent('Alpha');
  });

  it('asks to confirm delete in the active language', async () => {
    const user = userEvent.setup();
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);
    localStorage.setItem('authToken', 'valid-token');
    await renderHome();

    await screen.findByTestId('delete-account-button');
    await dispatchLang('ru');
    await user.click(screen.getByTestId('delete-account-button'));

    expect(confirmSpy).toHaveBeenCalledWith(ru.home.deleteConfirm);
    confirmSpy.mockRestore();
  });

  it('retranslates empty and error item chrome without touching API names', async () => {
    stubDefaultApis((url) => {
      if (url.includes('/api/items')) {
        return jsonResponse({ items: [] });
      }
      return null;
    });
    await renderHome();
    await waitFor(() =>
      expect(screen.getByTestId('items-list')).toHaveTextContent('No items found.'),
    );

    await dispatchLang('ru');
    expect(screen.getByTestId('items-list')).toHaveTextContent(ru.home.itemsEmpty);
  });

  it('keeps items error payload and translates the prefix', async () => {
    stubDefaultApis((url) => {
      if (url.includes('/api/items')) {
        return jsonResponse({ message: 'boom' }, false, 500);
      }
      return null;
    });
    await renderHome();
    await waitFor(() =>
      expect(screen.getByTestId('items-list')).toHaveTextContent('✗ items: HTTP 500'),
    );

    await dispatchLang('ru');
    expect(screen.getByTestId('items-list')).toHaveTextContent('✗ элементы: HTTP 500');
  });

  it('shows checking copy in the stored language while health is pending', async () => {
    localStorage.setItem('zds-lang', 'ru');
    const pending = new Promise<Response>(() => {});
    stubDefaultApis((url) => {
      if (url.includes('/api/health') || url.includes('/api/items')) {
        return pending;
      }
      return null;
    });

    const { unmount } = await renderHome();
    expect(screen.getByTestId('health-status')).toHaveTextContent(ru.home.healthChecking);
    expect(screen.getByTestId('items-list')).toHaveTextContent(ru.home.itemsLoading);
    unmount();
  });
});

// Observe both sides of pending requests: an immediate final-state assertion misses early cleanup.
describe('HomePage session contract', () => {
  beforeEach(() => { localStorage.clear(); localStorage.setItem('authToken', 'valid-token'); });
  afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });

  it.each([401, 500, 'offline'] as const)('ACC-002 clears session on profile failure %s', async (failure) => {
    stubDefaultApis((url) => url.includes('/api/auth/me')
      ? (failure === 'offline' ? Promise.reject(new TypeError('offline')) : jsonResponse({}, false, failure))
      : null);
    await renderHome();
    await waitFor(() => expect(localStorage.getItem('authToken')).toBeNull());
    expect(screen.getByTestId('welcome-panel')).not.toBeVisible();
    expect(screen.getByTestId('welcome-message')).toHaveTextContent('');
  });

  it.each(['en', 'ru'])('ACC-003 waits for profile and uses its name (%s)', async (lang) => {
    localStorage.setItem('zds-lang', lang);
    let respond!: (response: Response) => void;
    const pending = new Promise<Response>(resolve => { respond = resolve; });
    stubDefaultApis((url) => url.includes('/api/auth/me') ? pending : null);
    await renderHome();
    expect(screen.getByTestId('welcome-panel')).not.toBeVisible();
    expect(fetch).toHaveBeenCalledWith('/api/auth/me', {
      method: 'GET', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer valid-token' },
    });
    respond(jsonResponse({ username: 'profile-name' }));
    await waitFor(() => expect(screen.getByTestId('welcome-panel')).toBeVisible());
    expect(screen.getByTestId('welcome-message').textContent?.trim()).toBe(
      lang === 'ru' ? 'Добро пожаловать, profile-name!' : 'Welcome, profile-name!',
    );
  });

  for (const action of ['logout', 'delete-account'] as const) {
    it.each(['success', 'http-error', 'offline'])(`ACC-004/005 ${action} waits, then clears and redirects: %s`, async (outcome) => {
      let respond!: (response: Response) => void;
      let reject!: (reason: Error) => void;
      const pending = new Promise<Response>((resolve, fail) => { respond = resolve; reject = fail; });
      stubDefaultApis((url, init) =>
        (action === 'logout' ? url.includes('/api/auth/logout') : init?.method === 'DELETE') ? pending : null);
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      await renderHome();
      await waitFor(() => expect(screen.getByTestId('welcome-panel')).toBeVisible());
      await userEvent.setup().click(screen.getByTestId(`${action}-button`));
      expect(localStorage.getItem('authToken')).toBe('valid-token');
      expect(router.currentRoute.value.path).toBe('/');
      expect(screen.getByTestId('welcome-panel')).toBeVisible();
      if (outcome === 'offline') reject(new TypeError('offline'));
      else respond(jsonResponse({}, outcome === 'success', outcome === 'success' ? 204 : 500));
      await waitFor(() => expect(router.currentRoute.value.path).toBe('/login'));
      expect(localStorage.getItem('authToken')).toBeNull();
    });
  }
});
