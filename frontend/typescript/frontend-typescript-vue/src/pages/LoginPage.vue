<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import Button from '../components/Button.vue';
import Panel from '../components/Panel.vue';
import PlaqueField from '../components/PlaqueField.vue';
import { useI18n } from '../i18n';
import {
  getToken,
  login,
  resolveAuthErrorMessage,
  saveSession,
  validateCredentials,
} from '../lib/auth';
import { loginMessages } from '../lib/messages';

type LoginError =
  | { type: 'none' }
  | { type: 'validation' }
  | { type: 'network' }
  | { type: 'api'; message: string };

const router = useRouter();
const { lang, copy } = useI18n();
const messages = computed(() => loginMessages(lang.value));
const username = ref('');
const password = ref('');
const error = ref<LoginError>({ type: 'none' });
const submitting = ref(false);

const errorText = computed(() => {
  const current = error.value;
  const msgs = messages.value;
  if (current.type === 'validation') {
    return validateCredentials(username.value.trim(), password.value.trim(), msgs) ?? '';
  }
  if (current.type === 'network') {
    return msgs.errorNetwork;
  }
  if (current.type === 'api') {
    return current.message;
  }
  return '';
});

onMounted(() => {
  if (getToken()) {
    void router.replace('/');
  }
});

async function handleSubmit(event: Event): Promise<void> {
  event.preventDefault();
  error.value = { type: 'none' };

  const trimmedLogin = username.value.trim();
  const trimmedPassword = password.value.trim();
  const msgs = messages.value;
  const validationError = validateCredentials(trimmedLogin, trimmedPassword, msgs);
  if (validationError) {
    error.value = { type: 'validation' };
    return;
  }

  submitting.value = true;
  try {
    const response = await login(trimmedLogin, trimmedPassword);
    saveSession(response.token);
    await router.push(response.redirectUrl || '/');
  } catch (err) {
    if ((err as { network?: boolean } | undefined)?.network) {
      error.value = { type: 'network' };
    } else {
      error.value = {
        type: 'api',
        message: resolveAuthErrorMessage(err, msgs, msgs.errorWrongCredentials ?? ''),
      };
    }
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <main class="auth-page">
    <Panel
      :title="copy.login.title"
      title-test-id="login-form-title"
      test-id="login-panel"
      class-name="auth-panel"
    >
      <form id="login-form" class="auth-form" data-testid="login-form" @submit="handleSubmit">
        <div class="plaque-field-list">
          <PlaqueField
            v-model="username"
            :label="copy.login.loginLabel"
            id="login-input"
            name="username"
            type="text"
            autocomplete="username"
            data-testid="login-input"
          />
          <PlaqueField
            v-model="password"
            :label="copy.login.passwordLabel"
            id="password-input"
            name="password"
            type="password"
            autocomplete="current-password"
            data-testid="password-input"
          />
        </div>

        <p id="error-message" class="auth-error" aria-live="polite" data-testid="error-message">
          {{ errorText }}
        </p>

        <div class="auth-form__actions">
          <Button
            id="submit-button"
            type="submit"
            variant="primary"
            block
            data-testid="submit-button"
            :disabled="submitting"
          >
            {{ copy.login.submit }}
          </Button>
        </div>
      </form>

      <p class="auth-footer-link">
        {{ copy.login.noAccount }}
        <RouterLink to="/register" data-testid="register-link">{{ copy.login.registerLink }}</RouterLink>
      </p>
    </Panel>
  </main>
</template>
