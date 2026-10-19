<template>
  <div class="cw">
    <transition name="cw-pop">
      <section
        v-if="open"
        ref="panelRef"
        class="cw-panel"
        :class="{ dragging }"
        :style="panelStyle"
        aria-label="智能客服"
      >
        <header class="cw-hd" @pointerdown="startDrag($event, 'panel')">
          <span class="cw-avatar">E</span>
          <div class="cw-id">
            <b>小 E · 智能客服</b>
            <small :class="{ busy: loading }">{{ loading ? '正在输入…' : '在线，随时为你解答' }}</small>
          </div>
          <button class="cw-close" aria-label="关闭客服" @pointerdown.stop @click="open = false">
            <svg viewBox="0 0 24 24"><path d="M6 6l12 12M18 6L6 18" /></svg>
          </button>
        </header>

        <div ref="listRef" class="cw-body">
          <div v-if="!messages.length" class="cw-welcome">
            <p>你好，我是 E-Shop 智能客服小 E。</p>
            <p class="cw-sub">商品、订单、退换货都可以问我，也可以直接说「转人工」。</p>
          </div>

          <div v-for="(m, i) in messages" :key="i" class="cw-msg" :class="m.role">
            <div class="cw-col">
              <div class="cw-bubble">{{ m.text }}</div>
              <div v-if="m.role === 'assistant' && citedSources(m).length" class="cw-src">
                参考：{{ citedSources(m).join('、') }}
              </div>
              <div v-if="m.role === 'assistant' && m.links?.length" class="cw-links">
                <RouterLink
                  v-for="link in m.links"
                  :key="link.url"
                  :to="link.url"
                  class="cw-link"
                  @click="open = false"
                >
                  {{ link.label }}
                </RouterLink>
              </div>
            </div>
          </div>

          <div v-if="showTyping" class="cw-msg assistant">
            <div class="cw-bubble cw-typing">
              <i /><i /><i />
              <span v-if="statusText">{{ statusText }}</span>
            </div>
          </div>
        </div>

        <div class="cw-quickbar" role="group" aria-label="常见问题">
          <button v-for="q in quickQuestions" :key="q.label" :disabled="loading" @click="send(q.text)">
            {{ q.label }}
          </button>
        </div>

        <form class="cw-ft" @submit.prevent="loading ? stop() : send()">
          <input
            v-model="input"
            maxlength="500"
            placeholder="输入你的问题…"
            aria-label="输入问题"
          />
          <button type="submit" :class="{ stop: loading }" :disabled="!loading && !input.trim()">
            {{ loading ? '停止' : '发送' }}
          </button>
        </form>
        <p class="cw-note">AI 生成内容仅供参考，重要信息请以页面为准</p>
      </section>
    </transition>

    <button
      v-if="!open"
      ref="fabRef"
      class="cw-fab"
      :class="{ dragging }"
      :style="fabStyle"
      aria-label="打开智能客服"
      @pointerdown="startDrag($event, 'fab')"
      @click="onFabClick"
    >
      <svg viewBox="0 0 24 24">
        <path d="M21 11.5a8.4 8.4 0 0 1-9 8.4 9.9 9.9 0 0 1-3.3-.6L3 21l1.7-5A8.2 8.2 0 0 1 3.6 11.5a8.4 8.4 0 0 1 9-8.4 8.5 8.5 0 0 1 8.4 8.4z" />
        <path d="M8.5 11.5h7" />
      </svg>
      <span>客服</span>
    </button>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { aiApi } from '../api'

const SESSION_KEY = 'eshop_ai_session'
const FAB_POS_KEY = 'eshop_ai_fab_pos'
const PANEL_POS_KEY = 'eshop_ai_panel_pos'

const PANEL_W = 360
const PANEL_H = 540
const FAB_SIZE = 58

const open = ref(false)
const input = ref('')
const loading = ref(false)
const statusText = ref('')
const messages = ref([])
const listRef = ref(null)
const panelRef = ref(null)
const fabRef = ref(null)
const sessionId = ref(localStorage.getItem(SESSION_KEY) || '')

let controller = null

/** 等待首个 token（或规则模式打字机启动前）才显示「正在输入」气泡 */
const showTyping = computed(() => {
  if (!loading.value) return false
  const last = messages.value[messages.value.length - 1]
  return !last?.text
})

// ---------- 拖拽 ----------
const panelPos = ref(readPos(PANEL_POS_KEY))
const fabPos = ref(readPos(FAB_POS_KEY))
const dragging = ref(false)
const isCompact = ref(false)

let drag = null
let suppressClick = false

function readPos(key) {
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return null
    const p = JSON.parse(raw)
    return Number.isFinite(p?.x) && Number.isFinite(p?.y) ? { x: p.x, y: p.y } : null
  } catch (e) {
    return null
  }
}

function savePos(key, pos) {
  if (!pos) return
  try {
    localStorage.setItem(key, JSON.stringify(pos))
  } catch (e) {
    /* 隐私模式下 localStorage 可能不可写，忽略 */
  }
}

/** 夹在视口内，留 8px 边距，防止拖出屏幕后再也点不到 */
function clampToViewport(x, y, w, h) {
  const margin = 8
  const maxX = Math.max(margin, window.innerWidth - w - margin)
  const maxY = Math.max(margin, window.innerHeight - h - margin)
  return {
    x: Math.min(Math.max(margin, x), maxX),
    y: Math.min(Math.max(margin, y), maxY)
  }
}

const panelStyle = computed(() => {
  if (isCompact.value || !panelPos.value) return {}
  return {
    left: `${panelPos.value.x}px`,
    top: `${panelPos.value.y}px`,
    right: 'auto',
    bottom: 'auto'
  }
})

const fabStyle = computed(() => {
  if (isCompact.value || !fabPos.value) return {}
  return {
    left: `${fabPos.value.x}px`,
    top: `${fabPos.value.y}px`,
    right: 'auto',
    bottom: 'auto'
  }
})

function startDrag(e, kind) {
  if (isCompact.value || e.button > 0) return
  const el = kind === 'panel' ? panelRef.value : fabRef.value
  if (!el) return

  const rect = el.getBoundingClientRect()
  suppressClick = false
  drag = {
    kind,
    startX: e.clientX,
    startY: e.clientY,
    originX: rect.left,
    originY: rect.top,
    w: rect.width,
    h: rect.height,
    moved: false
  }
  dragging.value = true
  window.addEventListener('pointermove', onDragMove)
  window.addEventListener('pointerup', onDragEnd)
  window.addEventListener('pointercancel', onDragEnd)
}

function onDragMove(e) {
  if (!drag) return
  const dx = e.clientX - drag.startX
  const dy = e.clientY - drag.startY
  // 3px 阈值：小于它视为点击，避免手抖吞掉打开动作
  if (!drag.moved && Math.abs(dx) + Math.abs(dy) < 4) return
  drag.moved = true

  const next = clampToViewport(drag.originX + dx, drag.originY + dy, drag.w, drag.h)
  if (drag.kind === 'panel') panelPos.value = next
  else fabPos.value = next
}

function onDragEnd() {
  window.removeEventListener('pointermove', onDragMove)
  window.removeEventListener('pointerup', onDragEnd)
  window.removeEventListener('pointercancel', onDragEnd)

  if (drag?.moved) {
    suppressClick = true
    if (drag.kind === 'panel') savePos(PANEL_POS_KEY, panelPos.value)
    else savePos(FAB_POS_KEY, fabPos.value)
  }
  drag = null
  dragging.value = false
}

/** 拖动结束后浏览器仍会补一个 click，这里吃掉它 */
function onFabClick() {
  if (suppressClick) {
    suppressClick = false
    return
  }
  openPanel()
}

function openPanel() {
  // 首次打开且按钮被拖动过：面板贴着按钮展开（右下角对齐），再夹回视口
  if (!panelPos.value && fabPos.value) {
    panelPos.value = clampToViewport(
      fabPos.value.x + FAB_SIZE - PANEL_W,
      fabPos.value.y + FAB_SIZE - PANEL_H,
      PANEL_W,
      PANEL_H
    )
  }
  open.value = true
}

function onResize() {
  isCompact.value = window.innerWidth <= 520
  if (isCompact.value) return
  if (panelPos.value) {
    panelPos.value = clampToViewport(panelPos.value.x, panelPos.value.y, PANEL_W, PANEL_H)
  }
  if (fabPos.value) {
    fabPos.value = clampToViewport(fabPos.value.x, fabPos.value.y, FAB_SIZE, FAB_SIZE)
  }
}

onMounted(() => {
  isCompact.value = window.innerWidth <= 520
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  window.removeEventListener('pointermove', onDragMove)
  window.removeEventListener('pointerup', onDragEnd)
  window.removeEventListener('pointercancel', onDragEnd)
})

// 常驻快捷入口：label 是展示用的短标签，text 是真正发送的问句
const quickQuestions = [
  { label: '退换货', text: '七天无理由怎么算？' },
  { label: '发货时间', text: '付款后多久发货？' },
  { label: '运费', text: '运费怎么算？满多少包邮？' },
  { label: '转人工', text: '我要转人工' }
]

function scrollToBottom() {
  nextTick(() => {
    const el = listRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

/** 只在用户本来就贴着底部时才自动滚动，避免读历史时被强行拽回 */
function maybeScroll() {
  const el = listRef.value
  if (!el) return
  if (el.scrollHeight - el.scrollTop - el.clientHeight < 80) {
    el.scrollTop = el.scrollHeight
  }
}

/** 只展示知识库出处的标题（规则兜底的 key 没有 title，不展示） */
function citedSources(message) {
  const titles = (message?.sources || []).filter((s) => s && s.title).map((s) => s.title)
  return [...new Set(titles)]
}

/** 规则模式没有 token 增量，用打字机把整段回答补上，体验保持一致 */
function typeOut(target, full, signal) {
  return new Promise((resolve) => {
    let i = 0
    const step = Math.max(1, Math.ceil(full.length / 50))
    target.text = ''
    const timer = setInterval(() => {
      if (signal?.aborted) {
        clearInterval(timer)
        target.text = full.slice(0, i) || '已停止生成'
        resolve()
        return
      }
      i += step
      target.text = full.slice(0, i)
      maybeScroll()
      if (i >= full.length) {
        clearInterval(timer)
        target.text = full
        resolve()
      }
    }, 20)
  })
}

async function send(text) {
  const content = String(text ?? input.value).trim()
  if (!content || loading.value) return

  messages.value.push({ role: 'user', text: content })
  input.value = ''
  loading.value = true
  statusText.value = '正在思考…'
  scrollToBottom()

  const reply = { role: 'assistant', text: '', streaming: true, sources: [], links: [] }
  messages.value.push(reply)

  controller = new AbortController()
  let gotDelta = false
  let typing = null

  try {
    await aiApi.chatStream(
      { message: content, sessionId: sessionId.value },
      {
        signal: controller.signal,
        onMeta: (meta) => {
          if (meta.sessionId) {
            sessionId.value = meta.sessionId
            localStorage.setItem(SESSION_KEY, meta.sessionId)
          }
        },
        onStatus: (t) => {
          if (t) statusText.value = t
        },
        onDelta: (t) => {
          if (!t) return
          gotDelta = true
          reply.text += t
          maybeScroll()
        },
        onDone: (data) => {
          if (!gotDelta && data.answer) {
            typing = typeOut(reply, data.answer, controller.signal)
          } else if (data.answer && data.answer !== reply.text) {
            reply.text = data.answer
          }
          reply.sources = data.sources || []
          reply.links = data.links || []
          reply.streaming = false
        },
        onError: (message) => {
          reply.text = message
          reply.streaming = false
        }
      }
    )
    if (typing) await typing
  } catch (err) {
    if (err?.name === 'AbortError') {
      reply.text = reply.text ? `${reply.text}\n\n（已停止生成）` : '已停止生成'
    } else {
      reply.text = '抱歉，客服暂时不可用。你可以稍后再试，或拨打 400-000-0000（9:00-21:00）。'
    }
    reply.streaming = false
  } finally {
    loading.value = false
    controller = null
    statusText.value = ''
    scrollToBottom()
  }
}

function stop() {
  controller?.abort()
}
</script>

<style scoped>
.cw-panel {
  position: fixed;
  right: 24px;
  bottom: 24px;
  width: 360px;
  height: 540px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 12px 40px rgba(31, 35, 41, 0.16);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  z-index: 60;
  border: 1px solid var(--border);
}
.cw-hd {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
  background: #fff;
  cursor: grab;
  user-select: none;
  touch-action: none;
}
.cw-panel.dragging .cw-hd {
  cursor: grabbing;
}
.cw-panel.dragging,
.cw-fab.dragging {
  user-select: none;
}
.cw-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--primary);
  color: #fff;
  font-weight: 700;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.cw-id {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}
.cw-id b {
  font-size: 14px;
}
.cw-id small {
  color: var(--text-3);
  font-size: 12px;
}
.cw-id small.busy {
  color: var(--primary);
}
.cw-close {
  margin-left: auto;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.cw-close:hover {
  background: var(--bg);
}
.cw-close svg {
  width: 16px;
  height: 16px;
  stroke: var(--text-2);
  stroke-width: 2;
  fill: none;
  stroke-linecap: round;
}
.cw-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: var(--bg);
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.cw-welcome {
  background: #fff;
  border-radius: 12px;
  padding: 14px;
  font-size: 13px;
  color: var(--text);
}
.cw-sub {
  color: var(--text-2);
  margin-top: 6px;
}
.cw-quickbar {
  display: flex;
  gap: 8px;
  padding: 8px 12px;
  border-top: 1px solid var(--border);
  background: #fff;
  overflow-x: auto;
  scrollbar-width: none;
}
.cw-quickbar::-webkit-scrollbar {
  display: none;
}
.cw-quickbar button {
  flex: none;
  border: 1px solid var(--border);
  background: #fff;
  color: var(--text-2);
  border-radius: 999px;
  padding: 5px 10px;
  font-size: 12px;
  white-space: nowrap;
}
.cw-quickbar button:hover:not(:disabled) {
  border-color: var(--primary);
  color: var(--primary);
  background: var(--primary-light);
}
.cw-quickbar button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.cw-msg {
  display: flex;
}
.cw-msg.user {
  justify-content: flex-end;
}
.cw-col {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: 84%;
}
.cw-src {
  font-size: 11px;
  color: var(--text-3);
  padding-left: 2px;
  line-height: 1.5;
}
.cw-links {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding-left: 2px;
}
.cw-link {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  padding: 5px 10px;
  border: 1px solid var(--primary);
  border-radius: 999px;
  background: #fff;
  color: var(--primary);
  font-size: 12px;
  line-height: 1.4;
  text-decoration: none;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.cw-link:hover {
  background: var(--primary-light);
}
.cw-bubble {
  max-width: 100%;
  padding: 10px 12px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}
.cw-msg.assistant .cw-bubble {
  background: #fff;
  color: var(--text);
  border: 1px solid var(--border);
  border-top-left-radius: 4px;
}
.cw-msg.user .cw-bubble {
  background: var(--primary);
  color: #fff;
  border-top-right-radius: 4px;
}
.cw-typing {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 14px 14px;
}
.cw-typing span {
  margin-left: 6px;
  font-size: 12px;
  color: var(--text-3);
}
.cw-typing i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-3);
  animation: cw-blink 1.2s infinite ease-in-out;
}
.cw-typing i:nth-child(2) {
  animation-delay: 0.18s;
}
.cw-typing i:nth-child(3) {
  animation-delay: 0.36s;
}
@keyframes cw-blink {
  0%,
  80%,
  100% {
    opacity: 0.25;
  }
  40% {
    opacity: 1;
  }
}
.cw-ft {
  display: flex;
  gap: 8px;
  padding: 4px 12px 10px;
  background: #fff;
}
.cw-ft input {
  flex: 1;
  height: 38px;
  border: 1px solid var(--border);
  border-radius: 9px;
  padding: 0 12px;
  font-size: 13px;
  outline: none;
  font-family: inherit;
}
.cw-ft input:focus {
  border-color: var(--primary);
}
.cw-ft button {
  height: 38px;
  padding: 0 16px;
  border-radius: 9px;
  background: var(--primary);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
}
.cw-ft button:disabled {
  background: #f0c0b3;
  cursor: not-allowed;
}
.cw-ft button.stop {
  background: #fff;
  color: var(--text-2);
  border: 1px solid var(--border);
}
.cw-note {
  text-align: center;
  font-size: 11px;
  color: var(--text-3);
  padding: 0 0 8px;
  background: #fff;
}
.cw-fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  width: 58px;
  height: 58px;
  border-radius: 50%;
  background: var(--primary);
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
  box-shadow: 0 8px 24px rgba(238, 77, 45, 0.36);
  z-index: 60;
  transition: transform 0.15s;
  cursor: grab;
  touch-action: none;
}
.cw-fab.dragging {
  cursor: grabbing;
  transition: none;
}
.cw-fab.dragging:hover {
  transform: none;
}
.cw-fab:hover {
  transform: translateY(-2px);
}
.cw-fab svg {
  width: 22px;
  height: 22px;
  fill: none;
  stroke: #fff;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.cw-fab span {
  font-size: 10px;
  line-height: 1;
}
.cw-pop-enter-active,
.cw-pop-leave-active {
  transition: opacity 0.16s, transform 0.16s;
}
.cw-pop-enter-from,
.cw-pop-leave-to {
  opacity: 0;
  transform: translateY(8px) scale(0.98);
}
@media (max-width: 520px) {
  .cw-panel {
    right: 12px;
    left: 12px;
    bottom: 12px;
    width: auto;
    height: 72vh;
  }
  .cw-fab {
    right: 16px;
    bottom: 16px;
  }
}
</style>
