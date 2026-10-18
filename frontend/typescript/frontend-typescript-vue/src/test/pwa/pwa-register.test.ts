import { afterEach, describe, expect, it, vi } from 'vitest';
import { registerServiceWorker } from '../../pwa/pwa-register.js';

describe('pwa-register controllerchange reload', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  function stubSw(controller: ServiceWorker | null) {
    const listeners = new Map<string, EventListener>();
    const register = vi.fn().mockResolvedValue({ update: vi.fn().mockResolvedValue(undefined) });
    const addEventListener = vi.fn((type: string, handler: EventListener) => {
      listeners.set(type, handler);
    });
    const reload = vi.fn();
    vi.stubGlobal('navigator', {
      serviceWorker: { controller, register, addEventListener },
    });
    vi.stubGlobal('location', { reload });
    return {
      emit(type: string) {
        listeners.get(type)?.(new Event(type));
      },
      reload,
      register,
    };
  }

  it('does not reload on first claim (no prior controller)', () => {
    const sw = stubSw(null);
    registerServiceWorker({ swUrl: '/sw.js' });
    sw.emit('controllerchange');
    expect(sw.reload).not.toHaveBeenCalled();
  });

  it('reloads when an existing controller is replaced', () => {
    const sw = stubSw({} as ServiceWorker);
    registerServiceWorker({ swUrl: '/sw.js' });
    sw.emit('controllerchange');
    expect(sw.reload).toHaveBeenCalledTimes(1);
  });

  it('reloads at most once per registration', () => {
    const sw = stubSw({} as ServiceWorker);
    registerServiceWorker({ swUrl: '/sw.js' });
    sw.emit('controllerchange');
    sw.emit('controllerchange');
    expect(sw.reload).toHaveBeenCalledTimes(1);
  });
});

describe('PWA registration failure and lifecycle', () => {
 afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });
 it('reports registration failure to the callback', async () => {
  const error = new Error('offline');
  const callback = vi.fn(); const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
  vi.stubGlobal('navigator', { serviceWorker: { register: vi.fn().mockRejectedValue(error) } });
  registerServiceWorker({ reloadOnControllerChange: false, onRegisterError: callback });
  await vi.waitFor(() => expect(callback).toHaveBeenCalledWith(error));
  expect(warn).toHaveBeenCalledWith('service worker registration failed', error);
 });
 it('still resolves quietly when registration fails without a callback', async () => {
  const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
  vi.stubGlobal('navigator', { serviceWorker: { register: vi.fn().mockRejectedValue(new Error('gone')) } });
  registerServiceWorker({ reloadOnControllerChange: false });
  await vi.waitFor(() => expect(warn).toHaveBeenCalledWith('service worker registration failed', expect.any(Error)));
 });
 it('delays registration until load and reports registration without update support', async () => {
  const registration = {}; const register = vi.fn().mockResolvedValue(registration); const callback = vi.fn();
  const listener = vi.spyOn(window, 'addEventListener');
  vi.stubGlobal('navigator', { serviceWorker: { register } });
  registerServiceWorker({ immediate: false, reloadOnControllerChange: false, onRegistered: callback });
  expect(register).not.toHaveBeenCalled();
  const load = listener.mock.calls.find(([name]) => name === 'load')![1] as EventListener;
  load(new Event('load'));
  await vi.waitFor(() => expect(callback).toHaveBeenCalledWith(registration));
  window.removeEventListener('load', load);
 });
 it('ignores update failure after successful registration', async () => {
  const callback = vi.fn(); const onError = vi.fn();
  const update = vi.fn().mockRejectedValue(new Error('offline'));
  vi.stubGlobal('navigator', { serviceWorker: { register: vi.fn().mockResolvedValue({ update }) } });
  registerServiceWorker({ reloadOnControllerChange: false, onRegistered: callback, onRegisterError: onError });
  await vi.waitFor(() => expect(callback).toHaveBeenCalled()); expect(onError).not.toHaveBeenCalled();
 });
 it('is inert without navigator', () => {
  vi.stubGlobal('navigator', undefined); expect(() => registerServiceWorker()).not.toThrow();
 });
});
