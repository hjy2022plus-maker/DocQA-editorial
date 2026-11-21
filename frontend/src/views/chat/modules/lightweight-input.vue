<script setup lang="ts">
import { getAuthorization } from '@/service/request/shared';
import { getServiceBaseURL } from '@/utils/service';

const chat = useChatStore();
const busy = ref(false);
let active: AbortController | undefined;
const { baseURL } = getServiceBaseURL(import.meta.env, import.meta.env.DEV && import.meta.env.VITE_HTTP_PROXY === 'Y');

async function submit() {
  if (busy.value) return;
  const question = chat.input.message.trim();
  if (!question) return;
  busy.value = true;
  const attempt = new AbortController();
  active = attempt;
  let answer: Api.Chat.Message | undefined;
  try {
    if (!chat.conversationId && !(await chat.createNewSession())) return;
    if (attempt.signal.aborted) return;
    const conversationId = chat.conversationId;
    chat.list.push({ role: 'user', content: question, conversationId });
    answer = reactive<Api.Chat.Message>({ role: 'assistant', content: '', status: 'pending', conversationId });
    chat.list.push(answer);
    chat.input.message = '';
    const response = await fetch(baseURL.replace(/\/$/, '') + '/lightweight/qa/stream', {
      method: 'POST',
      headers: {
        Accept: 'text/event-stream',
        'Content-Type': 'application/json',
        Authorization: getAuthorization() || ''
      },
      body: JSON.stringify({ question, conversationId }),
      signal: attempt.signal
    });
    if (!response.ok) {
      const problem = await response.json().catch(() => null);
      throw new Error(problem?.message || '问答请求失败（' + response.status + '）');
    }
    if (!response.body || !response.headers.get('content-type')?.startsWith('text/event-stream')) {
      throw new Error('服务未返回 SSE');
    }
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let completed = false;
    const handle = (block: string) => {
      let event = '';
      const data: string[] = [];
      for (const line of block.split(/\r?\n/)) {
        if (line.startsWith('event:')) event = line.slice(6).trim();
        if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
      }
      if (!answer || !data.length || !event) return;
      const payload = JSON.parse(data.join('\n'));
      if (event === 'answer' || event === 'refusal') {
        answer.content += payload.text;
        answer.status = 'loading';
      } else if (event === 'citations') {
        answer.referenceMappings = payload;
      } else if (event === 'error') {
        throw new Error(payload.message);
      } else if (event === 'done') {
        completed = true;
        answer.status = 'finished';
      }
    };
    try {
      while (!completed) {
        const next = await reader.read();
        if (next.done) {
          buffer += decoder.decode();
          if (buffer.trim()) handle(buffer);
          break;
        }
        buffer += decoder.decode(next.value, { stream: true });
        const blocks = buffer.split(/\r?\n\r?\n/);
        buffer = blocks.pop() || '';
        blocks.forEach(handle);
      }
    } finally {
      await reader.cancel().catch(() => undefined);
      reader.releaseLock();
    }
    if (!completed) throw new Error('回答未完成，请重新发送');
    void chat.loadSessions();
  } catch (error) {
    if (answer) {
      answer.status = 'error';
      const message = attempt.signal.aborted ? '已停止接收' : error instanceof Error ? error.message : '问答失败';
      answer.content = answer.content ? answer.content + '\n\n' + message : message;
    }
  } finally {
    if (active === attempt) active = undefined;
    busy.value = false;
  }
}

function sendOnEnter(event: KeyboardEvent) {
  if (event.isComposing || event.shiftKey) return;
  event.preventDefault();
  void submit();
}

onUnmounted(() => active?.abort());
function stop() { active?.abort(); }
</script>

<template>
  <div class="shrink-0 px-4 pb-4 pt-2">
    <div class="chat-input-shell mx-auto max-w-[960px] w-full flex items-end gap-2 px-3.5 py-2.5">
      <textarea v-model="chat.input.message" maxlength="2000" rows="2" placeholder="向你的文档提问"
        class="text-body w-full resize-none border-none bg-transparent outline-none"
        @keydown.enter="sendOnEnter" />
      <NButton v-if="busy" size="small" @click="stop">停止</NButton>
      <NButton v-else type="primary" size="small" :disabled="!chat.input.message.trim()" @click="submit">发送</NButton>
    </div>
  </div>
</template>
