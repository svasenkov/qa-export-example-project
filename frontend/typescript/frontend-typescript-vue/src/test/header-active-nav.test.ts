import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { envNavItems } from '../../vendor/ds/js/env-hosts.js';

// Canonical design-system header from lean runtime (committed; works in
// standalone checkout without monorepo design-system symlink).
const { HEADER_JS, TEMPLATE_HTML } = vi.hoisted(() => {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const { readFileSync: read } = require('node:fs') as typeof import('node:fs');
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const { resolve: res } = require('node:path') as typeof import('node:path');
  const runtime = res(__dirname, '../../vendor/ds');
  return {
    HEADER_JS: res(runtime, 'js/header.js'),
    TEMPLATE_HTML: read(res(runtime, 'templates/header.html'), 'utf8'),
  };
});

// String literal required — vi.mock is hoisted.
vi.mock('../../vendor/ds/js/dom-utils.js', () => ({
  fetchTemplateText: vi.fn(async () => TEMPLATE_HTML),
  escapeHtml: (v: string) => v,
  copyToClipboard: vi.fn(),
}));

const MOUNT = '/frontend-typescript-vue';

const REFERENCE_HEADER_CONFIG = {
  brand: { href: `${MOUNT}/`, label: 'Multistack' },
  nav: [
    { href: `${MOUNT}/`, label: 'Home', active: false, testid: 'header-nav-home' },
    { href: `${MOUNT}/login`, label: 'Login', active: false, testid: 'header-nav-login' },
    {
      href: `${MOUNT}/register`,
      label: 'Register',
      active: false,
      testid: 'header-nav-register',
    },
    {
      href: '/stack/',
      label: 'Stack',
      active: false,
      testid: 'header-nav-stack',
    },
    ...envNavItems(),
  ],
  lang: { default: 'en' as const },
  theme: { default: 'dark' as const },
};

function navLinks(): HTMLAnchorElement[] {
  return Array.from(
    document.querySelectorAll<HTMLAnchorElement>('[data-testid="header-nav"] a'),
  );
}

function menuNavLinks(): HTMLAnchorElement[] {
  return Array.from(
    document.querySelectorAll<HTMLAnchorElement>('[data-testid="header-menu-nav"] a'),
  );
}

function activeTestids(): (string | undefined)[] {
  return navLinks()
    .filter((a) => a.classList.contains('is-active'))
    .map((a) => a.dataset.testid);
}

function activeMenuTestids(): (string | undefined)[] {
  return menuNavLinks()
    .filter((a) => a.classList.contains('is-active'))
    .map((a) => a.dataset.testid);
}

function ariaCurrentTestids(): (string | undefined)[] {
  return navLinks()
    .filter((a) => a.getAttribute('aria-current') === 'page')
    .map((a) => a.dataset.testid);
}

async function mountAt(path: string): Promise<void> {
  window.history.replaceState({}, '', path);
  document.body.innerHTML = '<div id="app-header"></div>';
  (window as unknown as { headerConfig: unknown }).headerConfig =
    structuredClone(REFERENCE_HEADER_CONFIG);
  vi.resetModules();
  await import(/* @vite-ignore */ HEADER_JS);
  await vi.waitFor(() => {
    expect(navLinks().length).toBe(6);
    expect(menuNavLinks().length).toBe(6);
  });
}

function mockMobileViewport(): void {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    configurable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: query.includes('max-width: 767px'),
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

describe('canonical header.js — active nav follows the route', () => {
  beforeEach(() => {
    window.history.replaceState({}, '', `${MOUNT}/`);
    document.documentElement.className = '';
    mockMobileViewport();
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('highlights Login on a direct /login load', async () => {
    await mountAt(`${MOUNT}/login`);
    expect(activeTestids()).toEqual(['header-nav-login']);
    expect(ariaCurrentTestids()).toEqual(['header-nav-login']);
  });

  it('highlights Register on a direct /register load', async () => {
    await mountAt(`${MOUNT}/register`);
    expect(activeTestids()).toEqual(['header-nav-register']);
    expect(ariaCurrentTestids()).toEqual(['header-nav-register']);
  });

  it('highlights Home on mount root', async () => {
    await mountAt(`${MOUNT}/`);
    expect(activeTestids()).toEqual(['header-nav-home']);
    expect(ariaCurrentTestids()).toEqual(['header-nav-home']);
  });

  it('re-syncs on SPA pushState (in-form Register → Login link)', async () => {
    await mountAt(`${MOUNT}/register`);
    expect(activeTestids()).toEqual(['header-nav-register']);

    window.history.pushState({}, '', `${MOUNT}/login`);

    await vi.waitFor(() => {
      expect(activeTestids()).toEqual(['header-nav-login']);
    });
    expect(ariaCurrentTestids()).toEqual(['header-nav-login']);
  });

  it('re-syncs on popstate (browser back/forward)', async () => {
    await mountAt(`${MOUNT}/login`);
    window.history.replaceState({}, '', `${MOUNT}/register`);
    window.dispatchEvent(new PopStateEvent('popstate'));

    await vi.waitFor(() => {
      expect(activeTestids()).toEqual(['header-nav-register']);
    });
    expect(ariaCurrentTestids()).toEqual(['header-nav-register']);
  });

  it('keeps aria-current on exactly one item after navigation', async () => {
    await mountAt(`${MOUNT}/`);
    window.history.pushState({}, '', `${MOUNT}/register`);
    await vi.waitFor(() => {
      expect(ariaCurrentTestids()).toEqual(['header-nav-register']);
    });
  });
});

describe('canonical header.js — mobile burger menu', () => {
  beforeEach(() => {
    window.history.replaceState({}, '', `${MOUNT}/`);
    document.documentElement.className = '';
    mockMobileViewport();
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('builds menu with nav, search, and github links', async () => {
    await mountAt(`${MOUNT}/`);

    expect(document.querySelector('[data-testid="header-menu"]')).not.toBeNull();
    expect(menuNavLinks().map((a) => a.dataset.testid)).toEqual([
      'header-menu-nav-home',
      'header-menu-nav-login',
      'header-menu-nav-register',
      'header-menu-nav-stack',
      'header-menu-nav-stage',
      'header-menu-nav-prod',
    ]);
    expect(
      document.querySelector('[data-testid="header-menu-search-input"]'),
    ).not.toBeNull();
    expect(
      document.querySelector('[data-testid="header-menu-github"]'),
    ).not.toBeNull();
    expect(
      document.querySelector('[data-testid="header-menu-github-pages"]'),
    ).not.toBeNull();
  });

  it('toggles menu visibility via burger button', async () => {
    await mountAt(`${MOUNT}/`);

    const burger = document.querySelector<HTMLButtonElement>(
      '[data-testid="header-burger"]',
    );
    const menu = document.querySelector<HTMLElement>('[data-testid="header-menu"]');
    expect(burger).not.toBeNull();
    expect(menu).not.toBeNull();
    expect(menu?.hidden).toBe(true);
    expect(burger?.getAttribute('aria-expanded')).toBe('false');

    burger?.click();
    expect(menu?.hidden).toBe(false);
    expect(burger?.getAttribute('aria-expanded')).toBe('true');

    burger?.click();
    expect(menu?.hidden).toBe(true);
    expect(burger?.getAttribute('aria-expanded')).toBe('false');
  });

  it('highlights the active route in menu nav links', async () => {
    await mountAt(`${MOUNT}/login`);
    expect(activeMenuTestids()).toEqual(['header-menu-nav-login']);
  });

  it('closes menu when a menu nav link is clicked', async () => {
    await mountAt(`${MOUNT}/`);

    const burger = document.querySelector<HTMLButtonElement>(
      '[data-testid="header-burger"]',
    );
    const menu = document.querySelector<HTMLElement>('[data-testid="header-menu"]');
    burger?.click();
    expect(menu?.hidden).toBe(false);

    menuNavLinks()[0]?.click();
    expect(menu?.hidden).toBe(true);
  });
});

describe('canonical header.js — lang and theme persist', () => {
  beforeEach(() => {
    window.history.replaceState({}, '', `${MOUNT}/`);
    document.documentElement.className = '';
    localStorage.clear();
    mockMobileViewport();
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('toggles html.theme-light and keeps it after remount', async () => {
    await mountAt(`${MOUNT}/`);
    const themeBtn = document.querySelector<HTMLButtonElement>(
      '[data-testid="header-theme-toggle"]',
    );
    expect(document.documentElement.classList.contains('theme-light')).toBe(false);

    themeBtn?.click();
    expect(document.documentElement.classList.contains('theme-light')).toBe(true);
    expect(localStorage.getItem('zds-theme')).toBe('light');

    await mountAt(`${MOUNT}/login`);
    expect(document.documentElement.classList.contains('theme-light')).toBe(true);
    expect(localStorage.getItem('zds-theme')).toBe('light');
  });

  it('toggles html[lang=ru] and keeps it after remount', async () => {
    await mountAt(`${MOUNT}/`);
    const langBtn = document.querySelector<HTMLButtonElement>(
      '[data-testid="header-lang-toggle"]',
    );
    expect(document.documentElement.lang).toBe('en');

    langBtn?.click();
    expect(document.documentElement.lang).toBe('ru');
    expect(localStorage.getItem('zds-lang')).toBe('ru');
    expect(document.querySelector('[data-testid="header-lang-label"]')?.textContent).toBe('RU');

    await mountAt(`${MOUNT}/`);
    expect(document.documentElement.lang).toBe('ru');
    expect(document.querySelector('[data-testid="header-lang-label"]')?.textContent).toBe('RU');
  });
});
