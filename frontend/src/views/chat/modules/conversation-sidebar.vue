<script setup lang="ts">
defineOptions({
  name: 'ConversationSidebar'
});

const collapsed = defineModel<boolean>('collapsed', { default: false });

const chatStore = useChatStore();
const { conversationId, sessionsLoading, filteredSessions, activeTab } = storeToRefs(chatStore);

onMounted(() => {
  chatStore.loadSessions();
});

function handleCollapse() {
  collapsed.value = true;
}

function handleNewChat() {
  chatStore.createNewSession();
}

function handleSelect(cid: string) {
  chatStore.switchSession(cid);
}

function handleArchive(cid: string) {
  chatStore.archiveSession(cid);
}

function handleUnarchive(cid: string) {
  chatStore.unarchiveSession(cid);
}

function setActiveTab(tab: 'active' | 'archived') {
  activeTab.value = tab;
}

function formatDate(dateStr?: string) {
  if (!dateStr) return '';
  const date = dayjs(dateStr);
  const now = dayjs();
  if (date.isSame(now, 'day')) {
    return date.format('HH:mm');
  }
  if (date.isSame(now, 'week')) {
    return date.format('ddd');
  }
  if (date.isSame(now, 'year')) {
    return date.format('MM-DD');
  }
  return date.format('YYYY-MM-DD');
}
</script>

<template>
  <div
    class="relative h-full flex flex-col shrink-0 overflow-hidden border-r border-[color:var(--ed-hairline)] transition-[width] duration-200 ease-out"
    :class="collapsed ? 'w-0 min-w-0 border-r-0' : 'w-[260px] min-w-[260px]'"
  >
    <div class="w-[260px] flex flex-col flex-1 overflow-hidden" :class="{ 'pointer-events-none invisible': collapsed }">
      <div class="flex items-center justify-between px-4 pb-3 pt-4">
        <span class="sidebar-title">对话列表</span>
        <div class="flex items-center gap-1">
          <NButton type="primary" size="small" @click="handleNewChat">
            <template #icon>
              <icon-material-symbols:add-rounded />
            </template>
            新对话
          </NButton>
          <NButton text size="tiny" @click="handleCollapse">
            <template #icon>
              <icon-material-symbols:left-panel-close-outline-rounded />
            </template>
          </NButton>
        </div>
      </div>

      <!-- Tabs -->
      <div class="sidebar-tabs mx-4 mb-2 flex">
        <button
          type="button"
          class="sidebar-tab"
          :class="{ 'sidebar-tab--active': activeTab === 'active' }"
          @click="setActiveTab('active')"
        >
          活跃
        </button>
        <button
          type="button"
          class="sidebar-tab"
          :class="{ 'sidebar-tab--active': activeTab === 'archived' }"
          @click="setActiveTab('archived')"
        >
          已归档
        </button>
      </div>

      <!-- List -->
      <div class="flex-1 overflow-y-auto px-2">
        <NSpin :show="sessionsLoading" class="h-full">
          <TransitionGroup name="session-list" tag="div">
            <div
              v-if="filteredSessions.length === 0 && !sessionsLoading"
              class="flex flex-col items-center justify-center gap-3 py-16"
            >
              <icon-material-symbols:chat-outline-rounded class="text-36px color-[var(--color-graphite)]" />
              <span class="text-13px color-[var(--color-graphite)]">
                {{ activeTab === 'active' ? '暂无对话记录' : '暂无归档对话' }}
              </span>
            </div>

            <div
              v-for="session in filteredSessions"
              :key="session.conversationId"
              class="session-item group mx-1 mb-0.5 flex cursor-pointer items-center gap-2.5 px-3 py-2.5 transition-colors"
              :class="
                session.conversationId === conversationId ? 'session-item--active' : 'hover:bg-[var(--app-subtle)]'
              "
              @click="handleSelect(session.conversationId)"
            >
              <div
                class="h-7 w-7 flex shrink-0 items-center justify-center text-15px"
                :class="
                  session.conversationId === conversationId
                    ? 'text-[var(--color-mauve)]'
                    : 'text-[var(--color-graphite)]'
                "
              >
                <icon-material-symbols:chat-outline-rounded />
              </div>
              <div class="min-w-0 flex-1">
                <div
                  class="truncate text-13px font-500"
                  :class="session.conversationId === conversationId ? 'text-[var(--color-ink)]' : ''"
                >
                  {{ session.title }}
                </div>
                <div class="text-11px color-[var(--color-graphite)]">{{ formatDate(session.updatedAt) }}</div>
              </div>

              <!-- Action button -->
              <NPopconfirm v-if="activeTab === 'active'" @positive-click="handleArchive(session.conversationId)">
                <template #trigger>
                  <NButton
                    class="shrink-0 transition-opacity"
                    :class="session.conversationId === conversationId ? '' : 'opacity-0 group-hover:opacity-100'"
                    text
                    size="tiny"
                    @click.stop
                  >
                    <template #icon>
                      <icon-material-symbols:archive-outline-rounded class="text-15px color-#999 hover:color-#666" />
                    </template>
                  </NButton>
                </template>
                归档后可在「已归档」中找回
              </NPopconfirm>
              <NButton
                v-else
                class="shrink-0 opacity-0 transition-opacity group-hover:opacity-100"
                text
                size="tiny"
                @click.stop="handleUnarchive(session.conversationId)"
              >
                <template #icon>
                  <icon-material-symbols:unarchive-outline-rounded class="text-15px color-#999 hover:color-#666" />
                </template>
              </NButton>
            </div>
          </TransitionGroup>
        </NSpin>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sidebar-title {
  font-family: var(--ed-font-serif);
  font-size: 20px;
  font-weight: 400;
  letter-spacing: -0.01em;
  color: var(--color-ink);
}

.sidebar-tabs {
  border-bottom: 1px solid var(--ed-hairline);
}

.sidebar-tab {
  flex: 1;
  margin-bottom: -1px;
  border: 0;
  border-bottom: 1px solid transparent;
  padding: 8px 12px 10px;
  background: transparent;
  cursor: pointer;
  font-family: var(--ed-font-sans);
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.2em;
  color: var(--color-graphite);
  transition:
    color 200ms ease,
    border-color 200ms ease;
}

.sidebar-tab:hover {
  color: var(--color-ink);
}

.sidebar-tab--active {
  color: var(--color-ink);
  border-bottom-color: var(--color-ink);
}

.session-item {
  position: relative;
  border-left: 2px solid transparent;
}

.session-item--active {
  border-left-color: var(--color-mauve);
}

.session-list-enter-active,
.session-list-leave-active {
  transition: all 0.2s ease;
}
.session-list-enter-from,
.session-list-leave-to {
  opacity: 0;
  transform: translateX(-8px);
}
</style>
