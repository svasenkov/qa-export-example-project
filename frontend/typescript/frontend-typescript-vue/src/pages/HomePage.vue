<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import Button from '../components/Button.vue';
import Panel from '../components/Panel.vue';
import { useI18n } from '../i18n';
import { fetchHealth, fetchItems, type Item } from '../lib/api';
import { UI_MOUNT } from '../lib/appBase';
import {
  clearSession,
  deleteAccount,
  fetchProfile,
  formatMessage,
  getToken,
  logout,
} from '../lib/auth';

type HealthState =
  | { status: 'checking' }
  | { status: 'ok'; health: string; service: string }
  | { status: 'error'; message: string };

type ItemsState =
  | { status: 'loading' }
  | { status: 'empty' }
  | { status: 'loaded'; items: Item[] }
  | { status: 'error'; message: string };

const router = useRouter();
const { copy } = useI18n();
const health = ref<HealthState>({ status: 'checking' });
const items = ref<ItemsState>({ status: 'loading' });
const welcomeName = ref<string | null>(null);

const blurbParts = computed(() => copy.value.home.blurb.split('{api}'));
const welcomeText = computed(() => {
  const name = welcomeName.value;
  return name === null ? '' : formatMessage(copy.value.home.welcome, { username: name });
});
const healthText = computed(() => {
  const state = health.value;
  const home = copy.value.home;
  if (state.status === 'checking') {
    return home.healthChecking;
  }
  if (state.status === 'ok') {
    return formatMessage(home.healthOk, {
      status: state.health,
      service: state.service,
      frontend: UI_MOUNT,
    });
  }
  return formatMessage(home.healthError, { message: state.message });
});

let active = true;

onMounted(() => {
  active = true;

  fetchHealth()
    .then((payload) => {
      if (active) {
        health.value = {
          status: 'ok',
          health: payload.status,
          service: payload.service,
        };
      }
    })
    .catch((error: Error) => {
      if (active) {
        health.value = { status: 'error', message: error.message };
      }
    });

  fetchItems()
    .then((payload) => {
      if (!active) return;
      const list = payload.items ?? [];
      items.value = list.length ? { status: 'loaded', items: list } : { status: 'empty' };
    })
    .catch((error: Error) => {
      if (active) {
        items.value = { status: 'error', message: error.message };
      }
    });

  if (getToken()) {
    fetchProfile()
      .then((profile) => {
        if (active) {
          welcomeName.value = profile.username;
        }
      })
      .catch(() => {
        if (active) {
          clearSession();
        }
      });
  }
});

onUnmounted(() => {
  active = false;
});

async function handleLogout(): Promise<void> {
  await logout();
  await router.push('/login');
}

async function handleDeleteAccount(): Promise<void> {
  if (!window.confirm(copy.value.home.deleteConfirm)) {
    return;
  }
  await deleteAccount();
  await router.push('/login');
}

function itemsErrorText(message: string): string {
  return formatMessage(copy.value.home.itemsError, { message });
}
</script>

<template>
  <main
    class="page-shell page-shell--below-header grid multistack"
    data-testid="multistack-layout"
  >
    <Panel :title="copy.home.title">
      <p class="text text--muted">
        {{ blurbParts[0] }}<code>/api/items</code>{{ blurbParts[1] }}
      </p>
    </Panel>

    <Panel
      :title="copy.home.session"
      test-id="welcome-panel"
      :hidden="welcomeName === null"
      body-class-name="multistack__welcome-body"
    >
      <p id="welcome-message" class="text" data-testid="welcome-message">
        {{ welcomeText }}
      </p>
      <Button id="logout-button" variant="primary" data-testid="logout-button" @click="handleLogout">
        {{ copy.home.logout }}
      </Button>
      <Button
        id="delete-account-button"
        variant="danger"
        data-testid="delete-account-button"
        @click="handleDeleteAccount"
      >
        {{ copy.home.deleteAccount }}
      </Button>
    </Panel>

    <Panel :title="copy.home.health" test-id="health-panel">
      <p
        :class="
          health.status === 'error'
            ? 'text text--sm text--muted multistack__error'
            : 'text text--sm text--muted'
        "
        data-testid="health-status"
      >
        {{ healthText }}
      </p>
    </Panel>

    <div class="grid" data-testid="items-list" aria-live="polite">
      <Panel v-if="items.status === 'loading'" :title="copy.home.items">
        <p class="text text--muted">{{ copy.home.itemsLoading }}</p>
      </Panel>
      <Panel v-else-if="items.status === 'empty'" :title="copy.home.items">
        <p class="text text--muted">{{ copy.home.itemsEmpty }}</p>
      </Panel>
      <Panel v-else-if="items.status === 'error'" :title="copy.home.items">
        <p class="multistack__error">{{ itemsErrorText(items.message) }}</p>
      </Panel>
      <template v-else-if="items.status === 'loaded'">
        <Panel v-for="item in items.items" :key="item.id" :title="item.name" test-id="item-row">
          <p class="text text--muted">{{ item.description }}</p>
        </Panel>
      </template>
    </div>
  </main>
</template>
