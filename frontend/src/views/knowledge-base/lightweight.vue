<script setup lang="ts">
import FilePreview from '@/components/custom/file-preview.vue';
import { request } from '@/service/request';

interface DocumentRow {
  id: string;
  fileName: string;
  md5: string;
  ownerId: string;
  isPublic: boolean;
  size: number;
  status: 'PROCESSING' | 'READY' | 'FAILED';
  createdAt: string;
  chunkCount: number;
}

const auth = useAuthStore();
const rows = ref<DocumentRow[]>([]);
const loading = ref(false);
const uploading = ref(false);
const shared = ref(false);
const fileInput = ref<HTMLInputElement>();
const preview = ref<DocumentRow>();
const previewVisible = ref(false);
let timer: number | undefined;
let disposed = false;

async function refresh() {
  if (loading.value || disposed) return;
  window.clearTimeout(timer);
  loading.value = true;
  try {
    const { error, data } = await request<DocumentRow[]>({ url: '/lightweight/documents' });
    if (!error && data && !disposed) rows.value = data;
  } finally {
    loading.value = false;
    if (!disposed && rows.value.some(row => row.status === 'PROCESSING')) {
      timer = window.setTimeout(refresh, 2500);
    }
  }
}

async function upload() {
  const file = fileInput.value?.files?.[0];
  if (!file || uploading.value) return;
  uploading.value = true;
  try {
    const body = new FormData();
    body.append('file', file);
    body.append('public', String(shared.value));
    const { error, data } = await request<DocumentRow>({
      url: '/lightweight/documents', method: 'POST', data: body
    });
    if (!error && data && !disposed) {
      rows.value = [data, ...rows.value.filter(row => row.id !== data.id)];
      if (fileInput.value) fileInput.value.value = '';
      await refresh();
    }
  } finally {
    uploading.value = false;
  }
}

async function remove(id: string) {
  const { error } = await request({ url: '/lightweight/documents/' + id, method: 'DELETE' });
  if (!error) {
    rows.value = rows.value.filter(row => row.id !== id);
    await refresh();
  }
}

function open(row: DocumentRow) {
  preview.value = row;
  previewVisible.value = true;
}

const labels = { PROCESSING: '处理中', READY: '可检索', FAILED: '处理失败' } as const;
onMounted(refresh);
onUnmounted(() => {
  disposed = true;
  window.clearTimeout(timer);
});
</script>

<template>
  <NCard title="知识库" size="small">
    <NSpace align="center" class="mb-4">
      <input ref="fileInput" type="file" accept=".pdf,.doc,.docx,.md,.txt" />
      <NCheckbox v-model:checked="shared">公开给其他已登录用户</NCheckbox>
      <NButton type="primary" :loading="uploading" @click="upload">上传</NButton>
      <NButton :loading="loading" @click="refresh">刷新</NButton>
    </NSpace>
    <NEmpty v-if="!rows.length && !loading" description="暂无文档" />
    <NTable v-else size="small">
      <thead><tr><th>文件名</th><th>权限</th><th>状态</th><th>分块</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="row in rows" :key="row.id">
          <td>{{ row.fileName }}</td>
          <td>{{ row.isPublic ? '公开' : '私有' }}</td>
          <td>{{ labels[row.status] }}</td>
          <td>{{ row.chunkCount }}</td>
          <td>
            <NSpace>
              <NButton size="tiny" :disabled="row.status !== 'READY'" @click="open(row)">预览</NButton>
              <NPopconfirm v-if="String(auth.userInfo.id) === row.ownerId && row.status !== 'PROCESSING'"
                @positive-click="remove(row.id)">
                <template #trigger><NButton size="tiny" type="error" quaternary>删除</NButton></template>
                删除此文档及其索引？
              </NPopconfirm>
            </NSpace>
          </td>
        </tr>
      </tbody>
    </NTable>
    <NModal v-model:show="previewVisible" class="document-preview-modal" :auto-focus="false">
      <FilePreview v-if="preview" :file-name="preview.fileName" :file-md5="preview.id"
        :visible="previewVisible" @close="previewVisible = false" />
    </NModal>
  </NCard>
</template>
