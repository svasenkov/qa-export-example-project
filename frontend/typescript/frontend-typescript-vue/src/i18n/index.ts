import { computed, onMounted, onUnmounted, ref, watch, type ComputedRef, type Ref } from 'vue';
import { en } from './en';
import { ru } from './ru';
import type { Dictionary, Lang } from './types';

/** Mirror of `HEADER_LANG_CHANGE` in design-system `js/header.js`. */
export const HEADER_LANG_CHANGE = 'header:lang-change';
/** Mirror of `LANG_STORAGE_KEY` in design-system `js/header.js`. */
export const LANG_STORAGE_KEY = 'zds-lang';

export const dictionaries: Record<Lang, Dictionary> = { en, ru };

export { en } from './en';
export { ru } from './ru';
export type { Dictionary, Lang } from './types';

export function isLang(value: string | null | undefined): value is Lang {
  return value === 'en' || value === 'ru';
}

export function langFromDetail(lang: string | null | undefined): Lang {
  return lang === 'ru' ? 'ru' : 'en';
}

export function readStoredLang(): Lang {
  try {
    const stored = localStorage.getItem(LANG_STORAGE_KEY);
    if (isLang(stored)) {
      return stored;
    }
  } catch {
    // private mode / blocked storage
  }
  return 'en';
}

export function applyDocumentLang(lang: Lang): void {
  document.documentElement.lang = lang;
}

/**
 * Analog of React `useI18n()`: per-component refs + `header:lang-change`.
 * Theme stays in header.js — this only retitles copy after the lang event.
 */
export function useI18n(): { lang: Ref<Lang>; copy: ComputedRef<Dictionary> } {
  const lang = ref<Lang>(readStoredLang());

  watch(
    lang,
    (value) => {
      applyDocumentLang(value);
    },
    { immediate: true },
  );

  const onLang = (event: Event) => {
    lang.value = langFromDetail((event as CustomEvent<{ lang?: string }>).detail?.lang);
  };

  onMounted(() => {
    document.addEventListener(HEADER_LANG_CHANGE, onLang);
  });
  onUnmounted(() => {
    document.removeEventListener(HEADER_LANG_CHANGE, onLang);
  });

  return {
    lang,
    copy: computed(() => dictionaries[lang.value]),
  };
}
