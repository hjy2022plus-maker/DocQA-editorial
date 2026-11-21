<script setup lang="ts">
import { $t } from '@/locales';

defineOptions({
  name: 'Register'
});

const { toggleLoginModule } = useRouterPush();
const { formRef, validate } = useNaiveForm();

interface FormModel {
  username: string;
  password: string;
  confirmPassword: string;
}

const model: FormModel = reactive({
  username: '',
  password: '',
  confirmPassword: ''
});

const rules = computed<Record<keyof FormModel, App.Global.FormRule[]>>(() => {
  const { formRules, createConfirmPwdRule } = useFormRules();

  return {
    username: formRules.userName,
    password: formRules.pwd,
    confirmPassword: createConfirmPwdRule(model.password)
  };
});

const loading = ref(false);

async function handleSubmit() {
  await validate();
  loading.value = true;
  const { error } = await fetchRegister(model.username, model.password);
  if (!error) {
    window.$message?.success('注册成功');
    toggleLoginModule('pwd-login');
  }
  loading.value = false;
}
</script>

<template>
  <div class="register-layout">
    <!-- 邀请码运营说明已停用，保留原布局代码以便历史版本回溯。
    <aside class="invite-side-panel">
      <div class="invite-side-header">
        <div class="invite-side-kicker">邀请码获取</div>
        <div class="invite-side-title">文档智能问答系统</div>
        <div class="invite-side-desc">请向管理员获取注册邀请码，填写后即可完成注册。</div>
      </div>

      <div class="invite-side-bottom">
        <div class="invite-side-steps">
          <div class="invite-step-row">
            <span class="invite-channel-step">1</span>
            <span>向管理员申请注册邀请码</span>
          </div>
          <div class="invite-step-row">
            <span class="invite-channel-step">2</span>
            <span>在右侧填写用户名、密码和邀请码</span>
          </div>
          <div class="invite-step-row">
            <span class="invite-channel-step">3</span>
            <span>注册成功后返回登录页即可使用</span>
          </div>
        </div>
      </div>
    </aside> -->

    <NForm
      ref="formRef"
      :model="model"
      :rules="rules"
      size="large"
      :show-label="false"
      class="register-form-panel"
      @keyup.enter="handleSubmit"
    >
      <NFormItem path="username">
        <NInput v-model:value="model.username" :placeholder="$t('page.login.common.userNamePlaceholder')">
          <template #prefix>
            <icon-ant-design:user-outlined />
          </template>
        </NInput>
      </NFormItem>
      <NFormItem path="password">
        <NInput
          v-model:value="model.password"
          type="password"
          show-password-on="click"
          :placeholder="$t('page.login.common.passwordPlaceholder')"
        >
          <template #prefix>
            <icon-ant-design:key-outlined />
          </template>
        </NInput>
      </NFormItem>
      <NFormItem path="confirmPassword">
        <NInput
          v-model:value="model.confirmPassword"
          type="password"
          show-password-on="click"
          :placeholder="$t('page.login.common.confirmPasswordPlaceholder')"
        >
          <template #prefix>
            <icon-ant-design:key-outlined />
          </template>
        </NInput>
      </NFormItem>
      <NSpace vertical :size="18" class="w-full">
        <NButton type="primary" size="large" class="ed-cta w-full" :loading="loading" @click="handleSubmit">
          {{ $t('page.login.common.register') }}
        </NButton>
        <NButton block @click="toggleLoginModule('pwd-login')">
          {{ $t('page.login.common.back') }}
        </NButton>
      </NSpace>

      <div class="mt-4 text-center">
        {{ $t('page.login.register.agreement') }}
        <NButton text type="primary">{{ $t('page.login.register.protocol') }}</NButton>
        {{ $t('page.login.register.and') }}
        <NButton text type="primary">{{ $t('page.login.register.policy') }}</NButton>
      </div>
    </NForm>
  </div>
</template>

<style scoped>
.register-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 48px;
  align-items: stretch;
}

.register-form-panel {
  min-width: 0;
}

.register-form-tip {
  font-size: 12px;
  line-height: 1.7;
  color: rgb(var(--base-text-color) / 0.66);
}

.invite-side-panel {
  display: flex;
  min-width: 0;
  flex-direction: column;
  border-top: 1px solid var(--ed-hairline);
  padding-top: 24px;
}

.invite-side-kicker {
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--color-graphite);
}

.invite-side-title {
  margin-top: 16px;
  font-family: var(--ed-font-serif);
  font-size: 28px;
  font-weight: 400;
  line-height: 1.3;
  letter-spacing: -0.01em;
  color: var(--color-ink);
}

.invite-side-desc {
  margin-top: 16px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--color-graphite);
}

.invite-side-steps {
  margin-top: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.invite-step-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
  font-size: 13px;
  line-height: 1.7;
  color: rgb(var(--base-text-color) / 0.78);
}

.invite-side-bottom {
  margin-top: auto;
  padding-top: 18px;
}

.invite-channel-step {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 0;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.2em;
  color: var(--color-mauve);
}

@media (max-width: 900px) {
  .register-layout {
    grid-template-columns: 1fr;
    gap: 32px;
  }
}
</style>
