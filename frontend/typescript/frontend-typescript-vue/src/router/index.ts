import { createRouter, createWebHistory } from 'vue-router';
import { APP_BASE } from '../lib/appBase';
import HomePage from '../pages/HomePage.vue';
import LoginPage from '../pages/LoginPage.vue';
import RegisterPage from '../pages/RegisterPage.vue';

/**
 * History mode under the product mount. createWebHistory(APP_BASE + '/') so
 * deep links like `/frontend-typescript-vue/login` resolve (nginx try_files → index.html).
 */
export const router = createRouter({
  history: createWebHistory(`${APP_BASE}/`),
  routes: [
    { path: '/', name: 'home', component: HomePage },
    { path: '/login', name: 'login', component: LoginPage },
    { path: '/register', name: 'register', component: RegisterPage },
  ],
});
