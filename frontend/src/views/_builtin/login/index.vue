<script setup lang="ts">
import { computed } from 'vue';
import type { Component } from 'vue';
import { loginModuleRecord } from '@/constants/app';
import { useAppStore } from '@/store/modules/app';
import { useThemeStore } from '@/store/modules/theme';
import { $t } from '@/locales';
import PwdLogin from './modules/pwd-login.vue';
import CodeLogin from './modules/code-login.vue';
import Register from './modules/register.vue';
import ResetPwd from './modules/reset-pwd.vue';
import BindWechat from './modules/bind-wechat.vue';

interface Props {
  /** The login module */
  module?: UnionKey.LoginModule;
}

const props = defineProps<Props>();

const appStore = useAppStore();
const themeStore = useThemeStore();

interface LoginModule {
  label: string;
  component: Component;
}

const moduleMap: Record<UnionKey.LoginModule, LoginModule> = {
  'pwd-login': { label: loginModuleRecord['pwd-login'], component: PwdLogin },
  'code-login': { label: loginModuleRecord['code-login'], component: CodeLogin },
  register: { label: loginModuleRecord.register, component: Register },
  'reset-pwd': { label: loginModuleRecord['reset-pwd'], component: ResetPwd },
  'bind-wechat': { label: loginModuleRecord['bind-wechat'], component: BindWechat }
};

const activeModule = computed(() => moduleMap[props.module || 'pwd-login']);
const isRegisterModule = computed(() => (props.module || 'pwd-login') === 'register');
</script>

<template>
  <div class="login-page" :class="{ 'login-page--register': isRegisterModule }">
    <header class="login-masthead">
      <div class="login-masthead__top">
        <span class="ed-label">PAISMART · ENTERPRISE KNOWLEDGE</span>
        <ThemeSchemaSwitch
          :theme-schema="themeStore.themeScheme"
          :show-tooltip="false"
          class="text-18px"
          @switch="themeStore.toggleThemeScheme"
        />
      </div>
      <h1 class="login-title">{{ $t('system.title') }}</h1>
      <p class="login-lede">企业文档的检索、溯源与问答：把散落在合同、研报与手册里的知识，整理成一段可以追问的阅读。</p>
      <blockquote class="ed-quote">每一段回答，都回到原文。</blockquote>
      <div class="login-meta">
        <span>企业知识库</span>
        <span>检索增强</span>
        <span>出处可追溯</span>
      </div>
    </header>

    <section class="login-panel">
      <div class="login-panel__head">
        <span class="ed-label ed-label--plain">ACCOUNT ACCESS</span>
        <LangSwitch
          v-if="themeStore.header.multilingual.visible"
          :lang="appStore.locale"
          :lang-options="appStore.localeOptions"
          :show-tooltip="false"
          @change-lang="appStore.changeLocale"
        />
      </div>
      <h2 class="login-panel__title">{{ $t(activeModule.label) }}</h2>
      <Transition :name="themeStore.page.animateMode" mode="out-in" appear>
        <component :is="activeModule.component" />
      </Transition>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(360px, 460px);
  align-items: center;
  gap: 96px;
  max-width: var(--page-max-width);
  min-height: 100%;
  margin: 0 auto;
  padding: 96px 48px;
  background: var(--color-paper);
  color: var(--color-ink);
}

.login-page--register {
  grid-template-columns: 1fr;
  gap: 48px;
  max-width: 960px;
  padding: 64px 24px;
}

.login-masthead__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.login-title {
  margin: 32px 0 24px;
  font-family: var(--ed-font-serif);
  font-size: clamp(48px, 7vw, 72px);
  font-weight: 400;
  line-height: 1.05;
  letter-spacing: -0.02em;
}

.login-lede {
  max-width: 34em;
  margin: 0;
  font-family: var(--ed-font-sans);
  font-size: 16px;
  line-height: 1.7;
  color: var(--color-graphite);
}

.login-masthead .ed-quote {
  max-width: 30em;
  margin: 48px 0 0;
}

.login-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  margin-top: 48px;
  font-family: var(--ed-font-sans);
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--color-graphite);
}

.login-meta span + span {
  padding-left: 24px;
  border-left: 1px solid var(--ed-hairline);
}

.login-panel {
  width: 100%;
  border-top: 1px solid var(--color-ink);
  padding-top: 24px;
}

.login-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 24px;
}

.login-panel__title {
  margin: 24px 0 32px;
  font-family: var(--ed-font-serif);
  font-size: 36px;
  font-weight: 400;
  line-height: 1.2;
  letter-spacing: -0.02em;
}

@media (max-width: 1024px) {
  .login-page {
    grid-template-columns: 1fr;
    gap: 48px;
    padding: 64px 24px;
  }

  .login-masthead .ed-quote {
    display: none;
  }

  .login-meta {
    margin-top: 32px;
  }
}

@media (max-width: 640px) {
  .login-page {
    padding: 40px 20px;
  }

  .login-title {
    margin: 24px 0 16px;
    font-size: 44px;
  }

  .login-panel__title {
    margin-bottom: 24px;
    font-size: 28px;
  }
}
</style>
