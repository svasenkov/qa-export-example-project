import { afterEach, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import Panel from '../../components/Panel.vue';
import PlaqueField from '../../components/PlaqueField.vue';
import AppHeader from '../../components/AppHeader.vue';
import { buildHeaderConfig } from '../../lib/headerConfig';

afterEach(() => { document.querySelectorAll('script[data-header-embed]').forEach(el => el.remove()); vi.unstubAllGlobals(); });
it.each([undefined, ''])('Panel omits an absent title (%s), preserving content', title => {
 const wrapper = mount(Panel, { props: { title, hidden: true }, slots: { default: 'content' } });
 expect(wrapper.find('.panel__title').exists()).toBe(false);
 expect(wrapper.text()).toBe('content');
 expect(wrapper.attributes()).toHaveProperty('hidden');
 wrapper.unmount();
});
it('PlaqueField preserves autofill fallback and emits the entered value without divider', async () => {
 const wrapper = mount(PlaqueField, { props: { label: 'Username', id: 'username', labelVariant: 'param', divided: false, stretch: false } });
 expect(wrapper.find('input').attributes('name')).toBe('username');
 expect(wrapper.find('.plaque-divider').exists()).toBe(false);
 expect(wrapper.find('.plaque-field__label').attributes('title')).toBe('Username');
 await wrapper.find('input').setValue('alice');
 expect(wrapper.emitted('update:modelValue')).toEqual([['alice']]);
 wrapper.unmount();
});
it('AppHeader publishes updated labels without inserting a second script', async () => {
 const wrapper = mount(AppHeader, { props: { config: buildHeaderConfig() } });
 expect(document.querySelectorAll('script[data-header-embed]')).toHaveLength(1);
 await wrapper.setProps({ config: buildHeaderConfig('ru') });
 expect(window.headerConfig?.nav[0].label).toBe(buildHeaderConfig('ru').nav[0].label);
 expect(document.querySelectorAll('script[data-header-embed]')).toHaveLength(1);
 wrapper.unmount();
});
it('AppHeader does no DOM work when window is unavailable (SSR guard)', async () => {
 const wrapper = mount(AppHeader, { props: { config: buildHeaderConfig() } });
 // A prop change re-runs publishConfig: with no window it must bail out before
 // touching the DOM, so the removed embed script must not come back.
 document.querySelector('script[data-header-embed]')?.remove();
 vi.stubGlobal('window', undefined);
 await wrapper.setProps({ config: buildHeaderConfig('ru') });
 expect(document.querySelector('script[data-header-embed]')).toBeNull();
 wrapper.unmount();
});
