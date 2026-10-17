<script setup lang="ts">
import { onMounted, watch } from 'vue';
import type { HeaderConfig } from '../lib/headerConfig';

const props = withDefaults(
  defineProps<{
    config: HeaderConfig;
    scriptSrc?: string;
    mountId?: string;
  }>(),
  {
    scriptSrc: '/js/header.js',
    mountId: 'app-header',
  },
);

function publishConfig(config: HeaderConfig, scriptSrc: string): void {
  if (typeof window === 'undefined') {
    return;
  }
  window.headerConfig = config;

  if (!document.querySelector('script[data-header-embed]')) {
    const headerScript = document.createElement('script');
    headerScript.type = 'module';
    headerScript.src = scriptSrc;
    headerScript.dataset.headerEmbed = 'true';
    document.body.appendChild(headerScript);
  }
}

onMounted(() => {
  publishConfig(props.config, props.scriptSrc);
});

watch(
  () => [props.config, props.scriptSrc] as const,
  ([config, scriptSrc]) => {
    publishConfig(config, scriptSrc);
  },
);
</script>

<template>
  <div :id="mountId" data-testid="app-header-mount" />
</template>
