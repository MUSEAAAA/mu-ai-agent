<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppLogo from '../components/AppLogo.vue'
import { deleteChatMemory, streamChat } from '../api/chat'

const props = defineProps({
  appType: {
    type: String,
    required: true,
  },
})

const appConfigs = {
  love: {
    name: 'AI 恋爱大师',
    eyebrow: 'LOVE MASTER',
    description: '温柔倾听，也给你清醒的答案',
    endpoint: '/ai/love_app/chat/sse',
    placeholder: '说说你最近在关系中遇到的困惑...',
    greeting:
      '嗨，我是你的 AI 恋爱大师。无论是暧昧期的猜测、相处中的摩擦，还是关于自己的情绪，都可以慢慢告诉我。',
    suggestions: ['对方忽冷忽热怎么办？', '怎样表达需求不显得强势？', '帮我分析一段聊天记录'],
    accent: 'rose',
  },
  manus: {
    name: 'AI 超级智能体',
    eyebrow: 'SUPER AGENT',
    description: '自主思考，调用工具，解决复杂任务',
    endpoint: '/ai/manus/chat',
    placeholder: '描述一个需要完成的任务...',
    greeting:
      '你好，我是 AI 超级智能体。我可以拆解目标、检索信息并调用工具执行任务。告诉我你想完成什么。',
    suggestions: ['帮我调研一个行业趋势', '制定一份项目执行计划', '分析并总结一个复杂问题'],
    accent: 'blue',
  },
}

const config = computed(() => appConfigs[props.appType])
const createChatId = () =>
  globalThis.crypto?.randomUUID?.() ||
  `chat-${Date.now()}-${Math.random().toString(16).slice(2)}`

const chatId = ref(createChatId())
const inputMessage = ref('')
const isStreaming = ref(false)
const messages = ref([])
const conversations = ref([])
const deletingChatIds = ref([])
const historyError = ref('')
const isSidebarOpen = ref(false)
const messageList = ref(null)
let abortController = null
const deletedChatIds = new Set()

const storageKey = computed(() => `mu-ai-conversations:${props.appType}`)

function createGreetingMessage() {
  return {
    id: createChatId(),
    role: 'assistant',
    content: config.value.greeting,
  }
}

function persistConversations() {
  try {
    localStorage.setItem(storageKey.value, JSON.stringify(conversations.value))
  } catch {
    // Local storage failure should not interrupt an active conversation.
  }
}

function getConversationTitle(conversationMessages) {
  const firstUserMessage = conversationMessages.find((message) => message.role === 'user')
  if (!firstUserMessage) return '新对话'

  const title = firstUserMessage.content.replace(/\s+/g, ' ').trim()
  return title.length > 20 ? `${title.slice(0, 20)}…` : title
}

function getStoredMessages(conversationMessages) {
  return conversationMessages
    .filter((message) => message.content)
    .map(({ id, role, content, error = false }) => ({
      id,
      role,
      content,
      error,
    }))
}

function saveConversation(targetChatId, conversationMessages) {
  if (deletedChatIds.has(targetChatId)) return

  const storedMessages = getStoredMessages(conversationMessages)
  if (!storedMessages.some((message) => message.role === 'user')) return

  const existingConversation = conversations.value.find(
    (conversation) => conversation.chatId === targetChatId,
  )
  const now = Date.now()
  const conversation = {
    chatId: targetChatId,
    title: getConversationTitle(storedMessages),
    messages: storedMessages,
    createdAt: existingConversation?.createdAt || now,
    updatedAt: now,
  }

  conversations.value = [
    conversation,
    ...conversations.value.filter((item) => item.chatId !== targetChatId),
  ].sort((a, b) => b.updatedAt - a.updatedAt)
  persistConversations()
}

function loadConversations() {
  try {
    const storedConversations = JSON.parse(localStorage.getItem(storageKey.value) || '[]')
    conversations.value = Array.isArray(storedConversations)
      ? storedConversations
          .filter((conversation) => conversation.chatId && Array.isArray(conversation.messages))
          .sort((a, b) => b.updatedAt - a.updatedAt)
      : []
  } catch {
    conversations.value = []
  }
}

function startNewConversation() {
  abortController?.abort()
  chatId.value = createChatId()
  inputMessage.value = ''
  isStreaming.value = false
  messages.value = [createGreetingMessage()]
  isSidebarOpen.value = false
}

function openConversation(conversation) {
  if (conversation.chatId === chatId.value) {
    isSidebarOpen.value = false
    return
  }

  abortController?.abort()
  chatId.value = conversation.chatId
  inputMessage.value = ''
  isStreaming.value = false
  messages.value = conversation.messages.map((message) => ({ ...message }))
  isSidebarOpen.value = false
  scrollToBottom(false)
}

async function deleteConversation(conversation) {
  const confirmed = window.confirm(`确定删除“${conversation.title}”吗？删除后无法恢复。`)
  if (!confirmed) return

  const targetChatId = conversation.chatId
  const wasActive = targetChatId === chatId.value
  const previousConversations = conversations.value

  historyError.value = ''
  deletingChatIds.value = [...deletingChatIds.value, targetChatId]
  deletedChatIds.add(targetChatId)

  if (wasActive) {
    abortController?.abort()
  }

  conversations.value = conversations.value.filter(
    (item) => item.chatId !== targetChatId,
  )
  persistConversations()

  if (wasActive) {
    const nextConversation = conversations.value[0]
    if (nextConversation) {
      openConversation(nextConversation)
    } else {
      startNewConversation()
    }
  }

  try {
    await deleteChatMemory(targetChatId)
  } catch (error) {
    deletedChatIds.delete(targetChatId)
    conversations.value = previousConversations
    persistConversations()
    historyError.value = `删除失败：${error.message}`

    if (wasActive) {
      const restoredConversation = conversations.value.find(
        (item) => item.chatId === targetChatId,
      )
      if (restoredConversation) {
        openConversation(restoredConversation)
      }
    }
  } finally {
    deletingChatIds.value = deletingChatIds.value.filter(
      (chatIdToDelete) => chatIdToDelete !== targetChatId,
    )
  }
}

function initializeConversations() {
  loadConversations()

  if (conversations.value.length) {
    openConversation(conversations.value[0])
  } else {
    startNewConversation()
  }
}

initializeConversations()

async function scrollToBottom(smooth = true) {
  await nextTick()
  if (messageList.value) {
    messageList.value.scrollTo({
      top: messageList.value.scrollHeight,
      behavior: smooth ? 'smooth' : 'auto',
    })
  }
}

function useSuggestion(suggestion) {
  inputMessage.value = suggestion
  sendMessage()
}

function stopStreaming() {
  abortController?.abort()
}

async function sendMessage() {
  const content = inputMessage.value.trim()
  if (!content || isStreaming.value) return

  const conversationChatId = chatId.value
  const conversationMessages = messages.value

  conversationMessages.push({
    id: createChatId(),
    role: 'user',
    content,
  })
  inputMessage.value = ''
  isStreaming.value = true
  saveConversation(conversationChatId, conversationMessages)

  const assistantMessage = {
    id: createChatId(),
    role: 'assistant',
    content: '',
    pending: true,
  }
  conversationMessages.push(assistantMessage)
  await scrollToBottom()

  const requestController = new AbortController()
  abortController = requestController

  try {
    const params =
      props.appType === 'love'
        ? { message: content, chatId: conversationChatId }
        : { message: content }

    await streamChat({
      path: config.value.endpoint,
      params,
      signal: requestController.signal,
      onMessage: (chunk) => {
        assistantMessage.pending = false
        assistantMessage.content += chunk
        if (chatId.value === conversationChatId) {
          scrollToBottom()
        }
      },
    })

    if (!assistantMessage.content) {
      assistantMessage.content = '本次没有收到有效回复，请重新试一次。'
    }
  } catch (error) {
    if (error.name === 'CanceledError' || error.name === 'AbortError') {
      if (!assistantMessage.content) {
        const index = conversationMessages.findIndex(
          (message) => message.id === assistantMessage.id,
        )
        if (index !== -1) conversationMessages.splice(index, 1)
      }
      return
    }

    assistantMessage.error = true
    assistantMessage.content = `连接失败：${error.message}`
  } finally {
    assistantMessage.pending = false
    saveConversation(conversationChatId, conversationMessages)

    if (abortController === requestController) {
      abortController = null
      isStreaming.value = false
    }
    if (chatId.value === conversationChatId) {
      scrollToBottom()
    }
  }
}

function formatConversationTime(timestamp) {
  const date = new Date(timestamp)
  const today = new Date()

  if (date.toDateString() === today.toDateString()) {
    return date.toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    })
  }

  return date.toLocaleDateString('zh-CN', {
    month: 'numeric',
    day: 'numeric',
  })
}

function handleKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    sendMessage()
  }
}

onBeforeUnmount(() => abortController?.abort())
onMounted(() => scrollToBottom(false))
</script>

<template>
  <main
    class="chat-page"
    :class="[`chat-page--${config.accent}`, { 'chat-page--sidebar-open': isSidebarOpen }]"
  >
    <aside class="chat-sidebar">
      <div class="sidebar-heading">
        <RouterLink to="/" class="sidebar-brand" aria-label="返回主页">
          <AppLogo />
        </RouterLink>
        <button
          class="sidebar-close-button"
          type="button"
          aria-label="关闭历史对话"
          @click="isSidebarOpen = false"
        >
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="m7 7 10 10M17 7 7 17" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
          </svg>
        </button>
      </div>

      <div class="sidebar-app">
        <span class="sidebar-app__icon" aria-hidden="true">
          <svg v-if="appType === 'love'" viewBox="0 0 32 32" fill="none">
            <path d="M16 26S5 20 5 12.5C5 8 10.5 6.5 13.5 10.5L16 14l2.5-3.5C21.5 6.5 27 8 27 12.5 27 20 16 26 16 26Z" fill="currentColor" />
          </svg>
          <svg v-else viewBox="0 0 32 32" fill="none">
            <path d="M8 21V11l8 7 8-7v10" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" />
            <circle cx="16" cy="7" r="2" fill="currentColor" />
          </svg>
        </span>
        <div>
          <strong>{{ config.name }}</strong>
          <span>{{ config.eyebrow }}</span>
        </div>
      </div>

      <button class="new-chat-button" type="button" @click="startNewConversation">
        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
        </svg>
        新建对话
      </button>

      <div class="conversation-history">
        <div class="conversation-history__title">
          <span>历史对话</span>
          <span>{{ conversations.length }}</span>
        </div>

        <div v-if="conversations.length" class="conversation-history__list">
          <div
            v-for="conversation in conversations"
            :key="conversation.chatId"
            class="conversation-item"
            :class="{ 'conversation-item--active': conversation.chatId === chatId }"
            :title="`${conversation.title} · ${conversation.chatId}`"
          >
            <button
              class="conversation-item__main"
              type="button"
              @click="openConversation(conversation)"
            >
              <span class="conversation-item__icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none">
                  <path d="M5 6.5h14v9H9l-4 3v-12Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" />
                </svg>
              </span>
              <span class="conversation-item__content">
                <strong>{{ conversation.title }}</strong>
                <small>
                  {{ conversation.chatId.slice(0, 8) }}
                  <i>·</i>
                  {{ formatConversationTime(conversation.updatedAt) }}
                </small>
              </span>
            </button>
            <button
              class="conversation-item__delete"
              type="button"
              :disabled="deletingChatIds.includes(conversation.chatId)"
              :aria-label="`删除对话：${conversation.title}`"
              title="删除对话"
              @click="deleteConversation(conversation)"
            >
              <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d="M8 9v8m4-8v8m4-8v8M5 6h14m-9-3h4l1 3H9l1-3Zm-3 3 1 14h8l1-14" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </button>
          </div>
        </div>

        <p v-if="historyError" class="conversation-history__error">{{ historyError }}</p>

        <p v-if="!conversations.length && !historyError" class="conversation-history__empty">
          发送第一条消息后，<br />
          对话会保存在这里
        </p>
      </div>

      <RouterLink to="/" class="back-link">
        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="m14 6-6 6 6 6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        返回应用中心
      </RouterLink>
    </aside>

    <button
      class="sidebar-backdrop"
      type="button"
      aria-label="关闭历史对话"
      @click="isSidebarOpen = false"
    ></button>

    <section class="chat-main">
      <header class="chat-header">
        <div class="mobile-brand">
          <RouterLink to="/" aria-label="返回主页"><AppLogo compact /></RouterLink>
        </div>
        <div class="chat-header__title">
          <span class="online-dot"></span>
          <div>
            <h1>{{ config.name }}</h1>
            <p>{{ config.description }}</p>
          </div>
        </div>
        <div class="chat-header__actions">
          <span class="chat-id" :title="chatId">
            会话 · {{ chatId.slice(0, 8) }}
          </span>
          <button
            class="mobile-history-button"
            type="button"
            aria-label="打开历史对话"
            @click="isSidebarOpen = true"
          >
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M5 6.5h14v9H9l-4 3v-12Z" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round" />
            </svg>
          </button>
          <button class="mobile-new-button" type="button" aria-label="新建对话" @click="startNewConversation">
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            </svg>
          </button>
        </div>
      </header>

      <div ref="messageList" class="message-list" aria-live="polite">
        <div class="message-list__inner">
          <div class="conversation-date">今天 · 智能对话</div>

          <article
            v-for="message in messages"
            :key="message.id"
            class="message"
            :class="[`message--${message.role}`, { 'message--error': message.error }]"
          >
            <div class="message__avatar" aria-hidden="true">
              <svg v-if="message.role === 'assistant'" viewBox="0 0 32 32" fill="none">
                <path d="M8 21V11l8 7 8-7v10" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" />
                <circle cx="16" cy="7" r="2" fill="currentColor" />
              </svg>
              <span v-else>你</span>
            </div>
            <div class="message__body">
              <span class="message__name">{{ message.role === 'assistant' ? config.name : '你' }}</span>
              <div class="message__bubble">
                <span v-if="message.pending" class="typing-indicator" aria-label="AI 正在输入">
                  <i></i><i></i><i></i>
                </span>
                <template v-else>{{ message.content }}</template>
              </div>
            </div>
          </article>

          <div v-if="messages.length === 1" class="suggestion-section">
            <span class="suggestion-section__label">你可以这样问</span>
            <div class="suggestion-list">
              <button
                v-for="suggestion in config.suggestions"
                :key="suggestion"
                type="button"
                @click="useSuggestion(suggestion)"
              >
                {{ suggestion }}
                <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path d="M5 12h14M14 7l5 5-5 5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>

      <footer class="composer-wrap">
        <form class="composer" @submit.prevent="sendMessage">
          <textarea
            v-model="inputMessage"
            :placeholder="config.placeholder"
            rows="1"
            :disabled="isStreaming"
            aria-label="消息内容"
            @keydown="handleKeydown"
          ></textarea>
          <button
            v-if="isStreaming"
            class="send-button send-button--stop"
            type="button"
            aria-label="停止生成"
            @click="stopStreaming"
          >
            <span></span>
          </button>
          <button
            v-else
            class="send-button"
            type="submit"
            :disabled="!inputMessage.trim()"
            aria-label="发送消息"
          >
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="m5 12 14-7-4.5 14-3-5.5L5 12Z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" />
              <path d="m11.5 13.5 3-3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            </svg>
          </button>
        </form>
        <p>Enter 发送 · Shift + Enter 换行 · AI 生成内容仅供参考</p>
      </footer>
    </section>
  </main>
</template>
