<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { studentApi } from '@/api/student'
import { errorMessage } from '@/api/errors'
import type { StudentHomeworkSummary, StudentScoreSummary } from '@/types/models'
import { formatDateTime } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import AppIcon from '@/components/AppIcon.vue'
import AppEmpty from '@/components/AppEmpty.vue'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const search = ref('')
const homeworks = ref<StudentHomeworkSummary[]>([])
const summary = ref<StudentScoreSummary>({
  totalScore: 0,
  maxScore: 0,
  scoreRate: 0,
  submissionCount: 0,
})

const filteredHomeworks = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return homeworks.value.filter(
    (homework) =>
      !keyword ||
      homework.title.toLowerCase().includes(keyword) ||
      (homework.description ?? '').toLowerCase().includes(keyword),
  )
})
const completedCount = computed(() => homeworks.value.filter((item) => item.submitted).length)
const pendingCount = computed(() => homeworks.value.length - completedCount.value)
const totalQuestions = computed(() =>
  homeworks.value.reduce((sum, item) => sum + item.questionCount, 0),
)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [homeworkList, scoreSummary] = await Promise.all([
      studentApi.listHomeworks(),
      studentApi.getScoreSummary(),
    ])
    homeworks.value = homeworkList
    summary.value = scoreSummary
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

function openHomework(homework: StudentHomeworkSummary): void {
  void router.push(`/student/homeworks/${homework.id}`)
}

function scoreText(homework: StudentHomeworkSummary): string {
  if (!homework.submitted) return '尚未提交'
  return `${homework.score ?? 0} / ${homework.maxScore ?? 0} 分`
}

onMounted(load)
</script>

<template>
  <div class="homework-page">
    <PageHeader
      eyebrow="STUDENT · ASSIGNMENTS"
      title="作业与成绩"
      subtitle="在这里完成老师已发布的作业。提交后立即自动判分，并生成只属于你的错题记录。"
    >
      <template #actions>
        <RouterLink to="/student/wrong-book" class="btn btn--ghost btn--sm">
          <AppIcon name="target" :size="14" />
          我的错题本
        </RouterLink>
        <button class="btn btn--ghost btn--sm" type="button" :disabled="loading" @click="load">
          <AppIcon name="refresh" :size="14" />
          刷新
        </button>
      </template>
    </PageHeader>

    <div v-if="loading" class="card loading-card">
      <span class="skeleton" style="width: 230px; height: 10px" />
    </div>

    <div v-else-if="error" class="card">
      <AppEmpty icon="alert-circle" title="作业加载失败" :description="error">
        <button class="btn btn--ghost btn--sm" @click="load">重新加载</button>
      </AppEmpty>
    </div>

    <template v-else>
      <section class="score-hero">
        <div class="score-hero__copy">
          <span class="score-hero__eyebrow">MY LEARNING RECORD</span>
          <h2>{{ completedCount ? '每一次提交，都让进步有迹可循' : '准备好开始第一份作业了吗？' }}</h2>
          <p>总分只统计每份作业最近一次提交，不展示排名，也不会公开给其他同学。</p>
          <div class="score-hero__actions">
            <button
              v-if="homeworks.length"
              class="btn btn--primary btn--sm"
              type="button"
              @click="openHomework(homeworks[0])"
            >
              <AppIcon name="arrow-right" :size="14" />
              {{ homeworks[0].submitted ? '查看最近成绩' : '开始最近作业' }}
            </button>
            <RouterLink to="/student/handouts" class="btn btn--ghost btn--sm">
              查看错题讲评
            </RouterLink>
          </div>
        </div>
        <div class="score-hero__number" aria-label="当前总得分">
          <span>当前总得分</span>
          <strong>{{ summary.totalScore }}<small>/{{ summary.maxScore }}</small></strong>
          <div class="score-hero__rate">
            <span :style="{ width: `${Math.min(100, Number(summary.scoreRate) || 0)}%` }" />
          </div>
          <em>累计正确率 {{ Number(summary.scoreRate || 0).toFixed(1) }}%</em>
        </div>
      </section>

      <div class="grid grid--4 score-stats">
        <StatCard label="已完成" :value="completedCount" suffix="份" note="最近一次提交计入总分" icon="check-circle" tone="mint" />
        <StatCard label="待完成" :value="pendingCount" suffix="份" :note="pendingCount ? '可以随时进入作答' : '当前作业已全部提交'" icon="clipboard" tone="apricot" />
        <StatCard label="累计得分" :value="summary.totalScore" suffix="分" :note="`满分 ${summary.maxScore} 分`" icon="target" tone="peri" />
        <StatCard label="题目总数" :value="totalQuestions" suffix="题" note="已发布作业的题目合计" icon="book-open" tone="lav" />
      </div>

      <div class="list-toolbar">
        <div>
          <h3 class="section-title">已发布作业</h3>
          <p>共 {{ homeworks.length }} 份，按老师发布时间排列。</p>
        </div>
        <label class="search-box">
          <AppIcon name="search" :size="15" />
          <input v-model="search" type="search" placeholder="搜索作业名称…" aria-label="搜索作业" />
        </label>
      </div>

      <div v-if="filteredHomeworks.length" class="homework-list">
        <article
          v-for="homework in filteredHomeworks"
          :key="homework.id"
          class="homework-card"
          :class="{ 'is-submitted': homework.submitted }"
        >
          <div class="homework-card__mark">
            <AppIcon :name="homework.submitted ? 'check-circle' : 'clipboard'" :size="21" />
          </div>
          <div class="homework-card__main">
            <div class="homework-card__top">
              <span class="status-pill" :class="{ 'is-done': homework.submitted }">
                {{ homework.submitted ? '已提交' : '待作答' }}
              </span>
              <span v-if="homework.submitted" class="attempt-label">
                第 {{ homework.attemptNo }} 次提交
              </span>
            </div>
            <h3>{{ homework.title }}</h3>
            <p>{{ homework.description || '老师暂未填写作业说明，请直接查看题目。' }}</p>
            <div class="homework-card__meta">
              <span><AppIcon name="list" :size="13" />{{ homework.questionCount }} 道题</span>
              <span><AppIcon name="clock" :size="13" />建议 {{ homework.timeEstimateMinutes || 20 }} 分钟</span>
              <span v-if="homework.dueAt"><AppIcon name="calendar" :size="13" />截止 {{ formatDateTime(homework.dueAt) }}</span>
              <span v-if="homework.submitted"><AppIcon name="refresh" :size="13" />{{ formatDateTime(homework.submittedAt) }}</span>
            </div>
          </div>
          <div class="homework-card__action">
            <div class="score-chip" :class="{ 'is-empty': !homework.submitted }">
              <span>{{ homework.submitted ? '本次成绩' : '当前状态' }}</span>
              <b>{{ scoreText(homework) }}</b>
            </div>
            <button class="btn btn--soft btn--sm" type="button" @click="openHomework(homework)">
              {{ homework.submitted ? '查看结果 / 再练' : '开始作答' }}
              <AppIcon name="arrow-right" :size="13" />
            </button>
          </div>
        </article>
      </div>

      <section v-else-if="!homeworks.length" class="card">
        <AppEmpty
          icon="clipboard"
          title="老师还没有发布作业"
          description="老师发布后，作业会出现在这里；提交后可以立即查看分数和错题。"
        />
      </section>

      <AppEmpty
        v-else
        icon="search"
        title="没有匹配的作业"
        description="换一个关键词试试。"
      />

      <div class="privacy-note">
        <AppIcon name="shield" :size="14" />
        题目页面不会显示标准答案或教师解析；只有提交后，系统才返回本次逐题判分结果。
      </div>
    </template>
  </div>
</template>

<style scoped>
.homework-page {
  max-width: 1120px;
}

.score-hero {
  display: flex;
  align-items: center;
  gap: 28px;
  margin-bottom: 16px;
  padding: clamp(22px, 3vw, 31px);
  overflow: hidden;
  border: 1px solid #dce5fb;
  border-radius: 24px;
  background:
    radial-gradient(circle at 92% 8%, rgba(255, 255, 255, 0.95), transparent 33%),
    linear-gradient(122deg, #fff 0%, #eef3ff 62%, #f5f1fd 100%);
  box-shadow: 0 22px 52px -44px rgba(70, 91, 170, 0.42);
}

.score-hero__copy {
  min-width: 0;
  flex: 1;
}

.score-hero__eyebrow {
  color: var(--peri-deep);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.12em;
}

.score-hero h2 {
  margin-top: 8px;
  font-family: var(--font-serif);
  font-size: clamp(22px, 2.6vw, 31px);
  font-weight: 600;
  line-height: 1.35;
}

.score-hero p {
  max-width: 58ch;
  margin-top: 7px;
  color: var(--ink-3);
  font-size: 13px;
  line-height: 1.7;
}

.score-hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 17px;
}

.score-hero__number {
  width: 225px;
  flex: none;
  padding: 18px 20px;
  border: 1px solid rgba(107, 140, 245, 0.17);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.76);
  box-shadow: 0 14px 34px -28px rgba(66, 85, 155, 0.42);
}

.score-hero__number > span {
  display: block;
  color: var(--ink-3);
  font-size: 12px;
}

.score-hero__number strong {
  display: block;
  margin-top: 4px;
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 42px;
  font-weight: 600;
  line-height: 1.1;
}

.score-hero__number strong small {
  color: var(--ink-3);
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 500;
}

.score-hero__rate {
  height: 6px;
  margin-top: 12px;
  overflow: hidden;
  border-radius: 99px;
  background: #e8ecf8;
}

.score-hero__rate span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #9fb2f7, var(--peri));
}

.score-hero__number em {
  display: block;
  margin-top: 7px;
  color: var(--ink-3);
  font-size: 11px;
  font-style: normal;
}

.score-stats {
  margin-bottom: 19px;
}

.list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 11px;
}

.list-toolbar p {
  margin-top: 3px;
  color: var(--ink-3);
  font-size: 12px;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 7px;
  width: 250px;
  min-height: 40px;
  padding: 0 13px;
  border: 1px solid var(--line);
  border-radius: 99px;
  color: var(--ink-3);
  background: rgba(255, 255, 255, 0.86);
}

.search-box:focus-within {
  border-color: color-mix(in srgb, var(--peri) 52%, var(--line));
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--peri) 10%, transparent);
}

.search-box input {
  width: 100%;
  border: 0;
  outline: 0;
  color: var(--ink);
  background: transparent;
  font-size: 13px;
}

.homework-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.homework-card {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) auto;
  gap: 15px;
  align-items: center;
  padding: 18px 19px;
  border: 1px solid var(--line);
  border-radius: 17px;
  background: #fff;
  box-shadow: var(--shadow-xs);
  transition: border-color 0.18s, box-shadow 0.18s, transform 0.18s;
}

.homework-card:hover {
  transform: translateY(-1px);
  border-color: color-mix(in srgb, var(--peri) 24%, var(--line));
  box-shadow: var(--shadow-sm);
}

.homework-card__mark {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 14px;
  color: var(--peri-deep);
  background: var(--peri-soft);
}

.homework-card.is-submitted .homework-card__mark {
  color: var(--mint-deep);
  background: var(--mint-soft);
}

.homework-card__main {
  min-width: 0;
}

.homework-card__top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 8px;
  border-radius: 99px;
  color: var(--apricot-deep);
  background: var(--apricot-soft);
  font-size: 11px;
  font-weight: 600;
}

.status-pill::before {
  content: '';
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.status-pill.is-done {
  color: var(--mint-deep);
  background: var(--mint-soft);
}

.attempt-label {
  color: var(--ink-4);
  font-size: 11px;
}

.homework-card h3 {
  margin-top: 6px;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 17px;
  font-weight: 600;
  line-height: 1.45;
}

.homework-card__main > p {
  display: -webkit-box;
  max-width: 68ch;
  margin-top: 3px;
  overflow: hidden;
  color: var(--ink-3);
  font-size: 12.5px;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
}

.homework-card__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 15px;
  margin-top: 8px;
  color: var(--ink-3);
  font-size: 11.5px;
}

.homework-card__meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.homework-card__action {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 9px;
  min-width: 148px;
}

.score-chip {
  text-align: right;
}

.score-chip span {
  display: block;
  color: var(--ink-4);
  font-size: 10.5px;
}

.score-chip b {
  display: block;
  margin-top: 1px;
  color: var(--mint-deep);
  font-family: var(--font-numbers);
  font-size: 16px;
  font-weight: 600;
}

.score-chip.is-empty b {
  color: var(--ink-3);
  font-family: var(--font-body);
  font-size: 12px;
  font-weight: 500;
}

.privacy-note {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 13px;
  padding: 0 4px;
  color: var(--ink-4);
  font-size: 11.5px;
  line-height: 1.6;
}

.privacy-note .icon {
  flex: none;
  margin-top: 1px;
}

@media (max-width: 880px) {
  .score-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .score-hero__number {
    width: 100%;
  }

  .homework-card {
    grid-template-columns: 44px minmax(0, 1fr);
  }

  .homework-card__mark {
    width: 44px;
    height: 44px;
  }

  .homework-card__action {
    grid-column: 2;
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    width: 100%;
    min-width: 0;
  }
}

@media (max-width: 620px) {
  .list-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .search-box {
    width: 100%;
  }

  .homework-card {
    grid-template-columns: 1fr;
    padding: 15px;
  }

  .homework-card__mark {
    display: none;
  }

  .homework-card__action {
    grid-column: 1;
  }
}
</style>
