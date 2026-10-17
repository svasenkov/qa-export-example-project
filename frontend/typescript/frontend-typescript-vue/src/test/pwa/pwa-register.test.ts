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
