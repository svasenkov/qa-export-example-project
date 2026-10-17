import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  applyDocumentLang,
  dictionaries,
  en,
  HEADER_LANG_CHANGE,
  isLang,
  LANG_STORAGE_KEY,
  langFromDetail,
  readStoredLang,
  ru,
} from '../../i18n';

function leafKeys(obj: unknown, prefix = ''): string[] {
  return Object.entries(obj as Record<string, unknown>).flatMap(([key, value]) =>
    value && typeof value === 'object' ? leafKeys(value, `${prefix}${key}.`) : [`${prefix}${key}`],
  );
}

describe('i18n helpers', () => {
  afterEach(() => {
    localStorage.clear();
    document.documentElement.lang = 'en';
    document.documentElement.classList.remove('theme-light');
  });

  it('defaults to en and only accepts en|ru', () => {
    expect(readStoredLang()).toBe('en');
    expect(isLang('en')).toBe(true);
    expect(isLang('ru')).toBe(true);
    expect(isLang('de')).toBe(false);
    expect(isLang(null)).toBe(false);
    expect(langFromDetail('ru')).toBe('ru');
    expect(langFromDetail('en')).toBe('en');
    expect(langFromDetail('de')).toBe('en');
    expect(langFromDetail(undefined)).toBe('en');
  });

  it('reads zds-lang from storage (persist)', () => {
    localStorage.setItem(LANG_STORAGE_KEY, 'ru');
    expect(readStoredLang()).toBe('ru');
    localStorage.setItem(LANG_STORAGE_KEY, 'fr');
    expect(readStoredLang()).toBe('en');
  });

  it('falls back to en when localStorage throws', () => {
    const getItem = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('storage disabled');
    });
    expect(readStoredLang()).toBe('en');
    getItem.mockRestore();
  });

  it('applies html[lang] and keeps the event name aligned with header.js', () => {
    applyDocumentLang('ru');
    expect(document.documentElement.lang).toBe('ru');
    applyDocumentLang('en');
    expect(document.documentElement.lang).toBe('en');
    expect(HEADER_LANG_CHANGE).toBe('header:lang-change');
  });

  it('keeps theme-light on html when applying lang (theme stays in header.js)', () => {
    document.documentElement.classList.add('theme-light');
    applyDocumentLang('ru');
    expect(document.documentElement.classList.contains('theme-light')).toBe(true);
    expect(document.documentElement.lang).toBe('ru');
  });

  it('keeps en/ru dictionaries 1:1 and a Vue-specific home.blurb', () => {
    expect(leafKeys(en)).toEqual(leafKeys(ru));
    expect(leafKeys(dictionaries.en)).toEqual(leafKeys(dictionaries.ru));
    expect(en.home.blurb).toBe('TypeScript Vue SPA — items loaded from {api}.');
    expect(ru.home.blurb).toBe('SPA TypeScript Vue — элементы из {api}.');
    expect(en.home.blurb).not.toMatch(/React SPA|Vanilla|Angular|jQuery/);
    expect(ru.home.blurb).not.toMatch(/React|Vanilla|Angular|jQuery/);
  });
});
