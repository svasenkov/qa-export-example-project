<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import Button from '../components/Button.vue';
import Panel from '../components/Panel.vue';
import PlaqueField from '../components/PlaqueField.vue';
import { useI18n } from '../i18n';
import {
  getToken,
  register,
  resolveAuthErrorMessage,
  saveSession,
  validateCredentials,
} from '../lib/auth';
import { registerMessages } from '../lib/messages';

type RegisterError =
  | { type: 'none' }
  | { type: 'validation' }
  | { type: 'mismatch' }
  | { type: 'network' }
  | { type: 'api'; message: string };

const router = useRouter();
const { lang, copy } = useI18n();
const messages = computed(() => registerMessages(lang.value));
const username = ref('');
const password = ref('');
const confirmPassword = ref('');
const error = ref<RegisterError>({ type: 'none' });
const submitting = ref(false);

const errorText = computed(() => {
  const current = error.value;
  const msgs = messages.value;
  if (current.type === 'validation') {
    return validateCredentials(username.value.trim(), password.value.trim(), msgs) ?? '';
  }
  if (current.type === 'mismatch') {
    return msgs.errorPasswordMismatch ?? '';
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
  const trimmedConfirm = confirmPassword.value.trim();
  const msgs = messages.value;

  const validationError = validateCredentials(trimmedLogin, trimmedPassword, msgs);
  if (validationError) {
    error.value = { type: 'validation' };
    return;
  }
  if (trimmedPassword !== trimmedConfirm) {
    error.value = { type: 'mismatch' };
    return;
  }

  submitting.value = true;
  try {
    const response = await register(trimmedLogin, trimmedPassword);
    saveSession(response.token);
    await router.push(response.redirectUrl || '/');
  } catch (err) {
    if ((err as { network?: boolean } | undefined)?.network) {
      error.value = { type: 'network' };
    } else {
      error.value = {
        type: 'api',
        message: resolveAuthErrorMessage(err, msgs, msgs.errorRegistrationFailed ?? ''),
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
      :title="copy.register.title"
      title-test-id="register-form-title"
      test-id="register-panel"
      class-name="auth-panel"
    >
      <form
        id="register-form"
        class="auth-form"
        data-testid="register-form"
        @submit="handleSubmit"
      >
        <div class="plaque-field-list">
          <PlaqueField
            v-model="username"
            :label="copy.register.loginLabel"
            id="register-login-input"
            name="username"
            type="text"
            autocomplete="username"
            data-testid="register-login-input"
          />
          <PlaqueField
            v-model="password"
            :label="copy.register.passwordLabel"
            id="register-password-input"
            name="password"
            type="password"
            autocomplete="new-password"
            data-testid="register-password-input"
          />
          <PlaqueField
            v-model="confirmPassword"
            :label="copy.register.confirmLabel"
            id="confirm-password-input"
            name="confirm-password"
            type="password"
            autocomplete="new-password"
            data-testid="confirm-password-input"
          />
        </div>

        <p id="register-error-message" class="auth-error" aria-live="polite" data-testid="register-error-message">
          {{ errorText }}
        </p>

        <div class="auth-form__actions">
          <Button
            id="register-submit-button"
            type="submit"
            variant="primary"
            block
            data-testid="register-submit-button"
            :disabled="submitting"
          >
            {{ copy.register.submit }}
          </Button>
        </div>
      </form>

      <p class="auth-footer-link">
        {{ copy.register.haveAccount }}
        <RouterLink to="/login" data-testid="login-link">{{ copy.register.loginLink }}</RouterLink>
      </p>
    </Panel>
  </main>
</template>
