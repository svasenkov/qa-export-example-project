import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { afterEach, describe, expect, it, vi } from 'vitest';
const app = vi.hoisted(() => ({ use: vi.fn(), mount: vi.fn() }));
vi.mock('vue', async original => ({ ...await original<typeof import('vue')>(), createApp: vi.fn(() => app) }));
vi.mock('../pwa/registerServiceWorker', () => ({ registerServiceWorker: vi.fn() }));

afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });
it('bootstrap installs the real router, mounts the app and starts PWA registration', async () => {
 app.use.mockReturnValue(app);
 await import('../main');
 const { router } = await import('../router');
 const { registerServiceWorker } = await import('../pwa/registerServiceWorker');
 expect(app.use).toHaveBeenCalledWith(router);
 expect(app.mount).toHaveBeenCalledWith('#app');
 expect(registerServiceWorker).toHaveBeenCalledTimes(1);
 expect(router.resolve('/login').name).toBe('login');
 expect(router.resolve('/register').name).toBe('register');
 expect(router.resolve('/').name).toBe('home');
});

// index.html ships an inline IIFE (outside the v8 module graph): it pins
// <base> during parse because the relative Vite base breaks under nested SPA
// routes. The test executes the shipped source verbatim against stubbed
// locations — the matrix must match vendor/ds/js/app-base.js.
describe('index.html inline boot script', () => {
 const html = readFileSync(join(process.cwd(), 'index.html'), 'utf8');
 const match = html.match(/<script>([\s\S]*?)<\/script>/);
 const source = match?.[1];

 it('keeps the build-time pin placeholder for critical assets', () => {
  expect(source).toContain('__PIN_ASSETS__');
 });

 // Canonical /stack/{be}/{fe} URLs land on the container whose nginx strips
 // the legacy /{be}/{fe} prefix — the base must point at the served mount.
 it.each([
  ['/stack/backend-go-stdlib/frontend-typescript-vue/login', '/backend-go-stdlib/frontend-typescript-vue/'],
  ['/stack/frontend-typescript-vue/', '/frontend-typescript-vue/'],
  ['/', './'],
  ['/login', './'],
 ])('resolves %s into base %s', async (pathname, baseHref) => {
  expect(source).toBeTruthy();
  vi.stubGlobal('location', { pathname });
  const written: string[] = [];
  const writeSpy = vi.spyOn(document, 'write').mockImplementation((markup: string) => {
   written.push(markup);
  });
  new Function(source!)();
  writeSpy.mockRestore();
  expect(written).toEqual([`<base href="${baseHref}">`]);
 });
});
