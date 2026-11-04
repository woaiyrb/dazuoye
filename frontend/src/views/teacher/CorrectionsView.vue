<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { teacherApi } from '@/api/teacher'
import { errorMessage } from '@/api/errors'
import type {
  CorrectionProgressItem,
  CorrectionRecord,
  QuestionCorrectionProgress,
} from '@/types/models'
import { useTeacherWorkspace } from '@/stores/teacherWorkspace'
import { formatDateTime, formatRelative } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'
import AppIcon from '@/components/AppIcon.vue'
import AppEmpty from '@/components/AppEmpty.vue'
import AppPagination from '@/components/AppPagination.vue'
import { fetchAllPages } from '@/utils/pagination'

const PROGRESS_PAGE_SIZE = 10
const QUESTION_PAGE_SIZE = 10
const RECORDS_PAGE_SIZE = 10

const route = useRoute()
const router = useRouter()
const workspace = useTeacherWorkspace()

const loading = ref(true)
const loadingDetail = ref(false)
const error = ref('')
const selectedHomeworkId = ref<number | null>(null)
const progress = ref<CorrectionProgressItem[]>([])
const questionProgress = ref<QuestionCorrectionProgress[]>([])
const corrections = ref<CorrectionRecord[]>([])
const tab = ref<'progress' | 'questions' | 'records'>('progress')
const search = ref('')
const completionFilter = ref<'ALL' | 'PENDING' | 'DONE'>('ALL')
const focusedStudentId = ref<number | null>(null)
const progressPage = ref(1)
const questionPage = ref(1)
const recordsPage = ref(1)
const focusedQuestionId = ref<number | null>(null)
const truncated = ref(false)

const eligibleHomeworks = computed(() =>
  workspace.homeworks.filter((homework) =>
    ['PUBLISHED', 'WITHDRAWN'].includes(homework.handoutStatus ?? ''),
  ),
)
const selectedHomework = computed(
  () => eligibleHomeworks.value.find((homework) => homework.id === selectedHomeworkId.value) ?? null,
)
const totals = computed(() => {
  const wrong = progress.value.reduce((sum, item) => sum + item.wrongCount, 0)
  const corrected = progress.value.reduce((sum, item) => sum + item.correctedCount, 0)
  return {
    wrong,
    corrected,
    pending: Math.max(0, wrong - corrected),
    rate: wrong ? Math.round((corrected / wrong) * 100) : 0,
    students: progress.value.length,
    doneStudents: progress.value.filter((item) => item.pendingCount === 0).length,
  }
})
const filteredProgress = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return progress.value
    .filter((item) => {
      if (completionFilter.value === 'PENDING') return item.pendingCount > 0
      if (completionFilter.value === 'DONE') return item.pendingCount === 0
      return true
    })
    .filter(
      (item) =>
        !keyword ||
        item.studentName.toLowerCase().includes(keyword) ||
        item.studentNo.toLowerCase().includes(keyword),
    )
    .filter((item) => !focusedStudentId.value || item.studentId === focusedStudentId.value)
})
const filteredCorrections = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return corrections.value
    .filter(
      (item) =>
        !keyword ||
        item.studentName.toLowerCase().includes(keyword) ||
        item.studentNo.toLowerCase().includes(keyword) ||
        String(item.seqNo).includes(keyword),
    )
    .filter((item) => !focusedStudentId.value || item.studentId === focusedStudentId.value)
    .filter((item) => !focusedQuestionId.value || item.questionId === focusedQuestionId.value)
})
const pagedProgress = computed(() => {
  const start = (progressPage.value - 1) * PROGRESS_PAGE_SIZE
  return filteredProgress.value.slice(start, start + PROGRESS_PAGE_SIZE)
})
const pagedQuestions = computed(() => {
  const start = (questionPage.value - 1) * QUESTION_PAGE_SIZE
  return questionProgress.value.slice(start, start + QUESTION_PAGE_SIZE)
})
const pagedCorrections = computed(() => {
  const start = (recordsPage.value - 1) * RECORDS_PAGE_SIZE
  return filteredCorrections.value.slice(start, start + RECORDS_PAGE_SIZE)
})

async function loadHomeworkData(): Promise<void> {
  if (!selectedHomeworkId.value) {
    progress.value = []
    corrections.value = []
    return
  }
  loadingDetail.value = true
  error.value = ''
  try {
    const [progressResult, questionResult, correctionResult] = await Promise.all([
      fetchAllPages((page, pageSize) =>
        teacherApi.listCorrectionProgress(selectedHomeworkId.value!, { page, pageSize }),
      ),
      fetchAllPages((page, pageSize) =>
        teacherApi.listQuestionCorrectionProgress(selectedHomeworkId.value!, { page, pageSize }),
      ),
      fetchAllPages((page, pageSize) =>
        teacherApi.listCorrections(selectedHomeworkId.value!, { page, pageSize }),
      ),
    ])
    progress.value = progressResult.items
    questionProgress.value = questionResult.items
    corrections.value = correctionResult.items
    truncated.value = progressResult.truncated || questionResult.truncated || correctionResult.truncated
    progressPage.value = 1
    questionPage.value = 1
    recordsPage.value = 1
  } catch (cause) {
    error.value = errorMessage(cause)
    progress.value = []
    corrections.value = []
  } finally {
    loadingDetail.value = false
  }
}

function syncHomeworkFromQuery(): void {
  const fromQuery = Number(route.query.homework)
  if (fromQuery && eligibleHomeworks.value.some((homework) => homework.id === fromQuery)) {
    selectedHomeworkId.value = fromQuery
    return
  }
  if (
    selectedHomeworkId.value &&
    eligibleHomeworks.value.some((homework) => homework.id === selectedHomeworkId.value)
  ) {
    return
  }
  const active = eligibleHomeworks.value.find((homework) => homework.id === workspace.activeHomeworkId)
  selectedHomeworkId.value = active?.id ?? eligibleHomeworks.value[0]?.id ?? null
}

async function load(): Promise<void> {
  loading.value = true
  try {
    await workspace.load(true)
    syncHomeworkFromQuery()
    await loadHomeworkData()
  } finally {
    loading.value = false
  }
}

function selectHomework(event: Event): void {
  const id = Number((event.target as HTMLSelectElement).value)
  selectedHomeworkId.value = id || null
  focusedStudentId.value = null
  focusedQuestionId.value = null
  search.value = ''
  void router.replace({ path: route.path, query: id ? { homework: String(id) } : {} })
  void loadHomeworkData()
}

function selectStudent(item: CorrectionProgressItem): void {
  focusedStudentId.value = item.studentId
  focusedQuestionId.value = null
  recordsPage.value = 1
  tab.value = 'records'
}

function clearStudent(): void {
  focusedStudentId.value = null
}

function focusQuestion(question: QuestionCorrectionProgress): void {
  focusedQuestionId.value = question.questionId
  focusedStudentId.value = null
  recordsPage.value = 1
  tab.value = 'records'
}

function clearQuestion(): void {
  focusedQuestionId.value = null
}

function goReview(): void {
  if (!selectedHomework.value) return
  void router.push(`/teacher/homeworks/${selectedHomework.value.id}/review`)
}

function goGrading(): void {
  if (!selectedHomework.value) return
  void router.push(`/teacher/homeworks/${selectedHomework.value.id}/grading`)
}

function focusStudentName(): string {
  if (!focusedStudentId.value) return ''
  return progress.value.find((item) => item.studentId === focusedStudentId.value)?.studentName ?? ''
}

function focusQuestionSeq(): number | null {
  if (!focusedQuestionId.value) return null
  return questionProgress.value.find((item) => item.questionId === focusedQuestionId.value)?.seqNo ?? null
}

watch(
  () => route.query.homework,
  () => {
    syncHomeworkFromQuery()
    void loadHomeworkData()
  },
)

watch([search, completionFilter, focusedStudentId], () => {
  progressPage.value = 1
  questionPage.value = 1
})

watch([search, focusedStudentId, focusedQuestionId], () => {
  recordsPage.value = 1
})

onMounted(load)
</script>

<template>
  <div class="corrections-page">
    <PageHeader
      eyebrow="TEACHER · CORRECTION TRACKING"
      title="订正跟踪"
      subtitle="只看本班。提交订正即记一条历史记录，是否掌握由教师结合课堂判断，系统不做自动定性。"
    >
      <template #actions>
        <button class="btn btn--ghost btn--sm" :disabled="!selectedHomework" @click="goGrading">
          <AppIcon name="upload" :size="14" />
          批改记录
        </button>
        <button class="btn btn--primary btn--sm" :disabled="!selectedHomework" @click="goReview">
          <AppIcon name="sparkles" :size="14" />
          讲评审核
        </button>
      </template>
    </PageHeader>

    <div class="correction-toolbar">
      <div class="homework-picker">
        <span class="homework-picker__label">跟踪作业</span>
        <select class="select" :value="selectedHomeworkId ?? ''" @change="selectHomework">
          <option v-if="!eligibleHomeworks.length" value="">暂无已发布讲评</option>
          <option v-for="homework in eligibleHomeworks" :key="homework.id" :value="homework.id">
            {{ homework.title }} · {{ homework.className }}
          </option>
        </select>
      </div>
      <div class="segmented">
        <button type="button" :class="{ 'is-active': tab === 'progress' }" @click="tab = 'progress'">
          按学生进度
          <span class="count">{{ progress.length }}</span>
        </button>
        <button type="button" :class="{ 'is-active': tab === 'questions' }" @click="tab = 'questions'">
          按题目
          <span class="count">{{ questionProgress.length }}</span>
        </button>
        <button type="button" :class="{ 'is-active': tab === 'records' }" @click="tab = 'records'">
          订正记录
          <span class="count">{{ corrections.length }}</span>
        </button>
      </div>
      <label class="search-box">
        <AppIcon name="search" :size="15" />
        <input v-model="search" type="search" placeholder="搜索姓名、学号或题号…" />
      </label>
    </div>

    <div v-if="loading" class="card loading-card">
      <span class="skeleton" style="width: 220px; height: 10px" />
    </div>

    <template v-else-if="eligibleHomeworks.length">
      <div class="correction-summary">
        <div class="summary-main">
          <div class="summary-ring">
            <svg viewBox="0 0 96 96">
              <circle cx="48" cy="48" r="38" fill="none" stroke="#eaf3ef" stroke-width="7" />
              <circle
                cx="48"
                cy="48"
                r="38"
                fill="none"
                stroke="var(--role)"
                stroke-width="7"
                stroke-linecap="round"
                :stroke-dasharray="2 * Math.PI * 38"
                :stroke-dashoffset="2 * Math.PI * 38 * (1 - totals.rate / 100)"
                transform="rotate(-90 48 48)"
              />
            </svg>
            <div>
              <b>{{ totals.rate }}<small>%</small></b>
              <span>整体完成率</span>
            </div>
          </div>
          <div class="summary-copy">
            <span class="summary-eyebrow">CORRECTION PULSE</span>
            <h2>{{ totals.pending ? `还有 ${totals.pending} 道错题等待订正` : '本班订正已全部提交' }}</h2>
            <p>
              {{ selectedHomework?.title }}。<template v-if="selectedHomework">发布于 {{ formatDateTime(selectedHomework.publishedAt) }}。</template>
              撤回讲评不会删除已提交的订正。
            </p>
          </div>
        </div>
        <div class="summary-metrics">
          <div><span>需订正学生</span><b>{{ totals.students }}</b></div>
          <div><span>已提交</span><b>{{ totals.corrected }}<small> 题</small></b></div>
          <div><span>仍待订正</span><b class="warm">{{ totals.pending }}<small> 题</small></b></div>
          <div><span>已完成学生</span><b>{{ totals.doneStudents }}<small> 人</small></b></div>
        </div>
      </div>

      <div v-if="focusedStudentId" class="focus-chip">
        <AppIcon name="user" :size="13" />
        正在查看 {{ focusStudentName() || '所选学生' }} 的订正记录
        <button type="button" @click="clearStudent">查看全部</button>
      </div>
      <div v-if="focusedQuestionId" class="focus-chip">
        <AppIcon name="book" :size="13" />
        正在查看第 {{ focusQuestionSeq() ?? '' }} 题的订正记录
        <button type="button" @click="clearQuestion">查看全部</button>
      </div>

      <section v-if="tab === 'progress'" class="card progress-card">
        <div class="section-head">
          <div>
            <h3 class="section-title">学生订正进度</h3>
            <p class="section-desc">完成率 = 已提交订正的错题 / 本人需订正错题；不判断对错、不排名。</p>
          </div>
          <div class="segmented">
            <button type="button" :class="{ 'is-active': completionFilter === 'ALL' }" @click="completionFilter = 'ALL'">全部</button>
            <button type="button" :class="{ 'is-active': completionFilter === 'PENDING' }" @click="completionFilter = 'PENDING'">待订正</button>
            <button type="button" :class="{ 'is-active': completionFilter === 'DONE' }" @click="completionFilter = 'DONE'">已完成</button>
          </div>
        </div>

        <div v-if="loadingDetail" class="loading-card"><span class="skeleton" style="width: 180px; height: 8px" /></div>
        <div v-else-if="filteredProgress.length" class="student-progress-list">
          <div class="progress-table-head">
            <span>学生</span>
            <span>进度</span>
            <span>状态</span>
            <span>最近提交</span>
            <span />
          </div>
          <button
            v-for="student in pagedProgress"
            :key="student.studentId"
            class="student-progress-row"
            type="button"
            @click="selectStudent(student)"
          >
            <span class="student-cell">
              <span class="avatar avatar--sm">{{ student.studentName.slice(0, 1) }}</span>
              <span>
                <b>{{ student.studentName }}</b>
                <small>{{ student.studentNo }}</small>
              </span>
            </span>
            <span class="student-progress">
              <span class="student-progress__bar">
                <i :style="{ width: `${student.completionRate}%` }" />
              </span>
              <b>{{ student.correctedCount }} / {{ student.wrongCount }}</b>
            </span>
            <span>
              <span v-if="student.pendingCount === 0" class="badge" data-tone="mint">已全部提交</span>
              <span v-else class="badge" data-tone="apricot">{{ student.pendingCount }} 题待订正</span>
            </span>
            <span class="student-last">
              {{ student.lastSubmittedAt ? formatRelative(student.lastSubmittedAt) : '尚未提交' }}
            </span>
            <AppIcon name="chevron-right" :size="14" class="row-arrow" />
          </button>
          <AppPagination
            v-model:page="progressPage"
            :page-size="PROGRESS_PAGE_SIZE"
            :total="filteredProgress.length"
            unit="人"
          />
        </div>
        <AppEmpty
          v-else
          icon="check-circle"
          title="没有符合条件的订正进度"
          description="换一个筛选条件，或等待学生提交第一份订正。"
        />
      </section>

      <section v-else-if="tab === 'questions'" class="card progress-card">
        <div class="section-head">
          <div>
            <h3 class="section-title">按题目查看订正</h3>
            <p class="section-desc">按题目聚合错误人数与已订正人数；点击可查看该题的订正记录。</p>
          </div>
          <span class="record-total">{{ questionProgress.length }} 道题</span>
        </div>
        <div v-if="loadingDetail" class="loading-card"><span class="skeleton" style="width: 180px; height: 8px" /></div>
        <div v-else-if="pagedQuestions.length" class="student-progress-list">
          <div class="progress-table-head">
            <span>题目</span>
            <span>错误人数</span>
            <span>已订正</span>
            <span>完成率</span>
            <span />
          </div>
          <button
            v-for="question in pagedQuestions"
            :key="question.questionId"
            class="student-progress-row question-progress-row"
            type="button"
            @click="focusQuestion(question)"
          >
            <span class="student-cell question-cell">
              <span class="question-index">Q{{ question.seqNo }}</span>
              <span>
                <b>第 {{ question.seqNo }} 题</b>
                <small>{{ question.stem || '题干已删除' }}</small>
              </span>
            </span>
            <span><b>{{ question.wrongCount }}</b> 人</span>
            <span><b>{{ question.correctedCount }} / {{ question.wrongCount }}</b></span>
            <span>
              <span class="badge" :data-tone="question.pendingCount === 0 ? 'mint' : 'apricot'">
                {{ question.completionRate }}%
              </span>
            </span>
            <AppIcon name="chevron-right" :size="14" class="row-arrow" />
          </button>
          <AppPagination
            v-model:page="questionPage"
            :page-size="QUESTION_PAGE_SIZE"
            :total="questionProgress.length"
            unit="题"
          />
        </div>
        <AppEmpty
          v-else
          icon="book"
          title="还没有可按题目查看的订正"
          description="讲评发布后，学生在本人错题下提交订正，这里会按题目汇总。"
        />
      </section>

      <section v-else class="card records-card">
        <div class="section-head">
          <div>
            <h3 class="section-title">订正记录</h3>
            <p class="section-desc">同一道题可多次提交，版本号递增，历史不会覆盖。</p>
          </div>
          <span class="record-total">{{ filteredCorrections.length }} 条</span>
        </div>
        <div v-if="loadingDetail" class="loading-card"><span class="skeleton" style="width: 180px; height: 8px" /></div>
        <div v-else-if="filteredCorrections.length" class="record-list">
          <article v-for="record in pagedCorrections" :key="record.id" class="record-item">
            <div class="record-item__head">
              <span class="avatar avatar--sm">{{ record.studentName.slice(0, 1) }}</span>
              <div class="record-item__copy">
                <b>{{ record.studentName }}</b>
                <span>{{ record.studentNo }} · 第 {{ record.seqNo }} 题 · 版本 V{{ record.versionNo }}</span>
              </div>
              <span class="record-time">{{ formatDateTime(record.submittedAt) }}</span>
            </div>
            <div class="record-body">
              <div class="record-answer">
                <span>重选答案</span>
                <b>{{ record.selectedAnswer || '未重选' }}</b>
              </div>
              <div class="record-content">
                <span>订正说明</span>
                <p>{{ record.content || '未填写说明' }}</p>
              </div>
            </div>
          </article>
          <AppPagination
            v-model:page="recordsPage"
            :page-size="RECORDS_PAGE_SIZE"
            :total="filteredCorrections.length"
            class="record-pagination"
          />
        </div>
        <AppEmpty
          v-else
          icon="clipboard-check"
          title="还没有订正记录"
          description="学生针对已发布讲评提交订正后，会在这里按时间倒序出现。"
        />
      </section>

      <div class="correction-footnote">
        <AppIcon name="info" :size="14" />
        口径提示：提交订正即计入进度，是否掌握由教师线下判断；学生之间互不可见，教师只能查看本班。
        <template v-if="truncated">当前数据量超过单页加载上限，仅展示前 2000 条，请缩小筛选范围。</template>
      </div>
    </template>

    <section v-else-if="!loading" class="card">
      <AppEmpty
        icon="clipboard-check"
        title="还没有可跟踪的已发布讲评"
        description="发布一份讲评后，学生针对本人错题提交的订正会自动汇总到本班视线。"
      >
        <RouterLink to="/teacher/homeworks" class="btn btn--primary btn--sm">前往作业列表</RouterLink>
      </AppEmpty>
    </section>

    <div v-if="error" class="error-banner" style="margin-top: 12px">
      <AppIcon name="alert-circle" :size="15" />
      {{ error }}
    </div>
  </div>
</template>

<style scoped>
.corrections-page {
  max-width: 1220px;
}

.correction-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 15px;
  flex-wrap: wrap;
}

.homework-picker {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 280px;
}

.homework-picker__label {
  flex: none;
  color: var(--ink-3);
  font-size: 9px;
}

.homework-picker .select {
  height: 34px;
  font-size: 10.5px;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 220px;
  height: 34px;
  margin-left: auto;
  padding: 0 11px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--ink-3);
  background: rgba(255, 255, 255, 0.8);
}

.search-box input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  font-size: 10.5px;
}

.correction-summary {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(300px, 0.7fr);
  gap: 20px;
  align-items: center;
  margin-bottom: 15px;
  padding: 22px 24px;
  overflow: hidden;
  border: 1px solid #dcece6;
  border-radius: 22px;
  background:
    radial-gradient(circle at 100% 0, rgba(255, 255, 255, 0.9), transparent 38%),
    linear-gradient(120deg, #fbfefd, #eef9f5 65%, #f3f5fe);
}

.summary-main {
  display: flex;
  align-items: center;
  gap: 20px;
}

.summary-ring {
  position: relative;
  width: 96px;
  height: 96px;
  flex: none;
}

.summary-ring svg {
  width: 100%;
  height: 100%;
}

.summary-ring > div {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.summary-ring b {
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 25px;
  font-weight: 600;
  line-height: 1;
}

.summary-ring b small {
  font-family: var(--font-body);
  font-size: 9px;
}

.summary-ring span {
  margin-top: 3px;
  color: var(--ink-3);
  font-size: 7.5px;
}

.summary-copy .summary-eyebrow {
  color: var(--mint-deep);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.12em;
}

.summary-copy h2 {
  margin-top: 5px;
  font-family: var(--font-serif);
  font-size: 19px;
  font-weight: 600;
  line-height: 1.4;
}

.summary-copy p {
  max-width: 60ch;
  margin-top: 4px;
  color: var(--ink-3);
  font-size: 9.5px;
  line-height: 1.7;
}

.summary-metrics {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1px;
  overflow: hidden;
  border: 1px solid rgba(216, 234, 227, 0.8);
  border-radius: 12px;
  background: rgba(216, 234, 227, 0.8);
}

.summary-metrics > div {
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.65);
}

.summary-metrics span {
  display: block;
  color: var(--ink-3);
  font-size: 8px;
}

.summary-metrics b {
  display: block;
  margin-top: 2px;
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 20px;
  font-weight: 600;
}

.summary-metrics b small {
  color: var(--ink-3);
  font-family: var(--font-body);
  font-size: 8px;
  font-weight: 400;
}

.summary-metrics b.warm {
  color: var(--coral-deep);
}

.focus-chip {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-bottom: 10px;
  padding: 6px 10px;
  border-radius: 7px;
  color: var(--role-deep);
  background: var(--role-soft);
  font-size: 9px;
}

.focus-chip button {
  margin-left: auto;
  color: var(--role-deep);
  font-size: 8.5px;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.progress-card,
.records-card {
  padding: 22px;
}

.progress-table-head {
  display: grid;
  grid-template-columns: minmax(170px, 1.1fr) minmax(170px, 1fr) 120px 100px 20px;
  gap: 12px;
  padding: 0 10px 7px;
  color: var(--ink-4);
  font-size: 8px;
}

.student-progress-list {
  display: flex;
  flex-direction: column;
}

.student-progress-row {
  display: grid;
  grid-template-columns: minmax(170px, 1.1fr) minmax(170px, 1fr) 120px 100px 20px;
  align-items: center;
  gap: 12px;
  padding: 9px 10px;
  border-top: 1px solid #eff5f2;
  text-align: left;
  transition: background 0.18s;
}

.student-progress-row:hover {
  background: #fbfdfc;
}

.student-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.student-cell > span:last-child {
  min-width: 0;
}

.student-cell b {
  display: block;
  color: var(--ink);
  font-size: 10px;
  font-weight: 600;
}

.student-cell small {
  display: block;
  color: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 8px;
}

.question-index {
  display: grid;
  width: 26px;
  height: 26px;
  flex: none;
  place-items: center;
  border-radius: 8px;
  color: var(--mint-deep);
  background: var(--surface-mint);
  font-family: var(--font-mono);
  font-size: 8px;
}

.question-cell small {
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.student-progress {
  display: flex;
  align-items: center;
  gap: 8px;
}

.student-progress__bar {
  height: 6px;
  flex: 1;
  overflow: hidden;
  border-radius: 99px;
  background: #f0f5f2;
}

.student-progress__bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #a7e1d7, var(--mint));
}

.student-progress > b {
  width: 32px;
  color: var(--ink-3);
  font-family: var(--font-numbers);
  font-size: 9px;
  font-weight: 500;
}

.student-progress-row :deep(.badge) {
  min-height: 19px;
  padding: 0 6px;
  font-size: 8px;
}

.student-last {
  color: var(--ink-4);
  font-size: 8.5px;
}

.row-arrow {
  color: var(--ink-4);
}

.record-total {
  color: var(--ink-4);
  font-family: var(--font-numbers);
  font-size: 12px;
}

.record-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 9px;
}

.record-pagination.app-pagination {
  display: grid;
  grid-column: 1 / -1;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 10px;
}

.record-pagination.app-pagination :deep(> span) {
  grid-column: 2;
  text-align: center;
}

.record-pagination.app-pagination :deep(> div) {
  grid-column: 3;
  justify-self: end;
}

.record-item {
  padding: 13px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fdfefd;
}

.record-item__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.record-item__copy {
  min-width: 0;
  flex: 1;
}

.record-item__copy b {
  display: block;
  color: var(--ink);
  font-size: 10px;
}

.record-item__copy span {
  display: block;
  margin-top: 1px;
  color: var(--ink-4);
  font-size: 8px;
}

.record-time {
  flex: none;
  color: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 7.5px;
}

.record-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 9px;
  padding-top: 8px;
  border-top: 1px dashed var(--line);
}

.record-answer {
  display: flex;
  align-items: center;
  gap: 7px;
}

.record-answer span,
.record-content > span {
  color: var(--ink-4);
  font-size: 8px;
}

.record-answer b {
  padding: 2px 7px;
  border-radius: 5px;
  color: var(--mint-deep);
  background: var(--mint-soft);
  font-family: var(--font-serif);
  font-size: 9.5px;
  font-weight: 600;
}

.record-content p {
  margin-top: 2px;
  color: var(--ink-2);
  font-size: 9px;
  line-height: 1.7;
}

.correction-footnote {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 11px;
  padding: 0 4px;
  color: var(--ink-4);
  font-size: 8.5px;
  line-height: 1.6;
}

.correction-footnote .icon {
  flex: none;
  margin-top: 1px;
}

@media (max-width: 980px) {
  .correction-summary {
    grid-template-columns: 1fr;
  }

  .summary-metrics {
    grid-template-columns: repeat(4, 1fr);
  }

  .record-list {
    grid-template-columns: 1fr;
  }

  .progress-table-head {
    display: none;
  }

  .student-progress-row {
    grid-template-columns: minmax(130px, 1fr) minmax(120px, 1fr) 90px 20px;
  }

  .student-last {
    display: none;
  }
}

@media (max-width: 680px) {
  .homework-picker {
    min-width: 0;
    width: 100%;
  }

  .homework-picker .select {
    flex: 1;
  }

  .search-box {
    width: 100%;
    margin-left: 0;
  }

  .correction-toolbar .segmented {
    order: 3;
  }

  .summary-main {
    flex-direction: column;
    align-items: flex-start;
  }

  .summary-metrics {
    grid-template-columns: 1fr 1fr;
  }

  .progress-card,
  .records-card {
    padding: 16px;
  }

  .student-progress-row {
    grid-template-columns: minmax(100px, 1fr) 70px 20px;
    gap: 7px;
  }

  .student-progress {
    grid-column: 1 / -1;
    grid-row: 2;
  }

  .student-progress-row > span:nth-child(3) {
    text-align: right;
  }
}
</style>
