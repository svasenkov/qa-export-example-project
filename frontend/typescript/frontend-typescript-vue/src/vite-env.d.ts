/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue';
  const component: DefineComponent<object, object, unknown>;
  export default component;
}

import type { HeaderConfig } from './lib/headerConfig';

declare global {
  interface Window {
    headerConfig?: HeaderConfig;
    __designSystemRemountHeader?: () => void | Promise<void>;
  }
}

export {};
