<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { teacherApi } from '@/api/teacher'
import type {
  CorrectionProgressItem,
  Doubt,
  HandoutDetail,
  HomeworkStatistics,
  HomeworkSummary,
} from '@/types/models'
import { useAuthStore } from '@/stores/auth'
import { useTeacherWorkspace } from '@/stores/teacherWorkspace'
import { useUiStore } from '@/stores/ui'
import { formatDateTime, formatRelative } from '@/utils/format'
import { QUESTION_TYPE } from '@/types/labels'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import AppIcon from '@/components/AppIcon.vue'
import AppEmpty from '@/components/AppEmpty.vue'

const auth = useAuthStore()
const workspace = useTeacherWorkspace()
const ui = useUiStore()
const router = useRouter()

const statistics = ref<HomeworkStatistics | null>(null)
const handout = ref<HandoutDetail | null>(null)
const doubts = ref<Doubt[]>([])
const pendingDoubtCount = ref(0)
const progressHomework = ref<HomeworkSummary | null>(null)
const progress = ref<CorrectionProgressItem[]>([])
const loading = ref(true)
const insightLoading = ref(false)

const greeting = computed(() => {
  const hour = new Date().getHours()
  const word = hour < 6 ? '夜深了' : hour < 11 ? '早上好' : hour < 14 ? '中午好' : hour < 19 ? '下午好' : '晚上好'
  return `${word}，${auth.displayName}`
})

const activeHomework = computed(() => workspace.activeHomework)
const publishedCount = computed(
  () => workspace.homeworks.filter((homework) => homework.status === 'PUBLISHED').length,
)

const pendingReviewCount = computed(() => {
  if (!handout.value) return 0
  return handout.value.pendingReviewCount + handout.value.blockedCount
})

const topQuestions = computed(() =>
  [...(statistics.value?.questions ?? [])].sort((a, b) => b.errorRate - a.errorRate),
)

const hotQuestion = computed(() => topQuestions.value[0] ?? null)
const weakPoint = computed(() => statistics.value?.knowledgePoints[0] ?? null)

const handoutReadyRatio = computed(() => {
  if (!handout.value || !handout.value.itemCount) return 0
  return Math.round((handout.value.readyCount / handout.value.itemCount) * 100)
})

const ringOffset = computed(() => {
  const circumference = 2 * Math.PI * 30
  return circumference * (1 - handoutReadyRatio.value / 100)
})

const reviewPath = computed(() =>
  activeHomework.value ? `/teacher/homeworks/${activeHomework.value.id}/review` : '/teacher/homeworks',
)

const statsPath = computed(() =>
  activeHomework.value
    ? `/teacher/homeworks/${activeHomework.value.id}/statistics`
    : '/teacher/homeworks',
)

const gradingPath = computed(() =>
  activeHomework.value ? `/teacher/homeworks/${activeHomework.value.id}/grading` : '/teacher/homeworks',
)

const queueItems = computed(() => {
  if (!handout.value) return []
  const priority: Record<string, number> = {
    AI_FAILED_MANUAL: 0,
    INSUFFICIENT_EVIDENCE: 1,
    DRAFT: 2,
    PENDING_REVIEW: 3,
    READY: 4,
    EXCLUDED: 5,
  }
  return [...handout.value.items]
    .filter((item) => item.status !== 'EXCLUDED')
    .sort((a, b) => (priority[a.status] ?? 9) - (priority[b.status] ?? 9) || a.seqNo - b.seqNo)
    .slice(0, 4)
})

const correctionTotals = computed(() => {
  const totalWrong = progress.value.reduce((sum, item) => sum + item.wrongCount, 0)
  const totalCorrected = progress.value.reduce((sum, item) => sum + item.correctedCount, 0)
  return {
    totalWrong,
    totalCorrected,
    rate: totalWrong ? Math.round((totalCorrected / totalWrong) * 100) : 0,
  }
})

const topPendingStudents = computed(() =>
  [...progress.value]
    .filter((item) => item.pendingCount > 0)
    .sort((a, b) => b.pendingCount - a.pendingCount)
    .slice(0, 3),
)

const currentStage = computed(() => {
  if (!activeHomework.value) return 0
  if (!activeHomework.value.currentGradingBatchId) return 2
  if (!handout.value) return 3
  if (handout.value.status === 'PUBLISHED') return 5
  return 4
})

const stageItems = computed(() => [
  { n: 1, label: '作业发布', note: activeHomework.value?.status === 'PUBLISHED' ? '已完成' : '待发布' },
  {
    n: 2,
    label: '批改导入',
    note: activeHomework.value?.currentGradingBatchVersion
      ? `批次 V${activeHomework.value.currentGradingBatchVersion}`
      : '待导入',
  },
  { n: 3, label: '错误统计', note: statistics.value ? '已生成' : '待计算' },
  {
    n: 4,
    label: '讲评审核',
    note: handout.value ? `${handout.value.readyCount}/${handout.value.itemCount} 就绪` : '未生成',
  },
  {
    n: 5,
    label: '发布 / 订正',
    note: handout.value?.status === 'PUBLISHED' ? '学生可见' : '未发布',
  },
])

function goCreate(): void {
  void router.push('/teacher/homeworks?create=1')
}

function goCorrections(): void {
  const id = progressHomework.value?.id ?? activeHomework.value?.id
  void router.push({ path: '/teacher/corrections', query: id ? { homework: String(id) } : {} })
}

function goReviewItem(): void {
  void router.push(reviewPath.value)
}

async function loadDashboard(force = false): Promise<void> {
  loading.value = true
  try {
    await workspace.load(force)
    await loadInsights()
  } finally {
    loading.value = false
  }
}

async function loadInsights(): Promise<void> {
  const homework = workspace.activeHomework
  if (!homework) {
    statistics.value = null
    handout.value = null
    return
  }
  insightLoading.value = true
  statistics.value = null
  handout.value = null

  const [statsResult, handoutResult, doubtResult] = await Promise.allSettled([
    homework.currentGradingBatchId ? teacherApi.getStatistics(homework.id) : Promise.reject(new Error('暂无批改')),
    homework.handoutId ? teacherApi.getHandout(homework.id) : Promise.reject(new Error('暂无讲评')),
    teacherApi.listDoubts({ status: 'PENDING', page: 1, pageSize: 4 }),
  ])

  if (statsResult.status === 'fulfilled') statistics.value = statsResult.value
  if (handoutResult.status === 'fulfilled') handout.value = handoutResult.value
  if (doubtResult.status === 'fulfilled') {
    doubts.value = doubtResult.value.items
    pendingDoubtCount.value = doubtResult.value.total
  } else {
    doubts.value = []
    pendingDoubtCount.value = 0
  }

  const visiblePublished = workspace.homeworks
    .filter((item) => ['PUBLISHED', 'WITHDRAWN'].includes(item.handoutStatus ?? ''))
    .sort((a, b) => (b.publishedAt ?? '').localeCompare(a.publishedAt ?? ''))
  const progressTarget =
    visiblePublished.find((item) => item.id === homework.id && item.handoutStatus === 'PUBLISHED') ??
    visiblePublished[0] ??
    null
  progressHomework.value = progressTarget
  if (progressTarget) {
    try {
      const page = await teacherApi.listCorrectionProgress(progressTarget.id, { page: 1, pageSize: 100 })
      progress.value = page.items
    } catch {
      progress.value = []
    }
  } else {
    progress.value = []
  }
  insightLoading.value = false
}

watch(
  () => workspace.activeHomeworkId,
  () => {
    void loadInsights()
  },
)

onMounted(() => {
  void loadDashboard()
})

function refresh(): void {
  ui.toast('info', '正在刷新总览', '将重新读取最新批改与讲评状态。')
  void loadDashboard(true)
}
</script>

<template>
  <div class="teacher-dashboard">
    <PageHeader
      :eyebrow="`TEACHER OVERVIEW · ${new Date().getFullYear()}`"
      :title="greeting"
      subtitle="今天先看哪一题？错误分布和周测口径都已就位，AI 只等你逐条确认。"
    >
      <template #actions>
        <button class="btn btn--ghost btn--sm" :disabled="loading" @click="refresh">
          <AppIcon name="refresh" :size="15" />
          刷新
        </button>
        <button class="btn btn--primary btn--sm" @click="goCreate">
          <AppIcon name="plus" :size="15" />
          创建作业
        </button>
      </template>
    </PageHeader>

    <div v-if="loading" class="dashboard-skeleton">
      <div class="skeleton skeleton-hero" />
      <div class="skeleton skeleton-row" />
      <div class="skeleton skeleton-main" />
    </div>

    <template v-else-if="activeHomework">
      <section class="focus-card">
        <div class="focus-card__texture" aria-hidden="true" />
        <div class="focus-card__copy">
          <div class="focus-topline">
            <span class="focus-label"><span class="dot" /> 当前处理</span>
            <span class="focus-class">{{ activeHomework.className }}</span>
            <StatusBadge :status="activeHomework.status" />
            <StatusBadge
              v-if="activeHomework.handoutStatus"
              :status="activeHomework.handoutStatus"
              plain
            />
          </div>
          <h2>{{ activeHomework.title }}</h2>
          <p>{{ activeHomework.description || '还没有填写作业说明，可在作业详情中补充。' }}</p>
          <div class="focus-meta">
            <span><AppIcon name="clipboard" :size="14" />{{ activeHomework.questionCount }} 道题</span>
            <span><AppIcon name="users" :size="14" />{{ activeHomework.studentCount }} 名学生</span>
            <span>
              <AppIcon name="upload" :size="14" />
              批改批次
              {{ activeHomework.currentGradingBatchVersion ? `V${activeHomework.currentGradingBatchVersion}` : '待导入' }}
            </span>
            <span v-if="activeHomework.dueAt">
              <AppIcon name="calendar" :size="14" />{{ formatDateTime(activeHomework.dueAt) }} 截止
            </span>
          </div>
          <div class="focus-actions">
            <button class="btn btn--primary" @click="goReviewItem">
              <AppIcon name="sparkles" :size="16" />
              {{ handout?.status === 'PUBLISHED' ? '查看已发布讲评' : '继续审核讲评' }}
            </button>
            <button class="btn btn--ghost" @click="router.push(gradingPath)">
              <AppIcon name="upload" :size="15" />
              批改与统计
            </button>
          </div>
        </div>

        <div class="focus-visual">
          <div class="readiness-ring">
            <svg viewBox="0 0 76 76">
              <circle cx="38" cy="38" r="30" fill="none" stroke="rgba(255,255,255,.5)" stroke-width="5" />
              <circle
                cx="38"
                cy="38"
                r="30"
                fill="none"
                stroke="var(--role)"
                stroke-width="5"
                stroke-linecap="round"
                :stroke-dasharray="2 * Math.PI * 30"
                :stroke-dashoffset="ringOffset"
                transform="rotate(-90 38 38)"
                class="ring-progress"
              />
            </svg>
            <div class="readiness-copy">
              <b>{{ handoutReadyRatio }}<small>%</small></b>
              <span>讲评就绪</span>
            </div>
          </div>
          <div class="focus-mini-stats">
            <div>
              <span>待处理</span>
              <b>{{ pendingReviewCount }}<small> 条</small></b>
            </div>
            <div>
              <span>最高错误率</span>
              <b>{{ hotQuestion ? hotQuestion.errorRate.toFixed(0) : '—' }}<small>%</small></b>
            </div>
          </div>
          <div v-if="hotQuestion" class="focus-hot-note">
            <span class="hand">本轮先讲</span>
            <b>第 {{ hotQuestion.seqNo }} 题</b>
            <span>{{ hotQuestion.wrongCount }} 人答错</span>
          </div>
        </div>
      </section>

      <div class="stage-line">
        <div
          v-for="stage in stageItems"
          :key="stage.n"
          class="stage-item"
          :class="{
            'is-done': stage.n < currentStage,
            'is-current': stage.n === currentStage,
          }"
        >
          <span class="stage-n">{{ stage.n < currentStage ? '✓' : stage.n }}</span>
          <div>
            <b>{{ stage.label }}</b>
            <small>{{ stage.note }}</small>
          </div>
        </div>
      </div>

      <div class="grid grid--4 stats-grid">
        <StatCard
          label="进行中作业"
          :value="publishedCount"
          suffix="份"
          note="本班已发布"
          icon="clipboard"
          tone="mint"
          trend="本学期累计"
        />
        <StatCard
          label="当前批改"
          :value="activeHomework.currentGradingBatchVersion ? activeHomework.studentCount : '—'"
          :suffix="activeHomework.currentGradingBatchVersion ? '人' : ''"
          :note="activeHomework.currentGradingBatchVersion ? `批次 V${activeHomework.currentGradingBatchVersion} · 已重算统计` : '尚未导入批改'"
          icon="upload"
          tone="peri"
        />
        <StatCard
          label="待审核条目"
          :value="pendingReviewCount"
          suffix="条"
          :note="handout ? `${handout.readyCount} 条已就绪 / ${handout.itemCount} 条` : '尚未生成讲评'"
          icon="sparkles"
          tone="lav"
          :trend="pendingReviewCount ? '需要你逐条确认' : '已清空'"
        />
        <StatCard
          label="待回复疑问"
          :value="pendingDoubtCount"
          suffix="条"
          note="仅本班学生"
          icon="message"
          tone="apricot"
          :trend="pendingDoubtCount ? '优先回应第 5 题' : '暂无待回复'"
        />
      </div>

      <div class="grid grid--main-side dashboard-mid">
        <section class="card chart-card">
          <div class="section-head">
            <div>
              <h3 class="section-title">按题目错误分布</h3>
              <p class="section-desc">
                统计只读，人工修订原始记录后自动重算。重点讲评阈值暂定 ≥ 40%。
              </p>
            </div>
            <RouterLink :to="statsPath" class="btn btn--ghost btn--sm">
              完整统计
              <AppIcon name="arrow-right" :size="14" />
            </RouterLink>
          </div>

          <div v-if="statistics" class="chart-body">
            <div class="chart-legend">
              <span><i class="legend-dot legend-dot--hot" />重点错题</span>
              <span><i class="legend-dot" />常规题目</span>
              <span class="chart-batch">批次 V{{ statistics.batchVersionNo }}</span>
            </div>
            <div class="hbar-list">
              <div
                v-for="question in statistics.questions"
                :key="question.questionId"
                class="hbar"
              >
                <span class="hbar__label">
                  <b>第{{ question.seqNo }}题</b>
                  <small>{{ QUESTION_TYPE[question.questionType].label }}</small>
                </span>
                <div class="hbar__track">
                  <div
                    class="hbar__fill"
                    :class="{ 'is-hot': question.errorRate >= 40 }"
                    :style="{ width: `${Math.max(question.errorRate, 2)}%` }"
                  />
                </div>
                <span class="hbar__meta">
                  <b>{{ question.errorRate.toFixed(0) }}%</b>
                  <span>{{ question.wrongCount }}/{{ question.totalCount }}</span>
                </span>
              </div>
            </div>
            <div v-if="hotQuestion" class="chart-insight">
              <span class="insight-icon"><AppIcon name="target" :size="17" /></span>
              <div>
                <b>优先讲第 {{ hotQuestion.seqNo }} 题</b>
                <p>
                  {{ hotQuestion.wrongCount }} 人答错，占 {{ hotQuestion.errorRate.toFixed(1) }}%；
                  {{ weakPoint ? `关联薄弱点「${weakPoint.name}」错误率 ${weakPoint.errorRate.toFixed(0)}%。` : '可结合典型错误集中讲评。' }}
                </p>
              </div>
              <button class="btn btn--soft btn--sm" @click="goReviewItem">查看草稿</button>
            </div>
          </div>
          <AppEmpty
            v-else
            icon="chart"
            title="还没有错误分布"
            description="先导入本班批改数据，系统会自动按题目与知识点重算，无需手工修改统计数字。"
          >
            <button class="btn btn--primary btn--sm" @click="router.push(gradingPath)">
              去导入批改
            </button>
          </AppEmpty>
        </section>

        <section class="card review-queue">
          <div class="section-head">
            <div>
              <h3 class="section-title">讲评审核台</h3>
              <p class="section-desc">逐条确认；无依据与 AI 失败绝不自动给结论。</p>
            </div>
            <StatusBadge
              v-if="handout"
              :status="handout.status"
            />
          </div>

          <div v-if="handout" class="queue-progress">
            <div class="row row--between">
              <span>发布就绪</span>
              <b>{{ handout.readyCount }} / {{ handout.itemCount }}</b>
            </div>
            <div class="progress">
              <span :style="{ width: `${handoutReadyRatio}%` }" />
            </div>
            <p v-if="!handout.publishable && handout.blockingReasons.length">
              {{ handout.blockingReasons.join(' · ') }}
            </p>
            <p v-else class="is-ready">
              <AppIcon name="check-circle" :size="13" />
              已满足发布条件，学生端仍需你点击发布后才可见。
            </p>
          </div>

          <div v-if="queueItems.length" class="queue-list">
            <button
              v-for="item in queueItems"
              :key="item.id"
              type="button"
              class="queue-item"
              @click="goReviewItem"
            >
              <span class="queue-seq">{{ String(item.seqNo).padStart(2, '0') }}</span>
              <div class="queue-copy">
                <b>{{ item.stem }}</b>
                <span>{{ item.status === 'READY' ? '已通过教师确认' : '等待处理 · 点击进入审核' }}</span>
              </div>
              <StatusBadge :status="item.status" />
            </button>
          </div>
          <AppEmpty
            v-else-if="!handout"
            icon="sparkles"
            title="生成第一版讲评草稿"
            description="基于当前批改、题目与知识点快照，AI 只产出候选，等待你逐条确认。"
          >
            <button class="btn btn--primary btn--sm" @click="goReviewItem">去生成草稿</button>
          </AppEmpty>
          <AppEmpty
            v-else
            icon="check"
            title="条目都已处理"
            description="查看已发布内容，或等待学生提交订正与疑问。"
          >
            <button class="btn btn--soft btn--sm" @click="goReviewItem">打开审核台</button>
          </AppEmpty>

          <button class="queue-footer" type="button" @click="goReviewItem">
            打开完整审核台
            <AppIcon name="arrow-right" :size="15" />
          </button>
        </section>
      </div>

      <div class="grid grid--2 dashboard-bottom">
        <section class="card correction-card">
          <div class="section-head">
            <div>
              <h3 class="section-title">订正与掌握</h3>
              <p class="section-desc">
                {{ progressHomework ? `《${progressHomework.title}》 · 撤回讲评不会删除已交订正` : '发布讲评后开始记录' }}
              </p>
            </div>
            <button class="btn btn--ghost btn--sm" :disabled="!progressHomework" @click="goCorrections">
              查看明细
            </button>
          </div>

          <template v-if="progressHomework">
            <div class="correction-summary">
              <div class="donut-wrap">
                <svg viewBox="0 0 90 90">
                  <circle cx="45" cy="45" r="35" fill="none" stroke="#edf4f1" stroke-width="7" />
                  <circle
                    cx="45"
                    cy="45"
                    r="35"
                    fill="none"
                    stroke="var(--role)"
                    stroke-width="7"
                    stroke-linecap="round"
                    :stroke-dasharray="2 * Math.PI * 35"
                    :stroke-dashoffset="2 * Math.PI * 35 * (1 - correctionTotals.rate / 100)"
                    transform="rotate(-90 45 45)"
                  />
                </svg>
                <div class="donut-label">
                  <b>{{ correctionTotals.rate }}<small>%</small></b>
                  <span>完成率</span>
                </div>
              </div>
              <div class="correction-figures">
                <div>
                  <span>需订正学生</span>
                  <b>{{ progress.length }}<small> 人</small></b>
                </div>
                <div>
                  <span>已提交订正</span>
                  <b>{{ correctionTotals.totalCorrected }}<small> / {{ correctionTotals.totalWrong }} 题</small></b>
                </div>
                <div>
                  <span>仍有待订正</span>
                  <b class="warm">{{ correctionTotals.totalWrong - correctionTotals.totalCorrected }}<small> 题</small></b>
                </div>
              </div>
            </div>

            <div v-if="topPendingStudents.length" class="pending-list">
              <p class="pending-list__label">优先提醒</p>
              <div v-for="student in topPendingStudents" :key="student.studentId" class="pending-row">
                <span class="avatar avatar--sm">{{ student.studentName.slice(0, 1) }}</span>
                <div class="pending-row__copy">
                  <b>{{ student.studentName }}</b>
                  <span>{{ student.correctedCount }} / {{ student.wrongCount }} 题已订正</span>
                </div>
                <div class="pending-row__bar">
                  <span :style="{ width: `${student.completionRate}%` }" />
                </div>
                <em>{{ student.pendingCount }} 题待订正</em>
              </div>
            </div>
          </template>
          <AppEmpty
            v-else
            icon="clipboard-check"
            title="还没有可跟踪的已发布讲评"
            description="讲评发布后，学生提交的订正会自动汇总到本班视线，跨班内容不可见。"
          />
        </section>

        <section class="card doubt-card">
          <div class="section-head">
            <div>
              <h3 class="section-title">学生疑问箱</h3>
              <p class="section-desc">只来自本班、只针对学生本人可见的讲评条目。</p>
            </div>
            <RouterLink to="/teacher/doubts" class="btn btn--ghost btn--sm">全部疑问</RouterLink>
          </div>

          <div v-if="doubts.length" class="doubt-list">
            <article v-for="doubt in doubts" :key="doubt.id" class="doubt-item">
              <span class="avatar avatar--sm">{{ doubt.studentName.slice(0, 1) }}</span>
              <div class="doubt-item__copy">
                <div class="doubt-item__top">
                  <b>{{ doubt.studentName }}</b>
                  <span>第 {{ doubt.seqNo }} 题 · {{ formatRelative(doubt.createdAt) }}</span>
                  <StatusBadge :status="doubt.status" plain />
                </div>
                <p>{{ doubt.content }}</p>
              </div>
              <RouterLink
                :to="{ path: '/teacher/doubts', query: { focus: String(doubt.id) } }"
                class="doubt-reply-link"
              >
                回复
                <AppIcon name="arrow-right" :size="13" />
              </RouterLink>
            </article>
          </div>
          <AppEmpty
            v-else
            icon="message"
            title="疑问箱目前是空的"
            description="学生针对已发布讲评提交的问题会出现在这里，回复会同步写入审计日志。"
          />

          <div class="doubt-footnote">
            <AppIcon name="shield" :size="13" />
            学生之间互不可见；AI 不能代替教师直接答复。
          </div>
        </section>
      </div>
    </template>

    <section v-else class="card">
      <AppEmpty
        icon="clipboard"
        title="先创建一份作业"
        description="作业草稿 → 添加题目 → 发布 → 导入批改，之后才能生成讲评。"
      >
        <button class="btn btn--primary" @click="goCreate">创建作业</button>
      </AppEmpty>
    </section>

    <div v-if="insightLoading" class="insight-loading">
      <span class="skeleton" />
      正在同步讲评与订正状态…
    </div>
  </div>
</template>

<style scoped>
.teacher-dashboard {
  max-width: 1280px;
}

.dashboard-skeleton {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.skeleton-hero {
  height: 180px;
  border-radius: 24px;
}

.skeleton-row {
  height: 120px;
}

.skeleton-main {
  height: 340px;
}

/* 当前作业焦点卡 */
.focus-card {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(260px, 0.65fr);
  gap: 20px;
  margin-bottom: 18px;
  padding: clamp(24px, 3vw, 34px);
  overflow: hidden;
  border: 1px solid rgba(205, 233, 223, 0.9);
  border-radius: 26px;
  background:
    radial-gradient(circle at 92% 15%, rgba(255, 255, 255, 0.88), transparent 26%),
    linear-gradient(128deg, #fbfefd 0%, #eaf9f4 54%, #eef3ff 100%);
  box-shadow: 0 24px 58px -44px rgba(35, 92, 76, 0.42);
}

.focus-card__texture {
  position: absolute;
  inset: 0;
  opacity: 0.5;
  background-image: radial-gradient(rgba(63, 126, 107, 0.13) 0.8px, transparent 0.8px);
  background-size: 19px 19px;
  mask-image: linear-gradient(to right, transparent 18%, #000 75%);
  pointer-events: none;
}

.focus-card__copy {
  position: relative;
  z-index: 1;
}

.focus-topline {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.focus-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--mint-deep);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.12em;
}

.focus-label .dot {
  background: var(--mint);
  box-shadow: 0 0 0 3px rgba(47, 184, 173, 0.13);
}

.focus-class {
  color: var(--ink-3);
  font-size: 10.5px;
}

.focus-card__copy h2 {
  margin-top: 13px;
  font-family: var(--font-display);
  font-size: clamp(25px, 2.5vw, 35px);
  font-weight: 400;
  letter-spacing: 0.015em;
  line-height: 1.3;
}

.focus-card__copy > p {
  max-width: 62ch;
  margin-top: 7px;
  color: var(--ink-2);
  font-size: 12.5px;
  line-height: 1.8;
}

.focus-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 7px 16px;
  margin-top: 14px;
  color: var(--ink-3);
  font-size: 10.5px;
}

.focus-meta span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.focus-meta .icon {
  color: var(--mint);
  opacity: 0.8;
}

.focus-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 20px;
}

.focus-visual {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  padding-left: 20px;
  border-left: 1px dashed rgba(78, 143, 123, 0.2);
}

.readiness-ring {
  position: relative;
  width: 126px;
  height: 126px;
}

.readiness-ring svg {
  width: 100%;
  height: 100%;
}

.ring-progress {
  transition: stroke-dashoffset 1s var(--ease-out);
}

.readiness-copy {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.readiness-copy b {
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 27px;
  font-weight: 600;
  line-height: 1;
}

.readiness-copy b small {
  font-family: var(--font-body);
  font-size: 11px;
  font-weight: 500;
}

.readiness-copy span {
  margin-top: 4px;
  color: var(--ink-3);
  font-size: 9px;
  letter-spacing: 0.05em;
}

.focus-mini-stats {
  display: flex;
  gap: 22px;
  margin-top: 13px;
}

.focus-mini-stats div {
  text-align: center;
}

.focus-mini-stats span {
  display: block;
  color: var(--ink-3);
  font-size: 8.5px;
}

.focus-mini-stats b {
  display: block;
  margin-top: 1px;
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 18px;
  font-weight: 600;
}

.focus-mini-stats small {
  font-family: var(--font-body);
  font-size: 8px;
  font-weight: 400;
}

.focus-hot-note {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  padding: 6px 10px;
  border-radius: 7px;
  background: rgba(255, 255, 255, 0.54);
  transform: rotate(-1deg);
}

.focus-hot-note span {
  color: var(--ink-3);
  font-size: 8.5px;
}

.focus-hot-note b {
  color: var(--coral-deep);
  font-size: 10px;
}

/* 流程 */
.stage-line {
  display: flex;
  align-items: center;
  gap: 0;
  margin: 2px 0 18px;
  padding: 11px 18px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.62);
  overflow-x: auto;
}

.stage-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 130px;
  padding-right: 16px;
}

.stage-item::after {
  content: '';
  position: absolute;
  top: 50%;
  right: 8px;
  width: 14px;
  height: 1px;
  background: var(--line-strong);
}

.stage-item:last-child::after {
  display: none;
}

.stage-n {
  display: grid;
  place-items: center;
  width: 23px;
  height: 23px;
  flex: none;
  border: 1px solid var(--line-strong);
  border-radius: 50%;
  color: var(--ink-4);
  background: #fff;
  font-family: var(--font-numbers);
  font-size: 10px;
}

.stage-item.is-done .stage-n {
  border-color: transparent;
  color: var(--mint-deep);
  background: var(--mint-soft);
}

.stage-item.is-current .stage-n {
  border-color: transparent;
  color: #fff;
  background: var(--role);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--role) 12%, transparent);
}

.stage-item b {
  display: block;
  color: var(--ink-3);
  font-size: 10.5px;
  font-weight: 600;
  white-space: nowrap;
}

.stage-item.is-done b,
.stage-item.is-current b {
  color: var(--ink);
}

.stage-item small {
  display: block;
  margin-top: 1px;
  color: var(--ink-4);
  font-size: 8.5px;
  white-space: nowrap;
}

.stats-grid {
  margin-bottom: 18px;
}

.dashboard-mid {
  margin-bottom: 18px;
  align-items: stretch;
}

/* 图表 */
.chart-card,
.review-queue,
.correction-card,
.doubt-card {
  padding: 23px;
}

.chart-body {
  min-height: 200px;
}

.chart-legend {
  display: flex;
  align-items: center;
  gap: 15px;
  margin-bottom: 7px;
  color: var(--ink-3);
  font-size: 9px;
}

.chart-legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.legend-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--mint);
}

.legend-dot--hot {
  background: var(--coral);
}

.chart-batch {
  margin-left: auto;
  font-family: var(--font-mono);
  font-size: 8.5px;
}

.hbar-list {
  display: flex;
  flex-direction: column;
}

.hbar {
  grid-template-columns: 84px minmax(110px, 1fr) 92px;
  padding: 6px 0;
}

.hbar__label {
  display: flex;
  align-items: baseline;
  gap: 5px;
}

.hbar__label b {
  color: var(--ink-2);
  font-size: 10.5px;
  font-weight: 600;
}

.hbar__label small {
  color: var(--ink-4);
  font-size: 8px;
}

.hbar__meta b {
  font-size: 12px;
}

.hbar__meta span {
  font-family: var(--font-mono);
  font-size: 8px;
}

.chart-insight {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 13px;
  padding: 10px 12px;
  border: 1px solid #f0e1cf;
  border-radius: 11px;
  background: var(--apricot-soft);
}

.insight-icon {
  display: grid;
  place-items: center;
  width: 29px;
  height: 29px;
  flex: none;
  border-radius: 9px;
  color: var(--apricot-deep);
  background: rgba(255, 255, 255, 0.56);
}

.chart-insight div {
  min-width: 0;
  flex: 1;
}

.chart-insight b {
  display: block;
  color: #8f6733;
  font-size: 10.5px;
}

.chart-insight p {
  margin-top: 1px;
  color: #a2855d;
  font-size: 9.5px;
  line-height: 1.6;
}

.chart-insight .btn {
  flex: none;
}

/* 审核队列 */
.queue-progress {
  margin-bottom: 12px;
  padding: 11px 12px;
  border-radius: 11px;
  background: var(--surface-soft);
}

.queue-progress .row {
  margin-bottom: 6px;
  color: var(--ink-3);
  font-size: 10px;
}

.queue-progress .row b {
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 13px;
  font-weight: 600;
}

.queue-progress p {
  margin-top: 6px;
  color: var(--ink-3);
  font-size: 9px;
  line-height: 1.6;
}

.queue-progress p.is-ready {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--mint-deep);
}

.queue-list {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.queue-item {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 8px 7px;
  border-radius: 9px;
  text-align: left;
  transition: background 0.18s;
}

.queue-item:hover {
  background: var(--surface-soft);
}

.queue-seq {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  flex: none;
  border-radius: 8px;
  color: var(--ink-3);
  background: #f4f8f6;
  font-family: var(--font-numbers);
  font-size: 10px;
}

.queue-copy {
  min-width: 0;
  flex: 1;
}

.queue-copy b {
  display: block;
  overflow: hidden;
  color: var(--ink-2);
  font-size: 10.5px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.queue-copy span {
  display: block;
  margin-top: 1px;
  color: var(--ink-4);
  font-size: 8.5px;
}

.queue-item :deep(.badge) {
  min-height: 19px;
  padding: 0 6px;
  font-size: 8.5px;
}

.queue-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  margin-top: 12px;
  padding-top: 11px;
  border-top: 1px dashed var(--line);
  color: var(--role-deep);
  font-size: 10px;
  font-weight: 600;
}

/* 订正 */
.correction-summary {
  display: flex;
  align-items: center;
  gap: 22px;
  padding: 4px 0 14px;
}

.donut-wrap {
  position: relative;
  width: 102px;
  height: 102px;
  flex: none;
}

.donut-wrap svg {
  width: 100%;
  height: 100%;
}

.donut-label {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.donut-label b {
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 24px;
  font-weight: 600;
}

.donut-label b small {
  font-family: var(--font-body);
  font-size: 9px;
}

.donut-label span {
  color: var(--ink-3);
  font-size: 8px;
}

.correction-figures {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  flex: 1;
}

.correction-figures div {
  padding: 8px 10px;
  border-radius: 9px;
  background: var(--surface-soft);
}

.correction-figures span {
  display: block;
  color: var(--ink-3);
  font-size: 8.5px;
}

.correction-figures b {
  display: block;
  margin-top: 2px;
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 18px;
  font-weight: 600;
}

.correction-figures b.warm {
  color: var(--coral-deep);
}

.correction-figures small {
  font-family: var(--font-body);
  font-size: 8px;
  font-weight: 400;
}

.pending-list {
  padding-top: 10px;
  border-top: 1px dashed var(--line);
}

.pending-list__label {
  margin-bottom: 3px;
  color: var(--ink-4);
  font-size: 8.5px;
  letter-spacing: 0.08em;
}

.pending-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 0;
}

.pending-row__copy {
  width: 84px;
  flex: none;
}

.pending-row__copy b {
  display: block;
  overflow: hidden;
  color: var(--ink-2);
  font-size: 10px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pending-row__copy span {
  display: block;
  color: var(--ink-4);
  font-size: 8px;
}

.pending-row__bar {
  height: 5px;
  flex: 1;
  overflow: hidden;
  border-radius: 999px;
  background: #f0f5f2;
}

.pending-row__bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #9edbd0;
}

.pending-row em {
  flex: none;
  color: var(--apricot-deep);
  font-size: 8.5px;
  font-style: normal;
}

/* 疑问 */
.doubt-list {
  display: flex;
  flex-direction: column;
}

.doubt-item {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 9px 0;
  border-bottom: 1px dashed var(--line);
}

.doubt-item:first-child {
  padding-top: 2px;
}

.doubt-item:last-child {
  border-bottom: 0;
}

.doubt-item__copy {
  min-width: 0;
  flex: 1;
}

.doubt-item__top {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
}

.doubt-item__top b {
  color: var(--ink);
  font-size: 10.5px;
}

.doubt-item__top span {
  color: var(--ink-4);
  font-size: 8.5px;
}

.doubt-item__top :deep(.badge) {
  min-height: 18px;
  padding: 0 6px;
  font-size: 8px;
}

.doubt-item__copy > p {
  display: -webkit-box;
  margin-top: 2px;
  overflow: hidden;
  color: var(--ink-2);
  font-size: 10px;
  line-height: 1.65;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.doubt-reply-link {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex: none;
  margin-top: 2px;
  color: var(--role-deep);
  font-size: 9px;
  font-weight: 600;
}

.doubt-footnote {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  margin-top: 9px;
  padding-top: 9px;
  border-top: 1px dashed var(--line);
  color: var(--ink-4);
  font-size: 8.5px;
  line-height: 1.6;
}

.doubt-footnote .icon {
  flex: none;
  margin-top: 1px;
}

.insight-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  padding: 18px;
  color: var(--ink-3);
  font-size: 10px;
}

.insight-loading span {
  width: 60px;
  height: 8px;
}

@media (max-width: 1120px) {
  .focus-card {
    grid-template-columns: minmax(0, 1.5fr) minmax(230px, 0.6fr);
  }

  .focus-visual {
    padding-left: 12px;
  }

  .stage-item {
    min-width: 118px;
  }
}

@media (max-width: 900px) {
  .focus-card {
    grid-template-columns: 1fr;
  }

  .focus-visual {
    flex-direction: row;
    justify-content: flex-start;
    gap: 24px;
    min-height: 0;
    padding-top: 16px;
    padding-left: 0;
    border-top: 1px dashed rgba(78, 143, 123, 0.2);
    border-left: 0;
  }

  .focus-mini-stats {
    margin-top: 0;
  }

  .focus-hot-note {
    margin-top: 0;
    margin-left: auto;
  }

  .correction-summary {
    gap: 14px;
  }

  .correction-figures {
    gap: 6px;
  }
}

@media (max-width: 640px) {
  .focus-card {
    padding: 20px 17px;
    border-radius: 20px;
  }

  .focus-card__copy h2 {
    font-size: 25px;
  }

  .focus-actions {
    width: 100%;
  }

  .focus-actions .btn {
    flex: 1;
  }

  .focus-visual {
    flex-wrap: wrap;
    gap: 16px;
  }

  .readiness-ring {
    width: 100px;
    height: 100px;
  }

  .readiness-copy b {
    font-size: 22px;
  }

  .focus-hot-note {
    width: 100%;
    margin-left: 0;
  }

  .stage-line {
    padding: 10px 12px;
  }

  .stage-item {
    min-width: 100px;
    padding-right: 10px;
  }

  .stage-item b {
    font-size: 9.5px;
  }

  .stage-item small {
    font-size: 8px;
  }

  .chart-card,
  .review-queue,
  .correction-card,
  .doubt-card {
    padding: 17px;
  }

  .chart-insight {
    flex-wrap: wrap;
  }

  .chart-insight .btn {
    width: 100%;
  }

  .correction-summary {
    flex-direction: column;
    align-items: stretch;
  }

  .donut-wrap {
    margin: 0 auto;
  }

  .correction-figures {
    grid-template-columns: repeat(3, 1fr);
  }

  .pending-row__copy {
    width: 68px;
  }

  .pending-row em {
    display: none;
  }
}
</style>
