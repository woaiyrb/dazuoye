<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { studentApi } from '@/api/student'
import { errorMessage } from '@/api/errors'
import type {
  CorrectionRecord,
  Doubt,
  StudentHandoutDetail,
  StudentHandoutSummary,
} from '@/types/models'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { formatDateTime, formatRelative } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import AppIcon from '@/components/AppIcon.vue'
import AppEmpty from '@/components/AppEmpty.vue'

const auth = useAuthStore()
const ui = useUiStore()
const router = useRouter()

const loading = ref(true)
const error = ref('')
const handouts = ref<StudentHandoutSummary[]>([])
const latest = ref<StudentHandoutSummary | null>(null)
const detail = ref<StudentHandoutDetail | null>(null)
const corrections = ref<CorrectionRecord[]>([])
const doubts = ref<Doubt[]>([])

const greeting = computed(() => {
  const hour = new Date().getHours()
  const word = hour < 6 ? '夜深了' : hour < 11 ? '早上好' : hour < 14 ? '中午好' : hour < 19 ? '下午好' : '晚上好'
  return `${word}，${auth.displayName}`
})
const correctedCount = computed(
  () => detail.value?.items.filter((item) => item.corrections.length > 0).length ?? 0,
)
const pendingCount = computed(() => Math.max(0, (detail.value?.items.length ?? 0) - correctedCount.value))
const progressRate = computed(() => {
  if (!detail.value?.items.length) return 0
  return Math.round((correctedCount.value / detail.value.items.length) * 100)
})
const repliedDoubts = computed(() => doubts.value.filter((doubt) => doubt.status === 'REPLIED').length)
const latestItems = computed(() => (detail.value?.items ?? []).slice(0, 3))
const latestDoubt = computed(() => doubts.value[0] ?? null)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [handoutList, correctionPage, doubtPage] = await Promise.all([
      studentApi.listMyHandouts(),
      studentApi.listMyCorrections({ page: 1, pageSize: 100 }),
      studentApi.listMyDoubts({ page: 1, pageSize: 100 }),
    ])
    handouts.value = handoutList
    corrections.value = correctionPage.items
    doubts.value = doubtPage.items
    latest.value = handoutList[0] ?? null
    if (latest.value) {
      detail.value = await studentApi.getHandout(latest.value.id)
    } else {
      detail.value = null
    }
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

function openHandout(id: number): void {
  void router.push(`/student/handouts/${id}`)
}

function openLatest(): void {
  if (latest.value) openHandout(latest.value.id)
}

function showAiReserved(): void {
  ui.toast(
    'info',
    'AI 追问仅预留接口',
    '首版不开放学生自由问答；后续只允许针对本人已发布讲评追问，回答仍需教师可见与把关。',
  )
}

onMounted(load)
</script>

<template>
  <div class="student-dashboard">
    <PageHeader
      :eyebrow="`STUDENT SPACE · ${new Date().getFullYear()}`"
      :title="greeting"
      subtitle="这里只放你的错题、老师确认过的讲解，以及你自己的订正与疑问。没有排名，也没有别人的成绩。"
    >
      <template #actions>
        <RouterLink to="/student/handouts" class="btn btn--ghost btn--sm">
          <AppIcon name="book-open" :size="14" />
          全部讲评
        </RouterLink>
        <button class="btn btn--primary btn--sm" :disabled="!latest" @click="openLatest">
          <AppIcon name="arrow-right" :size="14" />
          打开最新讲解
        </button>
      </template>
    </PageHeader>

    <div v-if="loading" class="card loading-card">
      <span class="skeleton" style="width: 220px; height: 10px" />
    </div>

    <div v-else-if="error" class="card">
      <AppEmpty icon="alert-circle" title="学习空间加载失败" :description="error">
        <button class="btn btn--ghost btn--sm" @click="load">重试</button>
      </AppEmpty>
    </div>

    <template v-else-if="latest && detail">
      <section class="student-hero">
        <div class="hero-copy">
          <div class="hero-topline">
            <span class="hero-pill"><span />老师已发布</span>
            <span class="hero-date">{{ formatDateTime(latest.publishedAt) }}</span>
          </div>
          <h2>{{ latest.homeworkTitle }}</h2>
          <p>
            老师为你确认了 {{ latest.itemCount }} 条错题讲解。
            {{ pendingCount ? `还有 ${pendingCount} 题可以订正。` : '本次错题都已提交订正。' }}
          </p>
          <div class="hero-meta">
            <span><AppIcon name="book-open" :size="14" />{{ latest.itemCount }} 条讲解</span>
            <span><AppIcon name="check-circle" :size="14" />{{ correctedCount }} 题已订正</span>
            <span><AppIcon name="message" :size="14" />{{ latest.doubtCount }} 条疑问</span>
          </div>
          <div class="hero-actions">
            <button class="btn btn--primary" @click="openLatest">
              <AppIcon name="book-open" :size="15" />
              查看我的错题讲解
            </button>
            <RouterLink to="/student/corrections" class="btn btn--ghost">
              <AppIcon name="pen" :size="14" />
              我的订正
            </RouterLink>
          </div>
        </div>

        <div class="hero-visual">
          <div class="hero-ring">
            <svg viewBox="0 0 118 118">
              <circle cx="59" cy="59" r="48" fill="none" stroke="rgba(255, 255, 255, 0.52)" stroke-width="7" />
              <circle
                cx="59"
                cy="59"
                r="48"
                fill="none"
                stroke="var(--role)"
                stroke-width="7"
                stroke-linecap="round"
                :stroke-dasharray="2 * Math.PI * 48"
                :stroke-dashoffset="2 * Math.PI * 48 * (1 - progressRate / 100)"
                transform="rotate(-90 59 59)"
              />
            </svg>
            <div class="hero-ring__copy">
              <b>{{ correctedCount }}<small>/{{ latest.itemCount }}</small></b>
              <span>已订正</span>
            </div>
          </div>
          <div class="hero-note">
            <span class="hand">今天先把</span>
            <b v-if="pendingCount">第 {{ detail.items.find((item) => !item.corrections.length)?.seqNo }} 题</b>
            <b v-else>全部错题</b>
            <span class="hand">{{ pendingCount ? '弄懂' : '都订正好了' }}</span>
          </div>
          <div class="hero-doodle" aria-hidden="true">x = ?</div>
        </div>
      </section>

      <div class="grid grid--4 student-stats">
        <StatCard label="本次错题" :value="latest.itemCount" suffix="题" note="只属于你" icon="book-open" tone="peri" />
        <StatCard label="待订正" :value="pendingCount" suffix="题" :note="pendingCount ? '订正后自动记录' : '全部已提交'" icon="pen" tone="apricot" />
        <StatCard label="已提交订正" :value="correctedCount" suffix="题" note="提交记录保留版本" icon="check-circle" tone="mint" />
        <StatCard label="疑问回复" :value="repliedDoubts" suffix="条" note="老师回复可见" icon="message" tone="lav" />
      </div>

      <div class="grid grid--main-side student-main">
        <section class="card item-preview-card">
          <div class="section-head">
            <div>
              <h3 class="section-title">我的错题讲解</h3>
              <p class="section-desc">只有“已发布 + 发布就绪 + 本人错题”会出现在这里。</p>
            </div>
            <RouterLink to="/student/handouts" class="btn btn--quiet btn--sm">查看全部</RouterLink>
          </div>

          <div class="preview-list">
            <button
              v-for="item in latestItems"
              :key="item.itemId"
              type="button"
              class="preview-item"
              @click="openLatest"
            >
              <span class="preview-seq">{{ String(item.seqNo).padStart(2, '0') }}</span>
              <div class="preview-copy">
                <div class="preview-top">
                  <b>第 {{ item.seqNo }} 题</b>
                  <span class="mini-answer">我答 {{ item.myRecord.studentAnswer || '未作答' }}</span>
                  <span v-if="item.corrections.length" class="badge" data-tone="mint">已订正</span>
                  <span v-else class="badge" data-tone="apricot">待订正</span>
                  <span v-if="item.doubts.length" class="doubt-flag">
                    <AppIcon name="message" :size="10" />{{ item.doubts.length }}
                  </span>
                </div>
                <p class="preview-stem">{{ item.stem }}</p>
                <p class="preview-commentary">{{ item.commentary }}</p>
              </div>
              <AppIcon name="chevron-right" :size="15" class="preview-arrow" />
            </button>
          </div>
        </section>

        <aside class="student-side">
          <section class="correction-card">
            <div class="correction-card__head">
              <span class="correction-icon"><AppIcon name="pen" :size="17" /></span>
              <div>
                <span>订正节奏</span>
                <b>{{ progressRate }}% 完成</b>
              </div>
              <span class="correction-ring">{{ correctedCount }}/{{ latest.itemCount }}</span>
            </div>
            <div class="progress" style="margin-top: 10px">
              <span :style="{ width: `${progressRate}%` }" />
            </div>
            <p>
              {{ pendingCount ? '订正不是重抄答案，试着写出你这次是怎么想的。' : '本次错题都已提交，记得回看老师的讲解依据。' }}
            </p>
            <button class="btn btn--soft btn--sm btn--block" @click="openLatest">
              {{ pendingCount ? '继续订正' : '回看讲解' }}
              <AppIcon name="arrow-right" :size="13" />
            </button>
          </section>

          <section class="doubt-card">
            <div class="doubt-head">
              <span class="doubt-icon"><AppIcon name="message" :size="16" /></span>
              <div>
                <span>老师的回复</span>
                <b v-if="latestDoubt">{{ latestDoubt.status === 'REPLIED' ? '有一条新回复' : '等待老师回复' }}</b>
                <b v-else>暂时没有疑问</b>
              </div>
            </div>
            <template v-if="latestDoubt">
              <p class="doubt-question">“{{ latestDoubt.content }}”</p>
              <div v-if="latestDoubt.replies.length" class="teacher-reply">
                <span>{{ (latestDoubt.replies[0].teacherName || '老师').slice(0, 1) }}</span>
                <p>{{ latestDoubt.replies[0].content }}</p>
              </div>
              <p v-else class="doubt-wait">
                <AppIcon name="clock" :size="12" />
                {{ formatRelative(latestDoubt.createdAt) }}提交，老师回复后会显示在这里。
              </p>
              <RouterLink to="/student/doubts" class="doubt-link">查看我的全部疑问</RouterLink>
            </template>
            <p v-else class="doubt-wait">遇到不理解的一步，可以在讲评里向老师提问。</p>
          </section>

          <section class="ai-reserved">
            <div class="ai-reserved__head">
              <span class="ai-spark"><AppIcon name="sparkles" :size="15" /></span>
              <div>
                <b>AI 追问</b>
                <span>接口预留 · 首版不开放</span>
              </div>
              <span class="reserved-pill">RESERVED</span>
            </div>
            <p>
              将来可以针对本人已发布的讲评条目追问，回答仍需老师可见与把关。现在先和老师直接交流。
            </p>
            <button class="ai-fake-input" type="button" @click="showAiReserved">
              例：为什么移项要变号？
              <span>未开放</span>
            </button>
          </section>
        </aside>
      </div>

      <section class="past-handouts">
        <div class="past-head">
          <h3 class="section-title">最近讲评</h3>
          <span class="rule-ref">仅本人可见 · 不含排名对比</span>
        </div>
        <div v-if="handouts.length > 1" class="past-list">
          <button
            v-for="handout in handouts.slice(1, 4)"
            :key="handout.id"
            type="button"
            class="past-item"
            @click="openHandout(handout.id)"
          >
            <span class="past-icon"><AppIcon name="book-open" :size="16" /></span>
            <div>
              <b>{{ handout.homeworkTitle }}</b>
              <span>{{ formatRelative(handout.publishedAt) }} · {{ handout.itemCount }} 条讲解</span>
            </div>
            <span class="past-progress">{{ handout.correctedCount }}/{{ handout.itemCount }} 已订正</span>
            <AppIcon name="chevron-right" :size="14" />
          </button>
        </div>
        <p v-else class="past-empty">这是你的第一份已发布讲评。之后的历史讲评会在这里按时间排列。</p>
      </section>

      <div class="privacy-note">
        <AppIcon name="shield" :size="14" />
        这里没有排名、没有同学对比，也不会展示 AI 草稿或教师内部审核信息。所有演示数据均为虚构。
      </div>
    </template>

    <section v-else class="card">
      <AppEmpty
        icon="book-open"
        title="老师还没有发布讲评"
        description="已发布且经老师确认的错题讲解才会出现在你的空间。请稍后再来看看。"
      />
    </section>
  </div>
</template>

<style scoped>
.student-dashboard {
  max-width: 1220px;
}

/* Hero */
.student-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(260px, 0.55fr);
  gap: 20px;
  margin-bottom: 16px;
  padding: clamp(24px, 3vw, 34px);
  overflow: hidden;
  border: 1px solid #dde5fb;
  border-radius: 27px;
  background:
    radial-gradient(circle at 95% 8%, rgba(255, 255, 255, 0.92), transparent 32%),
    linear-gradient(124deg, #fdfefe 0%, #eef3ff 58%, #f5f1fd 100%);
  box-shadow: 0 24px 58px -46px rgba(70, 91, 170, 0.4);
}

.student-hero::before {
  content: 'Σ';
  position: absolute;
  right: 26%;
  bottom: -74px;
  color: rgba(107, 140, 245, 0.055);
  font-family: var(--font-numbers);
  font-size: 260px;
  line-height: 1;
  pointer-events: none;
}

.hero-copy {
  position: relative;
  z-index: 1;
}

.hero-topline {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hero-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 8px;
  border-radius: 99px;
  color: var(--peri-deep);
  background: rgba(255, 255, 255, 0.7);
  font-size: 8.5px;
  font-weight: 600;
}

.hero-pill span {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--peri);
}

.hero-date {
  color: var(--ink-4);
  font-size: 8.5px;
}

.hero-copy h2 {
  margin-top: 12px;
  font-family: var(--font-display);
  font-size: clamp(27px, 2.7vw, 37px);
  font-weight: 400;
  letter-spacing: 0.015em;
  line-height: 1.3;
}

.hero-copy > p {
  max-width: 56ch;
  margin-top: 7px;
  color: var(--ink-2);
  font-size: 12px;
  line-height: 1.8;
}

.hero-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 7px 16px;
  margin-top: 13px;
  color: var(--ink-3);
  font-size: 9.5px;
}

.hero-meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.hero-meta .icon {
  color: var(--peri);
  opacity: 0.75;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 20px;
}

.hero-visual {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 190px;
  border-left: 1px dashed rgba(101, 122, 186, 0.2);
  padding-left: 20px;
}

.hero-ring {
  position: relative;
  width: 140px;
  height: 140px;
}

.hero-ring svg {
  width: 100%;
  height: 100%;
}

.hero-ring__copy {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.hero-ring__copy b {
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 32px;
  font-weight: 600;
  line-height: 1;
}

.hero-ring__copy b small {
  color: var(--ink-3);
  font-family: var(--font-body);
  font-size: 11px;
  font-weight: 400;
}

.hero-ring__copy span {
  margin-top: 4px;
  color: var(--ink-3);
  font-size: 8px;
}

.hero-note {
  display: flex;
  align-items: baseline;
  gap: 5px;
  margin-top: 10px;
  color: var(--ink-3);
  font-size: 9px;
}

.hero-note b {
  color: var(--peri-deep);
  font-size: 11px;
}

.hero-doodle {
  position: absolute;
  right: 5px;
  bottom: 8px;
  color: rgba(107, 140, 245, 0.17);
  font-family: var(--font-serif);
  font-size: 23px;
  transform: rotate(-7deg);
}

.student-stats {
  margin-bottom: 16px;
}

.student-main {
  margin-bottom: 16px;
}

/* 错题列表 */
.item-preview-card {
  padding: 22px;
}

.preview-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.preview-item {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  width: 100%;
  padding: 12px 9px;
  border-radius: 11px;
  text-align: left;
  transition: 0.18s;
}

.preview-item:hover {
  background: var(--role-wash);
}

.preview-seq {
  display: grid;
  place-items: center;
  width: 29px;
  height: 29px;
  flex: none;
  border-radius: 8px;
  color: var(--peri-deep);
  background: var(--peri-soft);
  font-family: var(--font-numbers);
  font-size: 11px;
}

.preview-copy {
  min-width: 0;
  flex: 1;
}

.preview-top {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
}

.preview-top > b {
  color: var(--ink);
  font-size: 10px;
  font-weight: 600;
}

.mini-answer {
  color: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 7.5px;
}

.preview-top :deep(.badge) {
  min-height: 16px;
  padding: 0 5px;
  font-size: 7px;
}

.preview-top :deep(.badge::before) {
  display: none;
}

.doubt-flag {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: var(--lav-deep);
  font-size: 7.5px;
}

.preview-stem {
  display: -webkit-box;
  margin-top: 4px;
  overflow: hidden;
  color: var(--ink-2);
  font-family: var(--font-serif);
  font-size: 10.5px;
  line-height: 1.65;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
}

.preview-commentary {
  display: -webkit-box;
  margin-top: 3px;
  overflow: hidden;
  color: var(--ink-3);
  font-size: 9px;
  line-height: 1.65;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.preview-arrow {
  flex: none;
  margin-top: 7px;
  color: var(--ink-4);
}

/* 侧栏 */
.student-side {
  display: flex;
  flex-direction: column;
  gap: 11px;
}

.correction-card {
  padding: 17px;
  border: 1px solid #dce5fb;
  border-radius: 17px;
  background: linear-gradient(145deg, #fdfefe, #f0f4ff);
}

.correction-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.correction-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex: none;
  border-radius: 10px;
  color: var(--peri-deep);
  background: rgba(255, 255, 255, 0.7);
}

.correction-card__head div {
  flex: 1;
}

.correction-card__head span:not(.correction-icon):not(.correction-ring) {
  display: block;
  color: var(--ink-3);
  font-size: 8px;
}

.correction-card__head b {
  display: block;
  color: var(--ink);
  font-size: 11px;
}

.correction-ring {
  color: var(--peri-deep);
  font-family: var(--font-numbers);
  font-size: 15px;
  font-weight: 600;
}

.correction-card > p {
  margin: 8px 0 11px;
  color: var(--ink-3);
  font-size: 9px;
  line-height: 1.7;
}

.doubt-card {
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: #fff;
}

.doubt-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.doubt-icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  flex: none;
  border-radius: 9px;
  color: var(--lav-deep);
  background: var(--lav-soft);
}

.doubt-head span:not(.doubt-icon) {
  display: block;
  color: var(--ink-3);
  font-size: 8px;
}

.doubt-head b {
  display: block;
  color: var(--ink-2);
  font-size: 9.5px;
  font-weight: 600;
}

.doubt-question {
  margin-top: 9px;
  color: var(--ink-2);
  font-family: var(--font-serif);
  font-size: 9.5px;
  line-height: 1.75;
}

.teacher-reply {
  display: flex;
  gap: 6px;
  margin-top: 8px;
  padding: 8px;
  border-radius: 8px;
  background: #f4faf7;
}

.teacher-reply > span {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  flex: none;
  border-radius: 50%;
  color: var(--mint-deep);
  background: var(--mint-soft);
  font-size: 8px;
}

.teacher-reply p {
  color: #537067;
  font-size: 8.5px;
  line-height: 1.7;
}

.doubt-wait {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  margin-top: 8px;
  color: var(--ink-4);
  font-size: 8px;
  line-height: 1.6;
}

.doubt-link {
  display: inline-block;
  margin-top: 8px;
  color: var(--role-deep);
  font-size: 8.5px;
  font-weight: 600;
}

/* AI 预留 */
.ai-reserved {
  padding: 15px;
  border: 1px solid #e7e1f6;
  border-radius: 16px;
  background: linear-gradient(145deg, #f9f7fe, #f2f9f7);
}

.ai-reserved__head {
  display: flex;
  align-items: center;
  gap: 7px;
}

.ai-spark {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  flex: none;
  border-radius: 9px;
  color: var(--lav-deep);
  background: var(--lav-soft);
}

.ai-reserved__head div {
  flex: 1;
}

.ai-reserved__head b {
  display: block;
  color: var(--ink-2);
  font-size: 9.5px;
}

.ai-reserved__head span:not(.ai-spark):not(.reserved-pill) {
  display: block;
  color: var(--ink-4);
  font-size: 7.5px;
}

.reserved-pill {
  padding: 1px 5px;
  border-radius: 4px;
  color: var(--lav-deep);
  background: rgba(255, 255, 255, 0.7);
  font-family: var(--font-mono);
  font-size: 6.5px;
}

.ai-reserved > p {
  margin-top: 7px;
  color: var(--ink-3);
  font-size: 8.5px;
  line-height: 1.7;
}

.ai-fake-input {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  margin-top: 8px;
  padding: 8px 9px;
  border: 1px solid #e8e2f3;
  border-radius: 8px;
  color: #a7a0b8;
  background: rgba(255, 255, 255, 0.6);
  font-size: 8px;
  text-align: left;
}

.ai-fake-input span {
  color: var(--lav-deep);
  font-size: 7px;
}

/* 最近 */
.past-handouts {
  margin-top: 2px;
}

.past-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 9px;
  margin-bottom: 8px;
  padding: 0 3px;
}

.past-list {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.past-item {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 11px;
  background: rgba(255, 255, 255, 0.72);
  text-align: left;
  transition: 0.18s;
}

.past-item:hover {
  border-color: color-mix(in srgb, var(--role) 22%, var(--line));
  background: #fff;
}

.past-icon {
  display: grid;
  place-items: center;
  width: 29px;
  height: 29px;
  flex: none;
  border-radius: 8px;
  color: var(--peri-deep);
  background: var(--peri-soft);
}

.past-item > div {
  min-width: 0;
  flex: 1;
}

.past-item b {
  display: block;
  overflow: hidden;
  color: var(--ink-2);
  font-size: 9.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.past-item div span {
  display: block;
  margin-top: 1px;
  color: var(--ink-4);
  font-size: 7.5px;
}

.past-progress {
  flex: none;
  color: var(--peri-deep);
  font-family: var(--font-numbers);
  font-size: 9px;
}

.past-item > .icon {
  color: var(--ink-4);
}

.past-empty {
  padding: 11px 12px;
  border: 1px dashed var(--line);
  border-radius: 10px;
  color: var(--ink-4);
  font-size: 8.5px;
}

.privacy-note {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 14px;
  padding: 0 4px;
  color: var(--ink-4);
  font-size: 8.5px;
  line-height: 1.6;
}

.privacy-note .icon {
  flex: none;
  margin-top: 1px;
}

@media (max-width: 980px) {
  .student-hero {
    grid-template-columns: 1fr;
  }

  .hero-visual {
    flex-direction: row;
    justify-content: flex-start;
    gap: 20px;
    min-height: 0;
    padding-top: 16px;
    padding-left: 0;
    border-top: 1px dashed rgba(101, 122, 186, 0.2);
    border-left: 0;
  }

  .hero-ring {
    width: 110px;
    height: 110px;
  }

  .hero-ring__copy b {
    font-size: 26px;
  }

  .hero-note {
    margin-top: 0;
  }

  .student-side {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }

  .ai-reserved {
    grid-column: 1 / -1;
  }
}

@media (max-width: 640px) {
  .student-hero {
    padding: 20px 17px;
    border-radius: 21px;
  }

  .hero-copy h2 {
    font-size: 26px;
  }

  .hero-visual {
    flex-wrap: wrap;
    gap: 12px;
  }

  .hero-doodle {
    display: none;
  }

  .item-preview-card {
    padding: 17px;
  }

  .preview-item {
    padding: 10px 4px;
  }

  .preview-seq {
    width: 25px;
    height: 25px;
  }

  .mini-answer {
    display: none;
  }

  .student-side {
    display: flex;
  }

  .past-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .past-progress {
    display: none;
  }
}
</style>
