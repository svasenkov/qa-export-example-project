import { render, screen, waitFor } from '@testing-library/vue';
import userEvent from '@testing-library/user-event';
import { nextTick } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HEADER_LANG_CHANGE, LANG_STORAGE_KEY, ru } from '../../i18n';
import LoginPage from '../../pages/LoginPage.vue';

async function dispatchLang(lang: string): Promise<void> {
  document.dispatchEvent(new CustomEvent(HEADER_LANG_CHANGE, { detail: { lang } }));
  await nextTick();
}

function jsonResponse(body: unknown, ok = true, status = 200) {
  return {
    ok,
    status,
    json: async () => body,
  };
}

async function renderLogin() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div data-testid="home-landed" />' } },
      { path: '/login', component: LoginPage },
      { path: '/register', component: { template: '<div />' } },
    ],
  });
  await router.push('/login');
  await router.isReady();
  const view = render(LoginPage, { global: { plugins: [router] } });
  return { router, unmount: view.unmount };
}

describe('LoginPage', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('mounts the login form with canonical title and controls', async () => {
    await renderLogin();

    expect(screen.getByTestId('login-panel')).toBeInTheDocument();
    expect(screen.getByTestId('login-form-title')).toHaveTextContent('Login Form');
    expect(screen.getByTestId('login-input')).toBeInTheDocument();
    expect(screen.getByTestId('login-input')).toHaveAttribute('name', 'username');
    expect(screen.getByTestId('password-input')).toBeInTheDocument();
    expect(screen.getByTestId('password-input')).toHaveAttribute('name', 'password');
    expect(screen.getByTestId('submit-button')).toHaveTextContent('Login');
    expect(screen.getByTestId('register-link')).toBeInTheDocument();
  });

  it('shows the exact login-required error when username is empty', async () => {
    const user = userEvent.setup();
    await renderLogin();

    await user.type(screen.getByTestId('password-input'), 'password1');
    await user.click(screen.getByTestId('submit-button'));

    expect(screen.getByTestId('error-message')).toHaveTextContent(
      'Login is required (minimum 3 characters)',
    );
  });

  it('shows the exact password-required error when password is empty', async () => {
    const user = userEvent.setup();
    await renderLogin();

    await user.type(screen.getByTestId('login-input'), 'user1');
    await user.click(screen.getByTestId('submit-button'));

    expect(screen.getByTestId('error-message')).toHaveTextContent(
      'Password is required (minimum 6 characters)',
    );
  });

  it('saves the session and follows redirectUrl on a successful login', async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({ token: 'fresh-token', username: 'user1', redirectUrl: '/' }),
        }),
      ),
    );
    const { router } = await renderLogin();

    await user.type(screen.getByTestId('login-input'), 'user1');
    await user.type(screen.getByTestId('password-input'), 'password1');
    await user.click(screen.getByTestId('submit-button'));

    await waitFor(() => expect(localStorage.getItem('authToken')).toBe('fresh-token'));
    await waitFor(() => expect(router.currentRoute.value.path).toBe('/'));
  });

  it('shows the API message when the credentials are wrong', async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve({
          ok: false,
          status: 401,
          json: async () => ({ message: 'Wrong login or password' }),
        }),
      ),
    );
    await renderLogin();

    await user.type(screen.getByTestId('login-input'), 'user1');
    await user.type(screen.getByTestId('password-input'), 'password1');
    await user.click(screen.getByTestId('submit-button'));

    await waitFor(() =>
      expect(screen.getByTestId('error-message')).toHaveTextContent('Wrong login or password'),
    );
    expect(localStorage.getItem('authToken')).toBeNull();
  });

  it('shows the network message when the request never reaches the API', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')));
    await renderLogin();

    await user.type(screen.getByTestId('login-input'), 'user1');
    await user.type(screen.getByTestId('password-input'), 'password1');
    await user.click(screen.getByTestId('submit-button'));

    await waitFor(() =>
      expect(screen.getByTestId('error-message')).toHaveTextContent(
        'Network error. Check your connection and try again.',
      ),
    );
  });

  it('redirects an already signed-in visitor to home', async () => {
    localStorage.setItem('authToken', 'valid-token');
    const { router } = await renderLogin();

    await waitFor(() => expect(router.currentRoute.value.path).toBe('/'));
  });

  it('does not translate API error payloads', async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve(jsonResponse({ message: 'Wrong login or password' }, false, 401)),
      ),
    );

    await renderLogin();
    await user.type(screen.getByTestId('login-input'), 'user1');
    await user.type(screen.getByTestId('password-input'), 'wrongpassword');
    await user.click(screen.getByTestId('submit-button'));
    await waitFor(() =>
      expect(screen.getByTestId('error-message')).toHaveTextContent('Wrong login or password'),
    );

    await dispatchLang('ru');
    expect(screen.getByTestId('login-form-title')).toHaveTextContent(ru.login.title);
    expect(screen.getByTestId('error-message')).toHaveTextContent('Wrong login or password');
  });

  it('switches visible copy on header:lang-change without touching testids', async () => {
    const user = userEvent.setup();
    await renderLogin();

    expect(screen.getByTestId('login-form-title')).toHaveTextContent('Login Form');
    await user.type(screen.getByTestId('password-input'), 'password1');
    await user.click(screen.getByTestId('submit-button'));
    expect(screen.getByTestId('error-message')).toHaveTextContent(
      'Login is required (minimum 3 characters)',
    );

    await dispatchLang('ru');

    expect(screen.getByTestId('login-form-title')).toHaveTextContent(ru.login.title);
    expect(screen.getByTestId('submit-button')).toHaveTextContent(ru.login.submit);
    expect(screen.getByTestId('register-link')).toHaveTextContent(ru.login.registerLink);
    expect(screen.getByTestId('login-input')).toHaveAttribute('data-testid', 'login-input');
    expect(screen.getByTestId('error-message')).toHaveTextContent(
      'Логин обязателен (минимум 3 символов)',
    );

    await user.type(screen.getByTestId('login-input'), 'user1');
    expect(screen.getByTestId('error-message')).toHaveTextContent('');
  });

  it('reads zds-lang after unmount/remount', async () => {
    localStorage.setItem(LANG_STORAGE_KEY, 'ru');
    const { unmount } = await renderLogin();
    expect(screen.getByTestId('login-form-title')).toHaveTextContent(ru.login.title);
    expect(document.documentElement.lang).toBe('ru');
    unmount();
    await renderLogin();
    expect(screen.getByTestId('login-form-title')).toHaveTextContent(ru.login.title);
  });

  it('treats an unknown lang event as en', async () => {
    localStorage.setItem(LANG_STORAGE_KEY, 'ru');
    await renderLogin();
    expect(screen.getByTestId('login-form-title')).toHaveTextContent(ru.login.title);
    await dispatchLang('de');
    expect(screen.getByTestId('login-form-title')).toHaveTextContent('Login Form');
  });
});
