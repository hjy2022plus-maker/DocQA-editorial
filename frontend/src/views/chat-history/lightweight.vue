<script setup lang="ts">
const chat = useChatStore();
const { routerPushByKey } = useRouterPush();
onMounted(() => chat.loadSessions());

async function open(id: string) {
  await chat.switchSession(id);
  if (chat.conversationId === id) await routerPushByKey('chat');
}
</script>

<template>
  <NCard title="历史会话" size="small">
    <NSpin :show="chat.sessionsLoading">
      <NEmpty v-if="!chat.sessions.length" description="暂无会话" />
      <NList v-else>
        <NListItem v-for="session in chat.sessions" :key="session.conversationId">
          <NSpace justify="space-between">
            <NButton text @click="open(session.conversationId)">{{ session.title }}</NButton>
            <span>{{ session.status === 'ARCHIVED' ? '已归档' : '' }}</span>
          </NSpace>
        </NListItem>
      </NList>
    </NSpin>
  </NCard>
</template>
