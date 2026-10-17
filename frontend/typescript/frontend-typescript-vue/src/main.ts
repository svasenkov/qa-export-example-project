import { createApp } from 'vue';
import App from './App.vue';
import { router } from './router';
import { registerServiceWorker } from './pwa/registerServiceWorker';
import './styles';

createApp(App).use(router).mount('#app');

registerServiceWorker();
