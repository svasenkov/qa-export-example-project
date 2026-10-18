import { render, screen, waitFor } from '@testing-library/vue';
import userEvent from '@testing-library/user-event';
import { createMemoryHistory, createRouter } from 'vue-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import LoginPage from '../../pages/LoginPage.vue';
import RegisterPage from '../../pages/RegisterPage.vue';
import type { AuthMessages } from '../../lib/auth';

// Optional message keys are declared `string?` — a dictionary may legitimately
// omit them, and the pages must fall back without crashing or leaking
// "undefined" into the DOM.
vi.mock('../../lib/messages', async (original) => {
  const actual = await original<typeof import('../../lib/messages')>();
  return {
    ...actual,
    loginMessages: (lang: Parameters<typeof actual.loginMessages>[0]): AuthMessages => {
      const messages = { ...actual.loginMessages(lang) };
      delete messages.errorWrongCredentials;
      return messages;
    },
    registerMessages: (lang: Parameters<typeof actual.registerMessages>[0]): AuthMessages => {
      const messages = { ...actual.registerMessages(lang) };
      delete messages.errorPasswordMismatch;
      delete messages.errorRegistrationFailed;
      return messages;
    },
  };
});

function testRouter(path: string, component: object) {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path, component },
    ],
  });
}

describe('auth pages without optional message keys', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('LoginPage still surfaces the API error text with errorWrongCredentials absent', async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        json: async () => ({ message: 'Server says no' }),
      }),
    );
    const router = testRouter('/login', LoginPage);
    await router.push('/login');
    await router.isReady();
    render(LoginPage, { global: { plugins: [router] } });

    await user.type(screen.getByTestId('login-input'), 'someone');
    await user.type(screen.getByTestId('password-input'), 'password1');
    await user.click(screen.getByTestId('submit-button'));

    await waitFor(() =>
      expect(screen.getByTestId('error-message')).toHaveTextContent('Server says no'),
    );
  });

  it('RegisterPage renders an empty message when optional keys are absent', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const router = testRouter('/register', RegisterPage);
    await router.push('/register');
    await router.isReady();
    render(RegisterPage, { global: { plugins: [router] } });

    await user.type(screen.getByTestId('register-login-input'), 'newuser');
    await user.type(screen.getByTestId('register-password-input'), 'password123');
    await user.type(screen.getByTestId('confirm-password-input'), 'password124');
    await user.click(screen.getByTestId('register-submit-button'));

    // The mismatch error has no dictionary text — the paragraph stays empty.
    await waitFor(() =>
      expect(screen.getByTestId('register-error-message').textContent).toBe(''),
    );
    expect(fetchMock).not.toHaveBeenCalled();

    // Fixing confirm and resubmitting reaches the API-error fallback path.
    fetchMock.mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => ({ message: 'Backend rejected' }),
    });
    const confirm = screen.getByTestId('confirm-password-input');
    await user.clear(confirm);
    await user.type(confirm, 'password123');
    await user.click(screen.getByTestId('register-submit-button'));

    await waitFor(() =>
      expect(screen.getByTestId('register-error-message')).toHaveTextContent('Backend rejected'),
    );
  });
});
