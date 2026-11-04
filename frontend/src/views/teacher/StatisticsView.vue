<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { teacherApi } from '@/api/teacher'
import { errorMessage } from '@/api/errors'
import type { HomeworkDetail, HomeworkStatistics, QuestionStatistic } from '@/types/models'
import { useTeacherWorkspace } from '@/stores/teacherWorkspace'
import { useUiStore } from '@/stores/ui'
import { formatDateTime, percentOf } from '@/utils/format'
import { QUESTION_TYPE } from '@/types/labels'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import AppIcon from '@/components/AppIcon.vue'
import AppEmpty from '@/components/AppEmpty.vue'

const route = useRoute()
const router = useRouter()
const workspace = useTeacherWorkspace()
const ui = useUiStore()

const homework = ref<HomeworkDetail | null>(null)
const statistics = ref<HomeworkStatistics | null>(null)
const loading = ref(true)
const pageError = ref('')
const tab = ref<'questions' | 'knowledge'>('questions')
const expandedQuestionId = ref<number | null>(null)
const sortByRate = ref(true)

const homeworkId = computed(() => Number(route.params.id))
const masterRate = computed(() => {
  if (!statistics.value) return 0
  const { overview } = statistics.value
  const total = overview.studentCount * overview.questionCount
  return total ? Math.round(((total - overview.totalWrong) / total) * 100) : 0
})
const sortedQuestions = computed(() => {
  const list = [...(statistics.value?.questions ?? [])]
  return sortByRate.value ? list.sort((a, b) => b.errorRate - a.errorRate) : list.sort((a, b) => a.seqNo - b.seqNo)
})
const knowledgePoints = computed(() =>
  [...(statistics.value?.knowledgePoints ?? [])].sort((a, b) => b.errorRate - a.errorRate),
)
const hotQuestions = computed(() =>
  [...(statistics.value?.questions ?? [])]
    .filter((question) => question.errorRate >= 40)
    .sort((a, b) => b.errorRate - a.errorRate),
)
async function load(): Promise<void> {
  loading.value = true
  pageError.value = ''
  try {
    const detail = await teacherApi.getHomework(homeworkId.value)
    homework.value = detail
    workspace.selectHomework(detail.id)
    if (!detail.currentGradingBatchId) {
      statistics.value = null
      return
    }
    statistics.value = await teacherApi.getStatistics(detail.id)
  } catch (cause) {
    pageError.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

function goGrading(): void {
  if (!homework.value) return
  void router.push(`/teacher/homeworks/${homework.value.id}/grading`)
}

function goReview(): void {
  if (!homework.value) return
  void router.push(`/teacher/homeworks/${homework.value.id}/review`)
}

function distributionEntries(question: QuestionStatistic): Array<{ key: string; label: string; count: number; rate: number }> {
  const entries = Object.entries(question.optionDistribution ?? {})
  const total = question.totalCount || 1
  return entries
    .map(([key, count]) => ({
      key,
      label: key === 'TRUE' ? '正确' : key === 'FALSE' ? '错误' : key,
      count,
      rate: percentOf(count, total),
    }))
    .sort((a, b) => a.key.localeCompare(b.key))
}

function toggleQuestion(questionId: number): void {
  expandedQuestionId.value = expandedQuestionId.value === questionId ? null : questionId
}

function exportCsv(): void {
  if (!homework.value || !statistics.value) return
  const rows: string[][] = [
    ['作业', homework.value.title],
    ['班级', homework.value.className],
    ['批次', `V${statistics.value.batchVersionNo}`],
    ['计算时间', formatDateTime(statistics.value.calculatedAt)],
    [],
    ['维度', '题目/知识点', '应交/涉及人数', '正确数', '错误数', '未作答', '错误率(%)', '平均分'],
    ...statistics.value.questions.map((question) => [
      '题目',
      `第${question.seqNo}题（${QUESTION_TYPE[question.questionType].label}）`,
      String(question.totalCount),
      String(question.correctCount),
      String(question.wrongCount),
      String(question.unansweredCount),
      question.errorRate.toFixed(2),
      question.avgScore === null ? '' : String(question.avgScore),
    ]),
    ...statistics.value.knowledgePoints.map((kp) => [
      '知识点',
      kp.name,
      String(kp.totalCount),
      String(kp.correctCount),
      String(kp.wrongCount),
      String(kp.unansweredCount),
      kp.errorRate.toFixed(2),
      kp.avgScore === null ? '' : String(kp.avgScore),
    ]),
  ]
  const content = rows.map((row) => row.map((cell) => (/[",\n]/.test(cell) ? `"${cell.replace(/"/g, '""')}"` : cell)).join(',')).join('\r\n')
  const blob = new Blob([`\uFEFF${content}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `错误统计_${homework.value.title}.csv`
  anchor.click()
  URL.revokeObjectURL(url)
  ui.toast('success', '统计已导出', '导出内容与页面展示一致，仅包含本班聚合结果。')
}

onMounted(load)
</script>

<template>
  <div class="statistics-page">
    <div v-if="loading" class="card loading-card">
      <span class="skeleton" style="width: 220px; height: 10px" />
    </div>

    <div v-else-if="pageError" class="card">
      <AppEmpty icon="alert-circle" title="无法加载统计" :description="pageError">
        <button class="btn btn--ghost btn--sm" @click="router.push('/teacher/homeworks')">返回作业列表</button>
      </AppEmpty>
    </div>

    <template v-else-if="homework">
      <PageHeader
        eyebrow="TEACHER · ANALYTICS"
        title="错误统计"
        :subtitle="`《${homework.title}》 · ${homework.className}。统计由当前批改批次自动重算，只读，不可手工修改。`"
      >
        <template #actions>
          <button class="btn btn--ghost btn--sm" :disabled="!statistics" @click="exportCsv">
            <AppIcon name="download" :size="14" />
            导出 CSV
          </button>
          <button class="btn btn--ghost btn--sm" @click="goGrading">
            <AppIcon name="upload" :size="14" />
            批改记录
          </button>
          <button v-if="statistics" class="btn btn--primary btn--sm" @click="goReview">
            <AppIcon name="sparkles" :size="14" />
            去审核讲评
          </button>
        </template>
      </PageHeader>

      <template v-if="statistics">
        <div class="stats-overview">
          <StatCard
            label="班级掌握率"
            :value="masterRate"
            suffix="%"
            :note="`${statistics.overview.studentCount} 人 × ${statistics.overview.questionCount} 题`"
            icon="target"
            tone="mint"
            trend="按全量作答计算"
          />
          <StatCard
            label="错误记录"
            :value="statistics.overview.totalWrong"
            suffix="条"
            :note="`未作答 ${statistics.overview.totalUnanswered} 条（单独计数）`"
            icon="alert-circle"
            tone="coral"
          />
          <StatCard
            label="题均得分"
            :value="statistics.overview.averageScore ?? '—'"
            suffix="分"
            note="所有题目记录的平均得分"
            icon="chart"
            tone="peri"
          />
          <StatCard
            label="重点错题"
            :value="hotQuestions.length"
            suffix="题"
            note="错误率 ≥ 40%（阈值暂定）"
            icon="target"
            tone="apricot"
          />
        </div>

        <div class="stats-meta-line">
          <span><AppIcon name="layers" :size="13" />当前批次 V{{ statistics.batchVersionNo }}</span>
          <span><AppIcon name="clock" :size="13" />计算于 {{ formatDateTime(statistics.calculatedAt) }}</span>
          <span class="stats-readonly"><AppIcon name="lock" :size="12" />统计只读</span>
        </div>

        <div class="stats-layout">
          <div class="stats-main">
            <div class="stats-tabs-row">
              <div class="segmented">
                <button type="button" :class="{ 'is-active': tab === 'questions' }" @click="tab = 'questions'">
                  按题目
                  <span class="count">{{ statistics.questions.length }}</span>
                </button>
                <button type="button" :class="{ 'is-active': tab === 'knowledge' }" @click="tab = 'knowledge'">
                  按知识点
                  <span class="count">{{ statistics.knowledgePoints.length }}</span>
                </button>
              </div>
              <button
                v-if="tab === 'questions'"
                class="btn btn--quiet btn--sm"
                type="button"
                @click="sortByRate = !sortByRate"
              >
                <AppIcon name="sliders" :size="14" />
                {{ sortByRate ? '按错误率排序' : '按题号排序' }}
              </button>
            </div>

            <section v-if="tab === 'questions'" class="card question-stats-card">
              <div class="section-head">
                <div>
                  <h3 class="section-title">题目错误分布</h3>
                  <p class="section-desc">错误率 = 错误人数 / 应交人数；未作答单独统计。点击题目查看选项分布。</p>
                </div>
                <span class="threshold-note">重点线 40%</span>
              </div>

              <div class="question-stat-list">
                <article
                  v-for="question in sortedQuestions"
                  :key="question.questionId"
                  class="question-stat"
                  :class="{ 'is-expanded': expandedQuestionId === question.questionId }"
                >
                  <button class="question-stat__row" type="button" @click="toggleQuestion(question.questionId)">
                    <span class="q-index">{{ String(question.seqNo).padStart(2, '0') }}</span>
                    <div class="q-main">
                      <div class="q-title">
                        <b>第 {{ question.seqNo }} 题</b>
                        <span class="tag">{{ QUESTION_TYPE[question.questionType].label }}</span>
                        <span class="q-stem">{{ question.stemPreview }}</span>
                      </div>
                      <div class="q-bar-row">
                        <div class="q-bar">
                          <i
                            :class="{ 'is-hot': question.errorRate >= 40 }"
                            :style="{ width: `${Math.max(question.errorRate, 1)}%` }"
                          />
                        </div>
                        <span class="q-rate" :class="{ 'is-hot': question.errorRate >= 40 }">
                          {{ question.errorRate.toFixed(1) }}%
                        </span>
                      </div>
                    </div>
                    <div class="q-numbers">
                      <div>
                        <b>{{ question.correctCount }}</b>
                        <span>正确</span>
                      </div>
                      <div>
                        <b class="wrong">{{ question.wrongCount }}</b>
                        <span>错误</span>
                      </div>
                      <div>
                        <b>{{ question.unansweredCount }}</b>
                        <span>未答</span>
                      </div>
                      <div>
                        <b>{{ question.avgScore ?? '—' }}</b>
                        <span>平均分</span>
                      </div>
                    </div>
                    <AppIcon
                      :name="expandedQuestionId === question.questionId ? 'chevron-up' : 'chevron-down'"
                      :size="15"
                      class="q-chevron"
                    />
                  </button>

                  <Transition name="fade">
                    <div v-if="expandedQuestionId === question.questionId" class="q-detail">
                      <div class="q-detail__head">
                        <span>选项分布</span>
                        <span>共 {{ question.totalCount }} 条记录</span>
                      </div>
                      <div class="distribution-list">
                        <div
                          v-for="entry in distributionEntries(question)"
                          :key="entry.key"
                          class="distribution-row"
                        >
                          <span class="distribution-key">{{ entry.label }}</span>
                          <div class="distribution-track">
                            <i :style="{ width: `${Math.max(entry.rate, 1)}%` }" />
                          </div>
                          <b>{{ entry.count }} 人</b>
                          <em>{{ entry.rate.toFixed(0) }}%</em>
                        </div>
                      </div>
                      <p class="q-detail__note">
                        选项分布仅用于定位典型错误，不评价学生能力或态度；讲评措辞请使用「可能 / 常见」。
                      </p>
                    </div>
                  </Transition>
                </article>
              </div>
            </section>

            <section v-else class="card kp-stats-card">
              <div class="section-head">
                <div>
                  <h3 class="section-title">知识点薄弱度</h3>
                  <p class="section-desc">按涉及学生聚合；同一学生任一道关联题错误，即计入该知识点错误。</p>
                </div>
                <RouterLink to="/teacher/homeworks" class="btn btn--quiet btn--sm">返回作业</RouterLink>
              </div>
              <div class="kp-stat-list">
                <div v-for="(kp, index) in knowledgePoints" :key="kp.knowledgePointId" class="kp-stat">
                  <span class="kp-rank">{{ String(index + 1).padStart(2, '0') }}</span>
                  <div class="kp-main">
                    <div class="kp-title">
                      <b>{{ kp.name }}</b>
                      <span>{{ kp.wrongCount }} / {{ kp.totalCount }} 人受影响</span>
                    </div>
                    <div class="kp-bar">
                      <i
                        :class="{ 'is-hot': kp.errorRate >= 40 }"
                        :style="{ width: `${Math.max(kp.errorRate, 1)}%` }"
                      />
                    </div>
                  </div>
                  <div class="kp-rate" :class="{ 'is-hot': kp.errorRate >= 40 }">
                    <b>{{ kp.errorRate.toFixed(1) }}%</b>
                    <span>错误率</span>
                  </div>
                  <div class="kp-score">
                    <b>{{ kp.avgScore ?? '—' }}</b>
                    <span>题均分</span>
                  </div>
                </div>
              </div>
            </section>
          </div>

          <aside class="stats-side">
            <section class="card focus-insight">
              <span class="focus-insight__label">本轮讲评建议</span>
              <h3 v-if="hotQuestions.length">先讲 {{ hotQuestions.length }} 道重点题</h3>
              <h3 v-else>本次整体掌握较好</h3>
              <p v-if="hotQuestions.length">
                按错误率排序，优先处理 {{ hotQuestions.map((question) => `第 ${question.seqNo} 题`).join('、') }}。
                生成讲评时，AI 会为每道题附上原题、答案、解析与统计快照。
              </p>
              <p v-else>可只挑选典型错误进行短讲评，或结合课堂实际情况手动决定。</p>
              <div v-if="hotQuestions.length" class="focus-q-list">
                <span v-for="question in hotQuestions.slice(0, 4)" :key="question.questionId">
                  第{{ question.seqNo }}题 · {{ question.errorRate.toFixed(0) }}%
                </span>
              </div>
              <button class="btn btn--primary btn--sm btn--block" @click="goReview">
                <AppIcon name="sparkles" :size="14" />
                进入讲评审核
              </button>
            </section>

            <section class="card method-card">
              <div class="method-icon"><AppIcon name="info" :size="16" /></div>
              <h4>统计口径</h4>
              <dl>
                <div>
                  <dt>错误率分母</dt>
                  <dd>应交人数（{{ statistics.overview.studentCount }}）</dd>
                </div>
                <div><dt>未作答</dt><dd>单独计数，不并入错误</dd></div>
                <div><dt>重点线</dt><dd>≥ 40%（暂定 Q21）</dd></div>
                <div><dt>小数位</dt><dd>页面 1 位，导出 2 位</dd></div>
              </dl>
              <p>所有口径与《开发前需求确认问题清单》对应，确认后统一调整。</p>
            </section>

            <section class="card boundary-card">
              <AppIcon name="shield" :size="16" />
              <div>
                <h4>数据边界</h4>
                <p>仅本班、当前批次。跨班数据不可见；学生端看不到任何班级统计与排名。</p>
              </div>
            </section>
          </aside>
        </div>
      </template>

      <section v-else class="card">
        <AppEmpty
          icon="chart"
          title="还没有可统计的批改批次"
          description="作业发布后导入批改数据，系统会自动按题目与知识点计算错误分布。"
        >
          <button class="btn btn--primary btn--sm" @click="goGrading">去导入批改</button>
        </AppEmpty>
      </section>
    </template>
  </div>
</template>

<style scoped>
.statistics-page {
  max-width: 1260px;
}

.stats-overview {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 12px;
}

.stats-meta-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin-bottom: 16px;
  padding: 0 3px;
  color: var(--ink-3);
  font-size: 9px;
}

.stats-meta-line span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.stats-readonly {
  color: var(--ink-4);
}

.stats-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 288px;
  gap: 16px;
  align-items: start;
}

.stats-main {
  min-width: 0;
}

.stats-tabs-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.question-stats-card,
.kp-stats-card {
  padding: 21px;
}

.threshold-note {
  flex: none;
  padding: 3px 8px;
  border: 1px dashed #efd7c3;
  border-radius: 999px;
  color: var(--coral-deep);
  font-size: 8.5px;
}

.question-stat-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.question-stat {
  border: 1px solid transparent;
  border-radius: 11px;
  transition: background 0.18s, border-color 0.18s;
}

.question-stat:hover {
  background: #fcfefd;
  border-color: var(--line);
}

.question-stat.is-expanded {
  border-color: var(--line);
  background: #fdfefd;
}

.question-stat__row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 8px;
  text-align: left;
}

.q-index {
  width: 24px;
  flex: none;
  color: var(--ink-3);
  font-family: var(--font-numbers);
  font-size: 12px;
}

.q-main {
  min-width: 0;
  flex: 1;
}

.q-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
}

.q-title > b {
  color: var(--ink);
  font-size: 10.5px;
}

.q-title .tag {
  padding: 1px 5px;
  font-size: 7.5px;
}

.q-stem {
  overflow: hidden;
  max-width: 300px;
  color: var(--ink-3);
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.q-bar-row {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 5px;
}

.q-bar {
  height: 5px;
  flex: 1;
  overflow: hidden;
  border-radius: 99px;
  background: #f0f5f2;
}

.q-bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #9fded4, var(--mint));
}

.q-bar i.is-hot {
  background: linear-gradient(90deg, #efb4a1, var(--coral));
}

.q-rate {
  width: 38px;
  flex: none;
  color: var(--mint-deep);
  font-family: var(--font-numbers);
  font-size: 10px;
  font-weight: 600;
}

.q-rate.is-hot {
  color: var(--coral-deep);
}

.q-numbers {
  display: flex;
  gap: 10px;
  flex: none;
}

.q-numbers div {
  width: 35px;
  text-align: center;
}

.q-numbers b {
  display: block;
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 11px;
  font-weight: 600;
}

.q-numbers b.wrong {
  color: var(--coral-deep);
}

.q-numbers span {
  display: block;
  color: var(--ink-4);
  font-size: 7.5px;
}

.q-chevron {
  flex: none;
  color: var(--ink-4);
}

.q-detail {
  padding: 3px 10px 13px 42px;
}

.q-detail__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0 5px;
  border-top: 1px dashed var(--line);
  color: var(--ink-3);
  font-size: 8.5px;
}

.distribution-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.distribution-row {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 9px;
}

.distribution-key {
  width: 38px;
  flex: none;
  overflow: hidden;
  color: var(--ink-2);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.distribution-track {
  height: 5px;
  flex: 1;
  overflow: hidden;
  border-radius: 99px;
  background: #f1f5f3;
}

.distribution-track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #a9c6bb;
}

.distribution-row b {
  width: 35px;
  color: var(--ink-2);
  font-family: var(--font-numbers);
  font-size: 9px;
  font-weight: 500;
  text-align: right;
}

.distribution-row em {
  width: 28px;
  color: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 8px;
  font-style: normal;
  text-align: right;
}

.q-detail__note {
  margin-top: 7px;
  color: var(--ink-4);
  font-size: 8px;
  line-height: 1.6;
}

/* 知识点 */
.kp-stat-list {
  display: flex;
  flex-direction: column;
}

.kp-stat {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 13px 5px;
  border-bottom: 1px dashed var(--line);
}

.kp-stat:last-child {
  border-bottom: 0;
}

.kp-rank {
  width: 22px;
  flex: none;
  color: var(--ink-4);
  font-family: var(--font-numbers);
  font-size: 11px;
}

.kp-main {
  min-width: 0;
  flex: 1;
}

.kp-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.kp-title b {
  overflow: hidden;
  color: var(--ink-2);
  font-family: var(--font-serif);
  font-size: 11.5px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kp-title span {
  flex: none;
  color: var(--ink-4);
  font-size: 8px;
}

.kp-bar {
  height: 6px;
  margin-top: 6px;
  overflow: hidden;
  border-radius: 99px;
  background: #f0f5f2;
}

.kp-bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #b0e4db, var(--mint));
}

.kp-bar i.is-hot {
  background: linear-gradient(90deg, #f0c0ad, var(--coral));
}

.kp-rate,
.kp-score {
  width: 56px;
  flex: none;
  text-align: right;
}

.kp-rate b {
  display: block;
  color: var(--mint-deep);
  font-family: var(--font-numbers);
  font-size: 13px;
  font-weight: 600;
}

.kp-rate.is-hot b {
  color: var(--coral-deep);
}

.kp-rate span,
.kp-score span {
  display: block;
  color: var(--ink-4);
  font-size: 7.5px;
}

.kp-score b {
  display: block;
  color: var(--ink-2);
  font-family: var(--font-numbers);
  font-size: 12px;
  font-weight: 500;
}

/* 侧栏 */
.stats-side {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.focus-insight {
  padding: 19px;
  border-color: #d6ece4;
  background:
    radial-gradient(circle at 100% 0, rgba(255, 255, 255, 0.9), transparent 40%),
    linear-gradient(145deg, #fbfefd, #edf9f5);
}

.focus-insight__label {
  color: var(--mint-deep);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.13em;
}

.focus-insight h3 {
  margin-top: 7px;
  font-family: var(--font-display);
  font-size: 21px;
  font-weight: 400;
  line-height: 1.4;
}

.focus-insight > p {
  margin-top: 6px;
  color: var(--ink-2);
  font-size: 9.5px;
  line-height: 1.75;
}

.focus-q-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin: 10px 0 13px;
}

.focus-q-list span {
  padding: 3px 7px;
  border-radius: 5px;
  color: var(--coral-deep);
  background: rgba(255, 255, 255, 0.75);
  font-size: 8px;
}

.method-card {
  padding: 17px;
}

.method-icon {
  display: grid;
  place-items: center;
  width: 29px;
  height: 29px;
  margin-bottom: 7px;
  border-radius: 8px;
  color: var(--ink-3);
  background: var(--surface-soft);
}

.method-card h4 {
  color: var(--ink-2);
  font-size: 10.5px;
}

.method-card dl {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin: 9px 0;
}

.method-card dl div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.method-card dt {
  color: var(--ink-4);
  font-size: 8.5px;
}

.method-card dd {
  margin: 0;
  color: var(--ink-2);
  font-size: 8.5px;
  text-align: right;
}

.method-card > p {
  padding-top: 8px;
  border-top: 1px dashed var(--line);
  color: var(--ink-4);
  font-size: 8px;
  line-height: 1.6;
}

.boundary-card {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 14px;
  background: var(--surface-soft);
  box-shadow: none;
}

.boundary-card .icon {
  flex: none;
  margin-top: 2px;
  color: var(--ink-4);
}

.boundary-card h4 {
  color: var(--ink-2);
  font-size: 9.5px;
}

.boundary-card p {
  margin-top: 2px;
  color: var(--ink-4);
  font-size: 8.5px;
  line-height: 1.65;
}

@media (max-width: 1080px) {
  .stats-overview {
    grid-template-columns: 1fr 1fr;
  }

  .stats-layout {
    grid-template-columns: 1fr;
  }

  .stats-side {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }

  .focus-insight {
    grid-row: span 2;
  }
}

@media (max-width: 720px) {
  .stats-overview {
    gap: 8px;
  }

  .question-stats-card,
  .kp-stats-card {
    padding: 16px;
  }

  .q-numbers {
    display: none;
  }

  .q-stem {
    display: none;
  }

  .q-detail {
    padding-left: 16px;
  }

  .stats-side {
    display: flex;
  }

  .kp-stat {
    gap: 7px;
  }

  .kp-score {
    display: none;
  }
}
</style>
