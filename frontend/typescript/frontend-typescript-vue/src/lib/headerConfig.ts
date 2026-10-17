import { envNavItems } from '../../vendor/ds/js/env-hosts.js';
import { dictionaries, type Lang } from '../i18n';
import { appPath } from './appBase';

export interface HeaderNavItem {
  href: string;
  label: string;
  active?: boolean;
  testid?: string;
  match?: 'path' | 'host';
}

/** Minimal header contract matching design-system `window.headerConfig`. */
export interface HeaderConfig {
  brand: { href: string; label: string };
  nav: HeaderNavItem[];
  lang: { default: string };
  theme: { default: string };
}

/** Stack matrix board — origin `/stack/`, not `/{pair}/stack`. */
export const STACK_INDEX_HREF = '/stack/';

function navLabelsKey(config: Pick<HeaderConfig, 'nav'> | HeaderConfig): string {
  return (config.nav ?? []).map((item) => item.label).join('\0');
}

/**
 * Canonical header config for the Multistack SPA. Nav hrefs are mount-prefixed
 * so design-system `header.js` matches the live route under
 * `/stack/{backend}/{frontend}/`. Omit `active` — header.js derives it from location.
 * Stage/Prod come from `js/env-hosts.js` (current product host; matrix `public_host` on loopback).
 * Nav *labels* follow the SPA dictionary; testids and hrefs stay stable.
 */
export function buildHeaderConfig(lang: Lang = 'en'): HeaderConfig {
  const nav = dictionaries[lang].nav;
  return {
    brand: { href: appPath('/'), label: 'Multistack' },
    nav: [
      { href: appPath('/'), label: nav.home, testid: 'header-nav-home' },
      { href: appPath('/login'), label: nav.login, testid: 'header-nav-login' },
      { href: appPath('/register'), label: nav.register, testid: 'header-nav-register' },
      { href: STACK_INDEX_HREF, label: nav.stack, testid: 'header-nav-stack' },
      ...envNavItems(),
    ],
    lang: { default: 'en' },
    theme: { default: 'dark' },
  };
}

/**
 * Publish nav labels and remount the canonical header **once** when they change.
 * Theme stays in header.js — this only retitles nav after `header:lang-change`.
 *
 * Compare against `previousKey` (caller ref), not `window.headerConfig`:
 * AppHeader writes the new config in a child watch first, which would
 * otherwise hide the label change and skip remount.
 */
export function syncHeaderNav(config: HeaderConfig, previousKey: string | null): string {
  window.headerConfig = config;
  const next = navLabelsKey(config);
  if (previousKey !== null && previousKey !== next) {
    void window.__designSystemRemountHeader?.();
  }
  return next;
}

/** English snapshot — default lang, used by tests that do not switch language. */
export const headerConfig: HeaderConfig = buildHeaderConfig('en');
