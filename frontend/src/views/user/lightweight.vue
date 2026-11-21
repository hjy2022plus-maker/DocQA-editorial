<script setup lang="ts">
import { request } from '@/service/request';

type Account = Pick<Api.Auth.UserInfo, 'id' | 'username' | 'role'>;
const accounts = ref<Account[]>([]);
const loading = ref(false);
const columns = [
  { title: '用户编号', key: 'id' },
  { title: '用户名', key: 'username' },
  { title: '角色', key: 'role' }
];

async function load() {
  loading.value = true;
  try {
    const { data, error } = await request<Account[]>({ url: '/admin/users' });
    if (!error && data) accounts.value = data;
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>

<template>
  <NCard title="用户与角色" size="small">
    <template #header-extra><NButton size="small" :loading="loading" @click="load">刷新</NButton></template>
    <NDataTable :columns="columns" :data="accounts" :row-key="row => row.id" :loading="loading" />
  </NCard>
</template>
