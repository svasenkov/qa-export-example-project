import { render, screen, waitFor } from '@testing-library/vue';
import { nextTick } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App.vue';
import { HEADER_LANG_CHANGE, ru } from '../i18n';
import { buildHeaderConfig } from '../lib/headerConfig';
import HomePage from '../pages/HomePage.vue';
import LoginPage from '../pages/LoginPage.vue';
import RegisterPage from '../pages/RegisterPage.vue';

async function dispatchLang(lang: string): Promise<void> {
  document.dispatchEvent(new CustomEvent(HEADER_LANG_CHANGE, { detail: { lang } }));
  await nextTick();
}

function jsonResponse(body: unknown) {
  return { ok: true, status: 200, json: async () => body };
}

function stubApis(): void {
  vi.stubGlobal(
    'fetch',
    vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/health')) {
        return Promise.resolve(jsonResponse({ status: 'UP', service: 'backend-go-stdlib' }));
      }
      if (url.includes('/api/items')) {
        return Promise.resolve(jsonResponse({ items: [] }));
      }
      return Promise.reject(new Error(`unexpected request: ${url}`));
    }),
  );
}

const routes = [
  { path: '/', component: HomePage },
  { path: '/login', component: LoginPage },
  { path: '/register', component: RegisterPage },
];

async function renderApp(initialPath: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes,
  });
  await router.push(initialPath);
  await router.isReady();
  return render(App, { global: { plugins: [router] } });
}

describe('App', () => {
  beforeEach(() => {
    localStorage.clear();
    stubApis();
    window.headerConfig = buildHeaderConfig('en');
    window.__designSystemRemountHeader = vi.fn().mockResolvedValue(undefined);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    delete window.headerConfig;
    delete window.__designSystemRemountHeader;
  });

  it('mounts the header slot and routes / to the home page', async () => {
    await renderApp('/');

    expect(screen.getByTestId('app-header-mount')).toBeInTheDocument();
    expect(screen.getByTestId('multistack-layout')).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByTestId('items-list')).toHaveTextContent('No items found.'),
    );
  });

  it('routes /login to the login form', async () => {
    await renderApp('/login');

    expect(screen.getByTestId('login-form-title')).toHaveTextContent('Login Form');
  });

  it('routes /register to the register form', async () => {
    await renderApp('/register');

    expect(screen.getByTestId('register-form-title')).toHaveTextContent('Register');
  });

  it('remounts header nav once when language changes', async () => {
    const remount = window.__designSystemRemountHeader as ReturnType<typeof vi.fn>;
    await renderApp('/login');

    expect(screen.getByTestId('login-form-title')).toHaveTextContent('Login Form');
    await dispatchLang('ru');
    expect(screen.getByTestId('login-form-title')).toHaveTextContent(ru.login.title);
    await waitFor(() => expect(remount).toHaveBeenCalledTimes(1));

    await dispatchLang('ru');
    expect(remount).toHaveBeenCalledTimes(1);
  });
});
