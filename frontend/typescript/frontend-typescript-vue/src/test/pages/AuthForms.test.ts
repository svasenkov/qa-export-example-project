import { fireEvent, render, screen, waitFor } from '@testing-library/vue';
import { createMemoryHistory, createRouter } from 'vue-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import LoginPage from '../../pages/LoginPage.vue';
import RegisterPage from '../../pages/RegisterPage.vue';
import HomePage from '../../pages/HomePage.vue';

// Independent expected copy from OpenSpec messages.md, not the production dictionary.
const copy = {
  en: ['Login and password are required (minimum 3 and 6 characters)',
    'Login is required (minimum 3 characters)', 'Login must be at least 3 characters',
    'Password is required (minimum 6 characters)', 'Password must be at least 6 characters',
    'Passwords do not match', 'Network error. Check your connection and try again.'],
  ru: ['Нужны логин и пароль (минимум 3 и 6 символов)',
    'Логин обязателен (минимум 3 символов)', 'Логин должен быть не короче 3 символов',
    'Пароль обязателен (минимум 6 символов)', 'Пароль должен быть не короче 6 символов',
    'Пароли не совпадают', 'Ошибка сети. Проверьте соединение и попробуйте снова.'],
};
const forms = [
  { kind: 'login', page: LoginPage, username: 'login-input', password: 'password-input',
    error: 'error-message', status: 401, message: 'Wrong login or password' },
  { kind: 'register', page: RegisterPage, username: 'register-login-input', password: 'register-password-input',
    error: 'register-error-message', status: 409, message: 'Username already taken' },
] as const;

for (const form of forms) {
  describe(`${form.kind} OpenSpec form contract`, () => {
    beforeEach(() => localStorage.clear());
    afterEach(() => vi.unstubAllGlobals());

    async function mount(withHome = false) {
      const router = createRouter({
        history: createMemoryHistory(),
        routes: [{ path: '/login', component: LoginPage },
          { path: '/register', component: RegisterPage },
          { path: '/', component: withHome ? HomePage : { template: '<div />' } },
          { path: '/destination', component: { template: '<div />' } }],
      });
      await router.push(`/${form.kind}`);
      await router.isReady();
      render(withHome ? { template: '<RouterView />' } : form.page, { global: { plugins: [router] } });
      return router;
    }

    async function submit(username: string, password: string, confirm = password) {
      await fireEvent.update(screen.getByTestId(form.username), username);
      await fireEvent.update(screen.getByTestId(form.password), password);
      if (form.kind === 'register') await fireEvent.update(screen.getByTestId('confirm-password-input'), confirm);
      await fireEvent.submit(screen.getByTestId(`${form.kind}-form`));
    }

    for (const lang of ['en', 'ru'] as const) {
      describe(lang, () => {
        beforeEach(() => { if (lang === 'ru') localStorage.setItem('zds-lang', lang); });

        it.each([
          ['both empty', '', '', 0], ['both whitespace', '  ', '  ', 0],
          ['empty login wins over short password', ' ', 'x', 1],
          ['short login wins over empty password', ' ab ', ' ', 2],
          ['short login wins over short password', 'ab', '12345', 2],
          ['password empty', 'abc', ' ', 3], ['password one below minimum', 'abc', '12345', 4],
        ] as const)('FORM-001/006 blocks request: %s', async (_name, username, password, message) => {
          const fetchMock = vi.fn().mockRejectedValue(new Error('unexpected request'));
          vi.stubGlobal('fetch', fetchMock);
          const router = await mount();
          // Deliberately mismatching confirmation: common validation must win first.
          await submit(username, password, 'different');
          const error = screen.getByTestId(form.error);
          expect(error.textContent?.trim()).toBe(copy[lang][message]);
          expect(error.tagName).toBe('P');
          expect(error).toHaveAttribute('id', form.error);
          expect(error).toHaveAttribute('aria-live', 'polite');
          expect(document.querySelectorAll(`#${form.error}`)).toHaveLength(1);
          expect(fetchMock).not.toHaveBeenCalled();
          expect(localStorage.getItem('authToken')).toBeNull();
          expect(router.currentRoute.value.path).toBe(`/${form.kind}`);
        });

        if (form.kind === 'register') {
          it.each(['different', '', '   '])('REG-001 rejects confirmation %j without request', async (confirm) => {
            const fetchMock = vi.fn().mockRejectedValue(new Error('unexpected request'));
            vi.stubGlobal('fetch', fetchMock);
            await mount();
            await submit('abc', '123456', confirm);
            expect(screen.getByTestId(form.error).textContent?.trim()).toBe(copy[lang][5]);
            expect(fetchMock).not.toHaveBeenCalled();
            expect(localStorage.getItem('authToken')).toBeNull();
          });
        }

        it.each([
          ['verbatim', { message: form.message }, form.message],
          ['empty', { message: '' }, form.kind === 'login'
            ? (lang === 'ru' ? 'Неверный логин или пароль' : 'Wrong login or password')
            : (lang === 'ru' ? 'Регистрация не удалась' : 'Registration failed')],
          ['missing', {}, 'Request failed'], ['null', { message: null }, 'Request failed'],
          ['number', { message: 42 }, 'Request failed'], ['object', { message: {} }, 'Request failed'],
        ])('FORM-005 API message: %s', async (_name, body, expected) => {
          const fetchMock = vi.fn().mockResolvedValue({ ok: false, status: form.status, json: async () => body });
          vi.stubGlobal('fetch', fetchMock);
          const router = await mount();
          await submit('abc', '123456');
          await waitFor(() => expect(screen.getByTestId(form.error).textContent?.trim()).toBe(expected));
          expect(fetchMock).toHaveBeenCalledTimes(1);
          expect(localStorage.getItem('authToken')).toBeNull();
          expect(router.currentRoute.value.path).toBe(`/${form.kind}`);
        });

        it('FORM-005a localizes network failure without creating a session', async () => {
          vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')));
          const router = await mount();
          await submit('abc', '123456');
          await waitFor(() => expect(screen.getByTestId(form.error).textContent?.trim()).toBe(copy[lang][6]));
          expect(localStorage.getItem('authToken')).toBeNull();
          expect(router.currentRoute.value.path).toBe(`/${form.kind}`);
        });
      });
    }

    it.each([
      ['username', 'u'.repeat(65), '123456', 'username must be 3-64 characters'],
      ['password', 'abc', 'p'.repeat(129), 'password must be 6-128 characters'],
    ])('FORM-002 sends excessive %s to the server', async (_field, username, password, message) => {
      const fetchMock = vi.fn().mockResolvedValue({ ok: false, status: 400, json: async () => ({ message }) });
      vi.stubGlobal('fetch', fetchMock);
      await mount();
      await submit(username, password);
      await waitFor(() => expect(screen.getByTestId(form.error).textContent?.trim()).toBe(message));
      expect(fetchMock).toHaveBeenCalledExactlyOnceWith(`/api/auth/${form.kind}`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password }),
      });
      expect(localStorage.getItem('authToken')).toBeNull();
    });

    it.each(['/', '/destination', '', undefined])('AUTH-002/REG-002 saves only after response; redirect %j', async (redirectUrl) => {
      let respond!: (value: unknown) => void;
      const fetchMock = vi.fn(() => new Promise(resolve => { respond = resolve; }));
      vi.stubGlobal('fetch', fetchMock);
      const router = await mount();
      await submit(' abc ', ' 123456 ', ' 123456 ');
      expect(fetchMock).toHaveBeenCalledExactlyOnceWith(`/api/auth/${form.kind}`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'abc', password: '123456' }),
      });
      expect(localStorage.getItem('authToken')).toBeNull();
      expect(router.currentRoute.value.path).toBe(`/${form.kind}`);
      respond({ ok: true, status: form.kind === 'login' ? 200 : 201,
        json: async () => ({ token: 'new-token', username: 'abc', redirectUrl }) });
      await waitFor(() => expect(router.currentRoute.value.path).toBe(redirectUrl || '/'));
      expect(localStorage.getItem('authToken')).toBe('new-token');
    });

    it('AUTH-002/REG-002/ACC-003 greets from profile, never from the auth response', async () => {
      let profile!: (value: unknown) => void;
      const pendingProfile = new Promise(resolve => { profile = resolve; });
      const fetchMock = vi.fn((url: string) => {
        if (url === `/api/auth/${form.kind}`) return Promise.resolve({ ok: true,
          status: form.kind === 'login' ? 200 : 201,
          json: async () => ({ token: 'session-token', username: 'auth-name', redirectUrl: '/' }) });
        if (url === '/api/auth/me') return pendingProfile;
        if (url === '/api/health') return Promise.resolve({ ok: true, json: async () => ({ status: 'ok', service: 'backend-go-stdlib' }) });
        if (url === '/api/items') return Promise.resolve({ ok: true, json: async () => ({ items: [] }) });
        throw new Error(`unexpected URL ${url}`);
      });
      vi.stubGlobal('fetch', fetchMock);
      await mount(true);
      await submit('abc', '123456');
      await waitFor(() => expect(screen.getByTestId('welcome-panel')).not.toBeVisible());
      expect(screen.getByTestId('welcome-message').textContent?.trim()).toBe('');
      expect(fetchMock).toHaveBeenCalledWith('/api/auth/me', {
        method: 'GET', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer session-token' },
      });
      profile({ ok: true, status: 200, json: async () => ({ username: 'profile-name' }) });
      await waitFor(() => expect(screen.getByTestId('welcome-panel')).toBeVisible());
      expect(screen.getByTestId('welcome-message').textContent?.trim()).toBe('Welcome, profile-name!');
    });

    it('FORM-007 preserves field identity and autocomplete', async () => {
      await mount();
      const fields = [
        [form.username, 'username', 'username'],
        [form.password, 'password', form.kind === 'login' ? 'current-password' : 'new-password'],
        ...(form.kind === 'register' ? [['confirm-password-input', 'confirm-password', 'new-password']] : []),
      ];
      for (const [id, name, autocomplete] of fields) {
        expect(screen.getByTestId(id)).toHaveAttribute('id', id);
        expect(screen.getByTestId(id)).toHaveAttribute('name', name);
        expect(screen.getByTestId(id)).toHaveAttribute('autocomplete', autocomplete);
      }
    });
  });
}
