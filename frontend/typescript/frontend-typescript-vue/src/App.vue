<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import AppHeader from './components/AppHeader.vue';
import { useI18n } from './i18n';
import { appPath } from './lib/appBase';
import { buildHeaderConfig, syncHeaderNav } from './lib/headerConfig';

const { lang } = useI18n();
const headerConfig = computed(() => buildHeaderConfig(lang.value));
const navKey = ref<string | null>(null);

watch(
  headerConfig,
  (config) => {
    navKey.value = syncHeaderNav(config, navKey.value);
  },
  { immediate: true },
);
</script>

<template>
  <AppHeader :config="headerConfig" :script-src="appPath('/js/header.js')" />
  <RouterView />
</template>
