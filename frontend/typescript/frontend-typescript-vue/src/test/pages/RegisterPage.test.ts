import { render, screen, waitFor } from '@testing-library/vue';
import userEvent from '@testing-library/user-event';
import { nextTick } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HEADER_LANG_CHANGE, ru } from '../../i18n';
import RegisterPage from '../../pages/RegisterPage.vue';

async function dispatchLang(lang: string): Promise<void> {
  document.dispatchEvent(new CustomEvent(HEADER_LANG_CHANGE, { detail: { lang } }));
  await nextTick();
}

async function renderRegister() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div data-testid="home-landed" />' } },
      { path: '/login', component: { template: '<div />' } },
      { path: '/register', component: RegisterPage },
    ],
  });
  await router.push('/register');
  await router.isReady();
  render(RegisterPage, { global: { plugins: [router] } });
  return router;
}

describe('RegisterPage', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('mounts the register form with canonical title and controls', async () => {
    await renderRegister();

    expect(screen.getByTestId('register-panel')).toBeInTheDocument();
    expect(screen.getByTestId('register-form-title')).toHaveTextContent('Register');
    expect(screen.getByTestId('register-login-input')).toBeInTheDocument();
    expect(screen.getByTestId('register-password-input')).toBeInTheDocument();
    expect(screen.getByTestId('confirm-password-input')).toBeInTheDocument();
    expect(screen.getByTestId('register-submit-button')).toHaveTextContent('Register');
    expect(screen.getByTestId('login-link')).toBeInTheDocument();
  });




  it('redirects an already signed-in visitor to home', async () => {
    localStorage.setItem('authToken', 'valid-token');
    const router = await renderRegister();

    await waitFor(() => expect(router.currentRoute.value.path).toBe('/'));
  });

  it('switches visible copy on header:lang-change', async () => {
    const user = userEvent.setup();
    await renderRegister();

    await user.type(screen.getByTestId('register-login-input'), 'newuser');
    await user.type(screen.getByTestId('register-password-input'), 'password123');
    await user.type(screen.getByTestId('confirm-password-input'), 'password124');
    await user.click(screen.getByTestId('register-submit-button'));
    expect(screen.getByTestId('register-error-message')).toHaveTextContent('Passwords do not match');

    await dispatchLang('ru');

    expect(screen.getByTestId('register-form-title')).toHaveTextContent(ru.register.title);
    expect(screen.getByTestId('register-submit-button')).toHaveTextContent(ru.register.submit);
    expect(screen.getByTestId('login-link')).toHaveTextContent(ru.register.loginLink);
    expect(screen.getByTestId('register-error-message')).toHaveTextContent(ru.register.errorPasswordMismatch);
  });

  it('retranslates a validation error and clears it when fields become valid', async () => {
    const user = userEvent.setup();
    await renderRegister();

    await user.type(screen.getByTestId('register-login-input'), 'ab');
    await user.type(screen.getByTestId('register-password-input'), 'password123');
    await user.type(screen.getByTestId('confirm-password-input'), 'password123');
    await user.click(screen.getByTestId('register-submit-button'));
    expect(screen.getByTestId('register-error-message')).toHaveTextContent(
      'Login must be at least 3 characters',
    );

    await dispatchLang('ru');
    expect(screen.getByTestId('register-error-message')).toHaveTextContent(
      'Логин должен быть не короче 3 символов',
    );

    await user.type(screen.getByTestId('register-login-input'), 'c');
    expect(screen.getByTestId('register-error-message')).toHaveTextContent('');
  });
});
