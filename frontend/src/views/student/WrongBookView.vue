<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { studentApi } from '@/api/student'
import { errorMessage } from '@/api/errors'
import type { StudentScoreSummary, StudentWrongQuestion } from '@/types/models'
import { answerLabel, formatDateTime, pct } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import AppIcon from '@/components/AppIcon.vue'
import AppEmpty from '@/components/AppEmpty.vue'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const search = ref('')
const selectedHomework = ref<number | null>(null)
const wrongQuestions = ref<StudentWrongQuestion[]>([])
const summary = ref<StudentScoreSummary>({
  totalScore: 0,
  maxScore: 0,
  scoreRate: 0,
  submissionCount: 0,
})

const homeworkOptions = computed(() => {
  const map = new Map<number, string>()
  for (const question of wrongQuestions.value) map.set(question.homeworkId, question.homeworkTitle)
  return Array.from(map, ([id, title]) => ({ id, title }))
})
const filteredQuestions = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return wrongQuestions.value.filter(
    (question) =>
      !keyword ||
      question.questionStem.toLowerCase().includes(keyword) ||
      question.homeworkTitle.toLowerCase().includes(keyword) ||
      String(question.seqNo).includes(keyword),
  )
})
const currentHomework = computed(() =>
  selectedHomework.value === null
    ? '全部作业'
    : homeworkOptions.value.find((item) => item.id === selectedHomework.value)?.title || '当前作业',
)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [questions, scoreSummary] = await Promise.all([
      studentApi.getWrongBook(selectedHomework.value ?? undefined),
      studentApi.getScoreSummary(),
    ])
    wrongQuestions.value = questions
    summary.value = scoreSummary
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

async function changeHomework(event: Event): Promise<void> {
  const value = Number((event.target as HTMLSelectElement).value)
  selectedHomework.value = value > 0 ? value : null
  await load()
}

function openHomework(homeworkId: number): void {
  void router.push(`/student/homeworks/${homeworkId}`)
}

onMounted(load)
</script>

<template>
  <div class="wrong-book-page">
    <PageHeader
      eyebrow="STUDENT · WRONG BOOK"
      title="我的错题本"
      subtitle="系统按每份作业最近一次提交自动收集错题和未作答题目。这里只有你的记录，不会展示其他同学的答案。"
    >
      <template #actions>
        <RouterLink to="/student/homeworks" class="btn btn--ghost btn--sm">
          <AppIcon name="clipboard" :size="14" />
          作业与成绩
        </RouterLink>
        <RouterLink to="/student/handouts" class="btn btn--primary btn--sm">
          <AppIcon name="book-open" :size="14" />
          查看老师讲评
        </RouterLink>
      </template>
    </PageHeader>

    <div v-if="loading" class="card loading-card">
      <span class="skeleton" style="width: 230px; height: 10px" />
    </div>

    <div v-else-if="error" class="card">
      <AppEmpty icon="alert-circle" title="错题本加载失败" :description="error">
        <button class="btn btn--ghost btn--sm" @click="load">重新加载</button>
      </AppEmpty>
    </div>

    <template v-else>
      <section class="wrong-hero">
        <div class="wrong-hero__icon"><AppIcon name="target" :size="30" /></div>
        <div class="wrong-hero__copy">
          <span>PERSONAL ERROR COLLECTION</span>
          <h2>{{ wrongQuestions.length ? `还有 ${wrongQuestions.length} 道题值得再看一遍` : '当前没有新的错题' }}</h2>
          <p>
            当前累计得分 {{ summary.totalScore }} / {{ summary.maxScore }} 分，
            累计正确率 {{ pct(summary.scoreRate, 1) }}。错题按最近一次提交实时更新。
          </p>
        </div>
        <div class="wrong-hero__ratio">
          <b>{{ wrongQuestions.length }}</b>
          <span>道错题</span>
        </div>
      </section>

      <div class="grid grid--3 wrong-stats">
        <StatCard label="累计得分" :value="summary.totalScore" suffix="分" :note="`满分 ${summary.maxScore} 分`" icon="target" tone="peri" />
        <StatCard label="累计正确率" :value="Number(summary.scoreRate || 0).toFixed(1)" suffix="%" note="按最近一次提交统计" icon="chart" tone="mint" />
        <StatCard label="错题数量" :value="wrongQuestions.length" suffix="题" note="包含未作答题目" icon="book-open" tone="apricot" />
      </div>

      <div class="wrong-toolbar">
        <div class="toolbar-copy">
          <h3 class="section-title">{{ currentHomework }}</h3>
          <p>{{ filteredQuestions.length }} 道题目，按提交时间和题号排列。</p>
        </div>
        <div class="toolbar-controls">
          <label class="select-wrap">
            <AppIcon name="list" :size="14" />
            <select :value="selectedHomework ?? 0" aria-label="按作业筛选错题" @change="changeHomework">
              <option :value="0">全部作业</option>
              <option v-for="item in homeworkOptions" :key="item.id" :value="item.id">{{ item.title }}</option>
            </select>
          </label>
          <label class="search-box">
            <AppIcon name="search" :size="15" />
            <input v-model="search" type="search" placeholder="搜索题目或作业…" aria-label="搜索错题" />
          </label>
        </div>
      </div>

      <div v-if="filteredQuestions.length" class="wrong-list">
        <article v-for="question in filteredQuestions" :key="`${question.homeworkId}-${question.questionId}`" class="wrong-card">
          <div class="wrong-card__side">
            <span>第</span>
            <b>{{ question.seqNo }}</b>
            <span>题</span>
          </div>
          <div class="wrong-card__body">
            <div class="wrong-card__head">
              <button type="button" class="homework-link" @click="openHomework(question.homeworkId)">
                {{ question.homeworkTitle }}
                <AppIcon name="arrow-right" :size="12" />
              </button>
              <span class="wrong-time">{{ formatDateTime(question.submittedAt) }} · 第 {{ question.attemptNo }} 次提交</span>
            </div>
            <h4>{{ question.questionStem }}</h4>
            <div class="answer-compare">
              <div class="answer-mine">
                <span>我的答案</span>
                <b>{{ answerLabel(question.selectedAnswer, question.questionType) }}</b>
              </div>
              <div class="answer-correct">
                <span>正确答案</span>
                <b>{{ answerLabel(question.correctAnswer, question.questionType) }}</b>
              </div>
            </div>
            <div v-if="question.teacherAnalysis" class="wrong-analysis">
              <span>解析</span>
              <p>{{ question.teacherAnalysis }}</p>
            </div>
            <div class="wrong-card__foot">
              <span><AppIcon name="target" :size="12" />本题 {{ question.maxScore }} 分</span>
              <button type="button" @click="openHomework(question.homeworkId)">
                重新练习这份作业
                <AppIcon name="arrow-right" :size="12" />
              </button>
            </div>
          </div>
        </article>
      </div>

      <section v-else-if="!wrongQuestions.length" class="card">
        <AppEmpty
          icon="check-circle"
          title="暂时没有错题"
          description="完成作业后，答错或未作答的题目会自动出现在这里。"
        >
          <RouterLink to="/student/homeworks" class="btn btn--primary btn--sm">去完成作业</RouterLink>
        </AppEmpty>
      </section>

      <AppEmpty
        v-else
        icon="search"
        title="没有匹配的错题"
        description="换一个关键词，或切换到全部作业。"
      />

      <div class="privacy-note">
        <AppIcon name="shield" :size="14" />
        错题本只返回你本人最近一次提交中的错误题目，不会混入其他学生记录或 AI 未审核草稿。
      </div>
    </template>
  </div>
</template>

<style scoped>
.wrong-book-page {
  max-width: 1080px;
}

.wrong-hero {
  display: flex;
  align-items: center;
  gap: 17px;
  margin-bottom: 16px;
  padding: 23px 25px;
  border: 1px solid #dce4fa;
  border-radius: 21px;
  background:
    radial-gradient(circle at 92% 4%, rgba(255, 255, 255, 0.94), transparent 33%),
    linear-gradient(120deg, #fff, #eff3ff 67%, #f8f2fc);
}

.wrong-hero__icon {
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  flex: none;
  border-radius: 17px;
  color: var(--peri-deep);
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 12px 30px -24px rgba(67, 85, 156, 0.46);
}

.wrong-hero__copy {
  min-width: 0;
  flex: 1;
}

.wrong-hero__copy > span {
  color: var(--peri-deep);
  font-family: var(--font-mono);
  font-size: 9.5px;
  font-weight: 600;
  letter-spacing: 0.1em;
}

.wrong-hero__copy h2 {
  margin-top: 3px;
  font-family: var(--font-serif);
  font-size: clamp(20px, 2.3vw, 27px);
  font-weight: 600;
  line-height: 1.4;
}

.wrong-hero__copy p {
  margin-top: 4px;
  color: var(--ink-3);
  font-size: 12.5px;
  line-height: 1.65;
}

.wrong-hero__ratio {
  min-width: 86px;
  flex: none;
  text-align: center;
}

.wrong-hero__ratio b {
  display: block;
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 37px;
  font-weight: 600;
  line-height: 1;
}

.wrong-hero__ratio span {
  display: block;
  margin-top: 4px;
  color: var(--ink-3);
  font-size: 11px;
}

.wrong-stats {
  margin-bottom: 18px;
}

.wrong-toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 15px;
  margin-bottom: 11px;
}

.toolbar-copy p {
  margin-top: 3px;
  color: var(--ink-3);
  font-size: 12px;
}

.toolbar-controls {
  display: flex;
  gap: 8px;
}

.select-wrap,
.search-box {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 40px;
  padding: 0 11px;
  border: 1px solid var(--line);
  border-radius: 11px;
  color: var(--ink-3);
  background: rgba(255, 255, 255, 0.86);
}

.select-wrap select {
  max-width: 180px;
  border: 0;
  outline: 0;
  color: var(--ink-2);
  background: transparent;
  font-size: 12px;
  cursor: pointer;
}

.search-box {
  width: 205px;
  border-radius: 99px;
}

.search-box input {
  width: 100%;
  border: 0;
  outline: 0;
  color: var(--ink);
  background: transparent;
  font-size: 12.5px;
}

.select-wrap:focus-within,
.search-box:focus-within {
  border-color: color-mix(in srgb, var(--peri) 52%, var(--line));
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--peri) 10%, transparent);
}

.wrong-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.wrong-card {
  display: flex;
  gap: 14px;
  padding: 17px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: #fff;
  box-shadow: var(--shadow-xs);
}

.wrong-card__side {
  display: flex;
  align-items: center;
  flex-direction: column;
  justify-content: center;
  width: 45px;
  height: 58px;
  flex: none;
  border-radius: 12px;
  color: var(--peri-deep);
  background: var(--peri-soft);
}

.wrong-card__side span {
  font-size: 9px;
}

.wrong-card__side b {
  font-family: var(--font-numbers);
  font-size: 19px;
  line-height: 1.2;
}

.wrong-card__body {
  min-width: 0;
  flex: 1;
}

.wrong-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.homework-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--peri-deep);
  font-size: 11px;
  font-weight: 600;
  text-align: left;
}

.homework-link:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.wrong-time {
  color: var(--ink-4);
  font-size: 10px;
}

.wrong-card h4 {
  margin-top: 5px;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 600;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.answer-compare {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 10px;
}

.answer-mine,
.answer-correct {
  padding: 9px 11px;
  border-radius: 10px;
  background: #fff7f4;
}

.answer-correct {
  background: var(--mint-soft);
}

.answer-mine span,
.answer-correct span,
.wrong-analysis > span {
  display: block;
  color: var(--ink-4);
  font-size: 10px;
}

.answer-mine b,
.answer-correct b {
  display: block;
  margin-top: 2px;
  color: var(--coral-deep);
  font-size: 12.5px;
  font-weight: 600;
  line-height: 1.55;
}

.answer-correct b {
  color: var(--mint-deep);
}

.wrong-analysis {
  margin-top: 9px;
  padding-top: 8px;
  border-top: 1px dashed var(--line);
}

.wrong-analysis p {
  margin-top: 3px;
  color: var(--ink-2);
  font-size: 12px;
  line-height: 1.75;
  white-space: pre-wrap;
}

.wrong-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 9px;
}

.wrong-card__foot > span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--ink-4);
  font-size: 10.5px;
}

.wrong-card__foot button {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--role-deep);
  font-size: 11px;
  font-weight: 600;
}

.wrong-card__foot button:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
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

@media (max-width: 780px) {
  .wrong-hero {
    align-items: flex-start;
  }

  .wrong-hero__ratio {
    display: none;
  }

  .wrong-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-controls {
    flex-wrap: wrap;
  }

  .select-wrap,
  .search-box {
    flex: 1;
    min-width: 180px;
  }
}

@media (max-width: 580px) {
  .wrong-card {
    padding: 14px;
  }

  .wrong-card__side {
    display: none;
  }

  .wrong-card__head {
    align-items: flex-start;
    flex-direction: column;
    gap: 2px;
  }

  .answer-compare {
    grid-template-columns: 1fr;
  }

  .wrong-card__foot {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
