<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { teacherApi } from '@/api/teacher'
import { errorMessage } from '@/api/errors'
import type { Doubt } from '@/types/models'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { formatDateTime, formatRelative } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import AppIcon from '@/components/AppIcon.vue'
import AppEmpty from '@/components/AppEmpty.vue'
import AppPagination from '@/components/AppPagination.vue'
import { fetchAllPages } from '@/utils/pagination'

const DOUBTS_PAGE_SIZE = 10

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()

const loading = ref(true)
const doubts = ref<Doubt[]>([])
const pageError = ref('')
const filter = ref<'ALL' | 'PENDING' | 'REPLIED' | 'CLOSED'>('ALL')
const keyword = ref('')
const selectedId = ref<number | null>(null)
const listPage = ref(1)
const truncated = ref(false)
const composer = ref('')
const sending = ref(false)
const sendError = ref('')
const threadRef = ref<HTMLElement | null>(null)

const counts = computed(() => ({
  ALL: doubts.value.length,
  PENDING: doubts.value.filter((doubt) => doubt.status === 'PENDING').length,
  REPLIED: doubts.value.filter((doubt) => doubt.status === 'REPLIED').length,
  CLOSED: doubts.value.filter((doubt) => doubt.status === 'CLOSED').length,
}))

const filteredDoubts = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  return doubts.value
    .filter((doubt) => filter.value === 'ALL' || doubt.status === filter.value)
    .filter(
      (doubt) =>
        !text ||
        doubt.content.toLowerCase().includes(text) ||
        doubt.studentName.toLowerCase().includes(text) ||
        doubt.studentNo.toLowerCase().includes(text) ||
        String(doubt.seqNo).includes(text),
    )
})
const pagedDoubts = computed(() => {
  const start = (listPage.value - 1) * DOUBTS_PAGE_SIZE
  return filteredDoubts.value.slice(start, start + DOUBTS_PAGE_SIZE)
})

const selectedDoubt = computed(
  () => doubts.value.find((doubt) => doubt.id === selectedId.value) ?? null,
)
const isClosed = computed(() => selectedDoubt.value?.status === 'CLOSED')

async function load(preferredId?: number | null): Promise<void> {
  loading.value = true
  pageError.value = ''
  try {
    const result = await fetchAllPages((page, pageSize) =>
      teacherApi.listDoubts({ page, pageSize }),
    )
    doubts.value = result.items.sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    truncated.value = result.truncated
    listPage.value = 1
    const targetId =
      preferredId ??
      selectedId.value ??
      (route.query.focus ? Number(route.query.focus) : null) ??
      filteredDoubts.value[0]?.id ??
      doubts.value[0]?.id ??
      null
    selectDoubt(targetId)
  } catch (cause) {
    pageError.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

function selectDoubt(id: number | null): void {
  selectedId.value = id
  composer.value = ''
  sendError.value = ''
}

function chooseFilter(next: typeof filter.value): void {
  filter.value = next
  listPage.value = 1
  if (!filteredDoubts.value.some((doubt) => doubt.id === selectedId.value)) {
    selectDoubt(filteredDoubts.value[0]?.id ?? null)
  }
}

watch([keyword, filter], () => {
  listPage.value = 1
})

async function sendReply(): Promise<void> {
  if (!selectedDoubt.value) return
  const content = composer.value.trim()
  if (!content) {
    sendError.value = '请填写回复内容。'
    return
  }
  if (content.length > 1000) {
    sendError.value = '回复最多 1000 字。'
    return
  }
  sending.value = true
  sendError.value = ''
  try {
    await teacherApi.replyDoubt(selectedDoubt.value.id, content)
    ui.toast('success', '回复已发送', '学生将在自己的疑问页看到回复，记录也会写入审计日志。')
    composer.value = ''
    await load(selectedDoubt.value.id)
    await nextTick()
    threadRef.value?.scrollTo({ top: threadRef.value.scrollHeight, behavior: 'smooth' })
  } catch (cause) {
    sendError.value = errorMessage(cause)
  } finally {
    sending.value = false
  }
}

function goReview(doubt: Doubt): void {
  if (!doubt.homeworkId) {
    void router.push('/teacher/homeworks')
    return
  }
  void router.push(`/teacher/homeworks/${doubt.homeworkId}/review`)
}

watch(
  () => route.query.focus,
  (value) => {
    if (value) {
      const id = Number(value)
      if (doubts.value.some((doubt) => doubt.id === id)) selectDoubt(id)
    }
  },
)

onMounted(load)
</script>

<template>
  <div class="doubts-page">
    <PageHeader
      eyebrow="TEACHER · STUDENT QUESTIONS"
      title="学生疑问箱"
      subtitle="只接收本班学生、针对本人已发布讲评条目的疑问。回复会同步给学生，并写入不可修改的审计记录。"
    >
      <template #actions>
        <button class="btn btn--ghost btn--sm" :disabled="loading" @click="load()">
          <AppIcon name="refresh" :size="14" />
          刷新
        </button>
      </template>
    </PageHeader>

    <div class="doubt-summary">
      <div class="doubt-summary__main">
        <span class="doubt-summary__icon"><AppIcon name="message" :size="20" /></span>
        <div>
          <span class="doubt-summary__eyebrow">QUESTION DESK</span>
          <h2>{{ counts.PENDING ? `${counts.PENDING} 条疑问在等你回复` : '当前没有待回复疑问' }}</h2>
          <p>回复应当针对具体题目与依据，不评价学生能力，也不代替 AI 自由作答。</p>
        </div>
      </div>
      <div class="doubt-summary__counts">
        <div><span>全部</span><b>{{ counts.ALL }}</b></div>
        <div><span>待回复</span><b class="warm">{{ counts.PENDING }}</b></div>
        <div><span>已回复</span><b>{{ counts.REPLIED }}</b></div>
        <div><span>已关闭</span><b>{{ counts.CLOSED }}</b></div>
      </div>
    </div>

    <div v-if="pageError" class="card" style="margin-top: 14px">
      <AppEmpty icon="alert-circle" title="疑问箱加载失败" :description="pageError">
        <button class="btn btn--ghost btn--sm" @click="load()">重试</button>
      </AppEmpty>
    </div>

    <div v-else class="doubt-workspace">
      <aside class="doubt-rail card">
        <div class="rail-head">
          <div>
            <h3 class="section-title">本班疑问</h3>
            <p class="section-desc">按提交时间倒序</p>
          </div>
          <span class="rail-count">{{ filteredDoubts.length }}</span>
        </div>

        <div class="rail-filters">
          <button
            v-for="option in [
              { value: 'ALL' as const, label: '全部' },
              { value: 'PENDING' as const, label: '待回复' },
              { value: 'REPLIED' as const, label: '已回复' },
              { value: 'CLOSED' as const, label: '已关闭' },
            ]"
            :key="option.value"
            type="button"
            :class="{ 'is-active': filter === option.value }"
            @click="chooseFilter(option.value)"
          >
            {{ option.label }}
          </button>
        </div>

        <label class="rail-search">
          <AppIcon name="search" :size="13" />
          <input v-model="keyword" type="search" placeholder="搜索学生或内容…" />
        </label>

        <div class="doubt-list" :class="{ 'is-loading': loading }">
          <div v-if="loading" class="loading-card">
            <span class="skeleton" style="width: 150px; height: 8px" />
          </div>
          <template v-else>
            <button
              v-for="doubt in pagedDoubts"
              :key="doubt.id"
              type="button"
              class="doubt-chip"
              :class="{ 'is-active': selectedId === doubt.id, 'is-pending': doubt.status === 'PENDING' }"
              @click="selectDoubt(doubt.id)"
            >
              <span class="doubt-avatar-wrap">
                <span class="avatar avatar--sm">{{ doubt.studentName.slice(0, 1) }}</span>
                <i v-if="doubt.status === 'PENDING'" class="unread-dot" />
              </span>
              <div class="doubt-chip__copy">
                <div class="doubt-chip__top">
                  <b>{{ doubt.studentName }}</b>
                  <span>{{ formatRelative(doubt.createdAt) }}</span>
                </div>
                <p>{{ doubt.content }}</p>
                <div class="doubt-chip__meta">
                  <span>第 {{ doubt.seqNo }} 题</span>
                  <StatusBadge :status="doubt.status" plain />
                </div>
              </div>
            </button>
            <AppEmpty
              v-if="!filteredDoubts.length"
              icon="message"
              title="没有匹配的疑问"
              description="切换筛选或清空搜索词再试。"
            />
            <AppPagination
              v-model:page="listPage"
              :page-size="DOUBTS_PAGE_SIZE"
              :total="filteredDoubts.length"
            />
            <p v-if="truncated" class="rail-truncated">
              数据量超过加载上限，仅展示前 2000 条，请用搜索缩小范围。
            </p>
          </template>
        </div>
      </aside>

      <section v-if="selectedDoubt" class="conversation card">
        <header class="conversation-head">
          <div class="conversation-student">
            <span class="avatar avatar--lg">{{ selectedDoubt.studentName.slice(0, 1) }}</span>
            <div>
              <div class="conversation-title">
                <h2>{{ selectedDoubt.studentName }} 的疑问</h2>
                <StatusBadge :status="selectedDoubt.status" />
              </div>
              <p>
                {{ selectedDoubt.studentNo }} · 第 {{ selectedDoubt.seqNo }} 题 ·
                {{ formatDateTime(selectedDoubt.createdAt) }}
              </p>
            </div>
          </div>
          <button class="btn btn--ghost btn--sm" type="button" @click="goReview(selectedDoubt)">
            <AppIcon name="external" :size="14" />
            查看对应讲评
          </button>
        </header>

        <div ref="threadRef" class="conversation-thread">
          <div class="thread-question">
            <div class="thread-meta">
              <span class="thread-avatar student">{{ selectedDoubt.studentName.slice(0, 1) }}</span>
              <div>
                <b>{{ selectedDoubt.studentName }}</b>
                <span>{{ formatDateTime(selectedDoubt.createdAt) }}</span>
              </div>
              <span class="thread-label">学生疑问</span>
            </div>
            <div class="bubble bubble--student">
              {{ selectedDoubt.content }}
            </div>
            <p class="thread-context">
              针对第 {{ selectedDoubt.seqNo }} 题
              <span v-if="selectedDoubt.homeworkTitle"> · 《{{ selectedDoubt.homeworkTitle }}》</span>
            </p>
          </div>

          <div v-for="reply in selectedDoubt.replies" :key="reply.id" class="thread-reply">
            <div class="thread-meta">
              <span class="thread-avatar teacher">{{ (reply.teacherName || auth.displayName).slice(0, 1) }}</span>
              <div>
                <b>{{ reply.teacherName || auth.displayName }}</b>
                <span>{{ formatDateTime(reply.createdAt) }}</span>
              </div>
              <span class="thread-label">教师回复</span>
            </div>
            <div class="bubble bubble--teacher">
              {{ reply.content }}
            </div>
          </div>

          <div v-if="!selectedDoubt.replies.length" class="no-reply-hint">
            <AppIcon name="clock" :size="14" />
            还没有回复。首条回复会将状态更新为「已回复」。
          </div>
        </div>

        <div class="reply-area">
          <div class="reply-composer">
            <span class="avatar avatar--sm">{{ auth.displayName.slice(0, 1) }}</span>
            <div class="reply-input-wrap">
              <textarea
                v-model="composer"
                class="reply-textarea"
                maxlength="1000"
                :disabled="isClosed"
                placeholder="针对这道题的具体步骤回复学生；说明依据，不做能力评价…"
                @keydown.ctrl.enter.prevent="sendReply"
              />
              <div class="reply-tools">
                <span class="reply-hint">
                  <AppIcon name="shield" :size="11" />
                  回复对学生可见 · Ctrl + Enter 发送
                </span>
                <span>{{ composer.length }} / 1000</span>
              </div>
            </div>
            <button
              class="btn btn--primary btn--sm reply-send"
              type="button"
              :disabled="sending || isClosed || !composer.trim()"
              @click="sendReply"
            >
              <AppIcon v-if="sending" name="refresh" :size="14" />
              <AppIcon v-else name="send" :size="14" />
              发送回复
            </button>
          </div>
          <div v-if="sendError" class="error-banner" style="margin-top: 8px">
            <AppIcon name="alert-circle" :size="14" />
            {{ sendError }}
          </div>
          <p v-if="isClosed" class="reply-closed">该疑问已关闭，不能再回复。</p>
        </div>

        <footer class="conversation-foot">
          <AppIcon name="shield" :size="12" />
          仅本班教师可见；学生之间互不可见；回复不可删除，修改需追加新回复。
        </footer>
      </section>

      <AppEmpty
        v-else
        icon="message"
        title="选择一条疑问"
        description="左侧列表只显示本班学生针对本人已发布讲评条目提出的问题。"
      />
    </div>
  </div>
</template>

<style scoped>
.doubts-page {
  max-width: 1240px;
}

.doubt-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 14px;
  padding: 18px 22px;
  border: 1px solid #f0e3d0;
  border-radius: 19px;
  background:
    radial-gradient(circle at 100% 20%, rgba(255, 255, 255, 0.9), transparent 34%),
    linear-gradient(118deg, #fffdf9, #fdf6e9 60%, #f8f4fd);
}

.doubt-summary__main {
  display: flex;
  align-items: center;
  gap: 13px;
}

.doubt-summary__icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  flex: none;
  border-radius: 13px;
  color: var(--apricot-deep);
  background: rgba(255, 255, 255, 0.72);
}

.doubt-summary__eyebrow {
  color: var(--apricot-deep);
  font-family: var(--font-mono);
  font-size: 7.5px;
  letter-spacing: 0.12em;
}

.doubt-summary h2 {
  margin-top: 3px;
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
}

.doubt-summary p {
  margin-top: 3px;
  color: var(--ink-3);
  font-size: 9px;
  line-height: 1.6;
}

.doubt-summary__counts {
  display: flex;
  gap: 18px;
  flex: none;
  padding-left: 20px;
  border-left: 1px dashed rgba(172, 138, 94, 0.22);
}

.doubt-summary__counts div {
  text-align: center;
}

.doubt-summary__counts span {
  display: block;
  color: var(--ink-3);
  font-size: 8px;
}

.doubt-summary__counts b {
  display: block;
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 21px;
  font-weight: 600;
  line-height: 1.2;
}

.doubt-summary__counts b.warm {
  color: var(--coral-deep);
}

.doubt-workspace {
  display: grid;
  grid-template-columns: 318px minmax(0, 1fr);
  gap: 14px;
  align-items: stretch;
}

.doubt-rail {
  position: sticky;
  top: 82px;
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 100px);
  padding: 16px;
  overflow: hidden;
}

.rail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.rail-head .section-title {
  font-size: 13.5px;
}

.rail-head .section-desc {
  font-size: 9px;
}

.rail-count {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  color: var(--role-deep);
  background: var(--role-soft);
  font-family: var(--font-numbers);
  font-size: 12px;
}

.rail-filters {
  display: flex;
  gap: 2px;
  margin: 9px 0 7px;
  padding: 3px;
  border-radius: 8px;
  background: var(--surface-soft);
}

.rail-filters button {
  flex: 1;
  padding: 4px 3px;
  border-radius: 6px;
  color: var(--ink-3);
  font-size: 8px;
  transition: 0.18s;
}

.rail-filters button.is-active {
  color: var(--role-deep);
  background: #fff;
  box-shadow: 0 1px 3px rgba(40, 80, 66, 0.08);
}

.rail-search {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 30px;
  margin-bottom: 6px;
  padding: 0 9px;
  border: 1px solid var(--line);
  border-radius: 8px;
  color: var(--ink-4);
  background: #fdfefd;
}

.rail-search input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  font-size: 9px;
}

.doubt-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 3px;
  overflow-y: auto;
  padding-right: 2px;
}

.doubt-chip {
  display: flex;
  flex: none;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
  padding: 9px 8px;
  border: 1px solid transparent;
  border-radius: 9px;
  text-align: left;
  transition: 0.18s;
}

.doubt-chip:hover {
  background: #fbfdfc;
}

.doubt-chip.is-active {
  border-color: color-mix(in srgb, var(--role) 24%, var(--line));
  background: var(--role-wash);
}

.doubt-chip.is-pending:not(.is-active) {
  background: #fffdf9;
}

.doubt-avatar-wrap {
  position: relative;
  flex: none;
}

.unread-dot {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 7px;
  height: 7px;
  border: 1.5px solid #fff;
  border-radius: 50%;
  background: var(--coral);
}

.doubt-chip__copy {
  min-width: 0;
  flex: 1;
}

.doubt-chip__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.doubt-chip__top b {
  color: var(--ink-2);
  font-size: 9.5px;
  font-weight: 600;
}

.doubt-chip__top > span {
  color: var(--ink-4);
  font-size: 7.5px;
}

.doubt-chip__copy > p {
  display: -webkit-box;
  margin-top: 2px;
  overflow: hidden;
  color: var(--ink-3);
  font-size: 8.5px;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.doubt-chip__meta {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 3px;
  color: var(--ink-4);
  font-size: 7.5px;
}

.doubt-chip__meta :deep(.badge) {
  min-height: 15px;
  padding: 0;
  font-size: 7px;
}

.doubt-chip__meta :deep(.badge::before) {
  display: none;
}

/* 会话 */
.conversation {
  display: flex;
  flex-direction: column;
  min-height: 560px;
  padding: 0;
  overflow: hidden;
}

.conversation-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 19px;
  border-bottom: 1px solid var(--line);
  background:
    radial-gradient(circle at 100% 0, color-mix(in srgb, var(--role) 6%, transparent), transparent 44%),
    #fff;
}

.conversation-student {
  display: flex;
  align-items: center;
  gap: 10px;
}

.conversation-title {
  display: flex;
  align-items: center;
  gap: 7px;
}

.conversation-title h2 {
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 600;
}

.conversation-student p {
  margin-top: 2px;
  color: var(--ink-4);
  font-size: 8.5px;
}

.conversation-thread {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 20px;
  flex: 1;
  overflow-y: auto;
  background:
    radial-gradient(rgba(63, 126, 107, 0.06) 0.7px, transparent 0.7px),
    #fbfdfc;
  background-size: 19px 19px;
}

.thread-question,
.thread-reply {
  max-width: 86%;
}

.thread-reply {
  align-self: flex-end;
}

.thread-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 5px;
}

.thread-avatar {
  display: grid;
  place-items: center;
  width: 25px;
  height: 25px;
  border-radius: 50%;
  font-family: var(--font-serif);
  font-size: 10px;
}

.thread-avatar.student {
  color: var(--peri-deep);
  background: var(--peri-soft);
}

.thread-avatar.teacher {
  color: var(--mint-deep);
  background: var(--mint-soft);
}

.thread-meta > div {
  display: flex;
  align-items: baseline;
  gap: 5px;
}

.thread-meta b {
  color: var(--ink-2);
  font-size: 9px;
}

.thread-meta span:not(.thread-avatar):not(.thread-label) {
  color: var(--ink-4);
  font-size: 7.5px;
}

.thread-label {
  margin-left: auto;
  color: var(--ink-4);
  font-size: 7.5px;
}

.bubble {
  padding: 11px 14px;
  font-size: 10.5px;
  line-height: 1.85;
  white-space: pre-wrap;
}

.bubble--student {
  border: 1px solid var(--line);
  border-radius: 2px 13px 13px 13px;
  color: var(--ink-2);
  background: #fff;
  box-shadow: var(--shadow-xs);
}

.bubble--teacher {
  border: 1px solid color-mix(in srgb, var(--mint) 18%, var(--line));
  border-radius: 13px 2px 13px 13px;
  color: #365a4d;
  background: #f0faf6;
}

.thread-context {
  margin-top: 5px;
  color: var(--ink-4);
  font-size: 7.5px;
}

.no-reply-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 10px;
  border: 1px dashed var(--line-strong);
  border-radius: 8px;
  color: var(--ink-4);
  font-size: 8px;
}

.reply-area {
  padding: 13px 18px 10px;
  border-top: 1px solid var(--line);
  background: #fff;
}

.reply-composer {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.reply-input-wrap {
  flex: 1;
  border: 1px solid var(--line);
  border-radius: 11px;
  background: #fdfefd;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.reply-input-wrap:focus-within {
  border-color: color-mix(in srgb, var(--role) 40%, var(--line));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--role) 8%, transparent);
}

.reply-textarea {
  display: block;
  width: 100%;
  min-height: 62px;
  padding: 9px 11px 0;
  border: 0;
  outline: 0;
  color: var(--ink);
  background: transparent;
  font-size: 10px;
  line-height: 1.7;
  resize: vertical;
}

.reply-textarea::placeholder {
  color: var(--ink-4);
}

.reply-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 2px 11px 7px;
  color: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 7px;
}

.reply-hint {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.reply-send {
  height: 38px;
  flex: none;
}

.reply-closed {
  margin-top: 6px;
  color: var(--ink-4);
  font-size: 8px;
}

.conversation-foot {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 7px 19px 9px;
  color: var(--ink-4);
  background: #fff;
  font-size: 7.5px;
}

@media (max-width: 1000px) {
  .doubt-workspace {
    grid-template-columns: 1fr;
  }

  .doubt-rail {
    position: static;
    max-height: none;
  }

  .doubt-list {
    display: grid;
    grid-template-columns: 1fr 1fr;
    max-height: 260px;
  }
}

@media (max-width: 700px) {
  .doubt-summary {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
    padding: 16px;
  }

  .doubt-summary__counts {
    width: 100%;
    justify-content: space-between;
    padding-top: 10px;
    padding-left: 0;
    border-top: 1px dashed rgba(172, 138, 94, 0.22);
    border-left: 0;
  }

  .doubt-list {
    grid-template-columns: 1fr;
    max-height: 240px;
  }

  .conversation-head {
    align-items: flex-start;
    flex-direction: column;
    padding: 14px;
  }

  .conversation-thread {
    padding: 14px;
  }

  .thread-question,
  .thread-reply {
    max-width: 100%;
  }

  .reply-area {
    padding: 10px 13px;
  }

  .reply-composer {
    flex-wrap: wrap;
  }

  .reply-composer > .avatar {
    display: none;
  }

  .reply-input-wrap {
    width: 100%;
    flex-basis: 100%;
  }

  .reply-send {
    width: 100%;
  }
}

.rail-truncated {
  margin: 8px 0 0;
  color: var(--apricot-deep);
  font-size: 8px;
  line-height: 1.6;
}
</style>
