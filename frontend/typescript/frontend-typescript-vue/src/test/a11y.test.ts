import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, waitFor } from '@testing-library/vue';
import { createMemoryHistory, createRouter } from 'vue-router';
import { run, type AxeResults } from 'axe-core';
import HomePage from '../pages/HomePage.vue';
import LoginPage from '../pages/LoginPage.vue';
import RegisterPage from '../pages/RegisterPage.vue';

// jsdom has no layout engine, so color-contrast cannot be measured here; that
// rule is covered by the browser-level axe audit on the mock stand.
const AXE_OPTIONS = {
  rules: { 'color-contrast': { enabled: false } },
} as Parameters<typeof run>[1];

function jsonResponse(body: unknown): Response {
  return { ok: true, status: 200, json: async () => body } as Response;
}

function stubApis() {
  vi.stubGlobal(
    'fetch',
    vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/health')) {
        return Promise.resolve(
          jsonResponse({ status: 'ok', service: 'backend-go-stdlib' }),
        );
      }
      if (url.includes('/api/items')) {
        return Promise.resolve(
          jsonResponse({ items: [{ id: 1, name: 'Alpha', description: 'First item' }] }),
        );
      }
      return Promise.reject(new Error(`unstubbed fetch ${url}`));
    }),
  );
}

async function renderAt(path: string, component: object) {
  const stub = { template: '<div />' };
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: path === '/' ? (component as never) : stub },
      { path: '/login', component: path === '/login' ? (component as never) : stub },
      { path: '/register', component: path === '/register' ? (component as never) : stub },
    ],
  });
  await router.push(path);
  await router.isReady();
  return render(component as never, { global: { plugins: [router] } });
}

function expectNoViolations(results: AxeResults) {
  const summary = results.violations
    .map((v) => `${v.id}: ${v.nodes.length} node(s) — ${v.help}`)
    .join('\n');
  expect(results.violations, summary).toEqual([]);
}

describe('a11y component lint (axe-core, jsdom subset)', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('home page render has no axe violations', async () => {
    stubApis();
    const view = await renderAt('/', HomePage);
    await waitFor(() => expect(view.container.textContent).toContain('Alpha'));

    expectNoViolations(await run(view.container as HTMLElement, AXE_OPTIONS));
    view.unmount();
  });

  it('login page render has no axe violations', async () => {
    const view = await renderAt('/login', LoginPage);
    await waitFor(() => expect(view.container.querySelector('form')).toBeTruthy());

    expectNoViolations(await run(view.container as HTMLElement, AXE_OPTIONS));
    view.unmount();
  });

  it('register page render has no axe violations', async () => {
    const view = await renderAt('/register', RegisterPage);
    await waitFor(() => expect(view.container.querySelector('form')).toBeTruthy());

    expectNoViolations(await run(view.container as HTMLElement, AXE_OPTIONS));
    view.unmount();
  });
});
