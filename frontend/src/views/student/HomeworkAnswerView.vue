<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { studentApi } from '@/api/student'
import { ApiError, errorMessage } from '@/api/errors'
import type {
  StudentHomeworkDetail,
  StudentHomeworkQuestion,
  StudentHomeworkResult,
} from '@/types/models'
import { useUiStore } from '@/stores/ui'
import { answerLabel, formatDateTime, pct } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'
import AppIcon from '@/components/AppIcon.vue'
import AppEmpty from '@/components/AppEmpty.vue'

const route = useRoute()
const router = useRouter()
const ui = useUiStore()
const homeworkId = Number(route.params.id)

const loading = ref(true)
const submitting = ref(false)
const error = ref('')
const homework = ref<StudentHomeworkDetail | null>(null)
const result = ref<StudentHomeworkResult | null>(null)
const answers = reactive<Record<number, string>>({})

const answeredCount = computed(
  () => homework.value?.questions.filter((question) => Boolean(answers[question.id]?.trim())).length ?? 0,
)
const questionCount = computed(() => homework.value?.questions.length ?? 0)
const progress = computed(() =>
  questionCount.value ? Math.round((answeredCount.value / questionCount.value) * 100) : 0,
)
const totalPoints = computed(
  () => homework.value?.questions.reduce((sum, question) => sum + Number(question.score), 0) ?? 0,
)

async function load(): Promise<void> {
  if (!Number.isInteger(homeworkId) || homeworkId <= 0) {
    error.value = '作业编号无效。'
    loading.value = false
    return
  }
  loading.value = true
  error.value = ''
  try {
    homework.value = await studentApi.getHomework(homeworkId)
    clearAnswerState()
    try {
      const latest = await studentApi.getLatestResult(homeworkId)
      result.value = latest
      hydrateAnswers(latest)
    } catch (cause) {
      if (!(cause instanceof ApiError) || cause.status !== 404) throw cause
      result.value = null
    }
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

function clearAnswerState(): void {
  if (!homework.value) return
  for (const question of homework.value.questions) answers[question.id] = ''
}

function hydrateAnswers(latest: StudentHomeworkResult): void {
  for (const answer of latest.answers) {
    answers[answer.questionId] = answer.selectedAnswer === '未作答' ? '' : answer.selectedAnswer
  }
}

function optionValue(question: StudentHomeworkQuestion, optionKey: string): string {
  return question.questionType === 'TRUE_FALSE' ? optionKey : optionKey
}

function selectOption(questionId: number, value: string): void {
  answers[questionId] = value
}

async function submit(): Promise<void> {
  if (!homework.value) return
  if (!answeredCount.value) {
    ui.toast('info', '还没有作答', '请至少选择一道题的答案后再提交。')
    return
  }
  submitting.value = true
  try {
    const payload: Record<number, string> = {}
    for (const question of homework.value.questions) {
      const value = answers[question.id]?.trim()
      if (value) payload[question.id] = value
    }
    result.value = await studentApi.submitHomework(homeworkId, payload)
    ui.toast('success', '提交成功', `本次得分 ${result.value.score} / ${result.value.maxScore} 分。`)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } catch (cause) {
    ui.toast('error', '提交失败', errorMessage(cause))
  } finally {
    submitting.value = false
  }
}

function retryHomework(): void {
  clearAnswerState()
  result.value = null
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function goBack(): void {
  void router.push('/student/homeworks')
}

function typeLabel(question: StudentHomeworkQuestion): string {
  return question.questionType === 'TRUE_FALSE' ? '判断题' : '单选题'
}

onMounted(load)
</script>

<template>
  <div class="answer-page">
    <PageHeader
      eyebrow="STUDENT · ANSWER SHEET"
      :title="homework?.title || '作业作答'"
      :subtitle="homework?.description || '请完成题目后提交，系统会立即判分。'"
    >
      <template #actions>
        <button class="btn btn--ghost btn--sm" type="button" @click="goBack">
          <AppIcon name="arrow-left" :size="14" />
          返回作业列表
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

    <template v-else-if="homework">
      <section v-if="result" class="result-hero" :class="{ 'is-passed': result.scoreRate >= 60 }">
        <span class="result-hero__icon">
          <AppIcon :name="result.scoreRate >= 60 ? 'check-circle' : 'target'" :size="27" />
        </span>
        <div class="result-hero__copy">
          <span>第 {{ result.attemptNo }} 次提交 · {{ formatDateTime(result.submittedAt) }}</span>
          <h2>{{ result.scoreRate >= 60 ? '本次完成得不错' : '再回看一遍错题，会更扎实' }}</h2>
          <p>
            共答对 {{ result.answers.filter((item) => item.isCorrect).length }} /
            {{ result.answers.length }} 道题，错误和未作答题目已自动加入错题本。
          </p>
        </div>
        <div class="result-hero__score">
          <strong>{{ result.score }}<small>/{{ result.maxScore }}</small></strong>
          <span>正确率 {{ pct(result.scoreRate, 1) }}</span>
        </div>
        <div class="result-hero__actions">
          <RouterLink to="/student/wrong-book" class="btn btn--soft btn--sm">查看错题本</RouterLink>
          <button class="btn btn--ghost btn--sm" type="button" @click="retryHomework">重新作答</button>
        </div>
      </section>

      <section v-else class="paper-head">
        <div>
          <span class="paper-head__eyebrow">答题进度</span>
          <h2>{{ answeredCount }} / {{ questionCount }} 道已作答</h2>
          <div class="paper-progress"><span :style="{ width: `${progress}%` }" /></div>
        </div>
        <div class="paper-head__meta">
          <span><b>{{ totalPoints }}</b> 总分</span>
          <span><b>{{ homework.timeEstimateMinutes || 20 }}</b> 分钟建议</span>
          <span v-if="homework.dueAt"><b>{{ formatDateTime(homework.dueAt) }}</b> 截止</span>
        </div>
      </section>

      <div v-if="homework.instructions" class="answer-instructions">
        <AppIcon name="info" :size="15" />
        <p>{{ homework.instructions }}</p>
      </div>

      <section class="question-list">
        <article
          v-for="question in homework.questions"
          :key="question.id"
          class="question-card"
          :class="{
            'is-correct': result?.answers.find((item) => item.questionId === question.id)?.isCorrect,
            'is-wrong': result && !result.answers.find((item) => item.questionId === question.id)?.isCorrect,
          }"
        >
          <div class="question-head">
            <span class="question-seq">{{ String(question.seqNo).padStart(2, '0') }}</span>
            <div>
              <span class="question-type">{{ typeLabel(question) }} · {{ question.score }} 分</span>
              <h3>{{ question.stem }}</h3>
            </div>
            <span
              v-if="result"
              class="judge-badge"
              :class="{ wrong: !result.answers.find((item) => item.questionId === question.id)?.isCorrect }"
            >
              <AppIcon
                :name="result.answers.find((item) => item.questionId === question.id)?.isCorrect ? 'check-circle' : 'x'"
                :size="13"
              />
              {{ result.answers.find((item) => item.questionId === question.id)?.isCorrect ? '正确' : '错误' }}
            </span>
          </div>

          <fieldset v-if="!result" class="option-list" :disabled="submitting">
            <legend class="visually-hidden">请选择第 {{ question.seqNo }} 题答案</legend>
            <label
              v-for="option in question.options"
              :key="option.key"
              class="option-row"
              :class="{ 'is-selected': answers[question.id] === optionValue(question, option.key) }"
              @click.prevent="selectOption(question.id, optionValue(question, option.key))"
            >
              <input
                type="radio"
                :name="`question-${question.id}`"
                :value="optionValue(question, option.key)"
                :checked="answers[question.id] === optionValue(question, option.key)"
                @change="selectOption(question.id, optionValue(question, option.key))"
              />
              <span class="option-key">{{ question.questionType === 'TRUE_FALSE' ? (option.key === 'TRUE' ? '对' : '错') : option.key }}</span>
              <span class="option-text">{{ option.text }}</span>
              <span class="option-check"><AppIcon name="check" :size="13" /></span>
            </label>
          </fieldset>

          <div v-else class="answer-review">
            <div class="review-line">
              <span>我的答案</span>
              <b>{{ answerLabel(
                result.answers.find((item) => item.questionId === question.id)?.selectedAnswer,
                question.questionType,
              ) }}</b>
            </div>
            <div class="review-line is-correct-answer">
              <span>正确答案</span>
              <b>{{ answerLabel(
                result.answers.find((item) => item.questionId === question.id)?.correctAnswer,
                question.questionType,
              ) }}</b>
            </div>
            <div
              v-if="result.answers.find((item) => item.questionId === question.id)?.teacherAnalysis"
              class="review-analysis"
            >
              <span>题目解析</span>
              <p>{{ result.answers.find((item) => item.questionId === question.id)?.teacherAnalysis }}</p>
            </div>
          </div>
        </article>
      </section>

      <div v-if="!result" class="submit-bar">
        <div>
          <b>{{ answeredCount }} / {{ questionCount }}</b>
          <span>提交后立即自动判分，仍可重新作答</span>
        </div>
        <button class="btn btn--primary" type="button" :disabled="submitting || !answeredCount" @click="submit">
          <AppIcon :name="submitting ? 'refresh' : 'check-circle'" :size="15" />
          {{ submitting ? '正在判分…' : '提交并查看成绩' }}
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.answer-page {
  max-width: 980px;
}

.result-hero {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  margin-bottom: 16px;
  padding: 21px 23px;
  border: 1px solid #f0ddd6;
  border-radius: 21px;
  background: linear-gradient(120deg, #fff, #fff8f5);
}

.result-hero.is-passed {
  border-color: #d8ece5;
  background: linear-gradient(120deg, #fff, #f1faf7);
}

.result-hero__icon {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: 15px;
  color: var(--apricot-deep);
  background: var(--apricot-soft);
}

.result-hero.is-passed .result-hero__icon {
  color: var(--mint-deep);
  background: var(--mint-soft);
}

.result-hero__copy {
  min-width: 0;
}

.result-hero__copy > span {
  color: var(--ink-4);
  font-size: 11px;
}

.result-hero__copy h2 {
  margin-top: 2px;
  font-family: var(--font-serif);
  font-size: 21px;
  font-weight: 600;
}

.result-hero__copy p {
  margin-top: 3px;
  color: var(--ink-3);
  font-size: 12px;
  line-height: 1.6;
}

.result-hero__score {
  text-align: right;
}

.result-hero__score strong {
  display: block;
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 36px;
  font-weight: 600;
  line-height: 1;
}

.result-hero__score small {
  color: var(--ink-3);
  font-family: var(--font-body);
  font-size: 13px;
}

.result-hero__score span {
  display: block;
  margin-top: 5px;
  color: var(--ink-3);
  font-size: 11px;
}

.result-hero__actions {
  display: flex;
  grid-column: 2 / -1;
  justify-content: flex-end;
  gap: 8px;
}

.paper-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 22px;
  margin-bottom: 12px;
  padding: 18px 20px;
  border: 1px solid #dde5fb;
  border-radius: 17px;
  background: linear-gradient(120deg, #fff, #f2f5ff);
}

.paper-head__eyebrow {
  color: var(--peri-deep);
  font-size: 11px;
  font-weight: 600;
}

.paper-head h2 {
  margin-top: 2px;
  font-family: var(--font-serif);
  font-size: 19px;
  font-weight: 600;
}

.paper-progress {
  width: min(280px, 55vw);
  height: 6px;
  margin-top: 8px;
  overflow: hidden;
  border-radius: 99px;
  background: #e6ebfa;
}

.paper-progress span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #a7b7f5, var(--peri));
  transition: width 0.25s var(--ease-out);
}

.paper-head__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 17px;
  color: var(--ink-3);
  font-size: 11.5px;
}

.paper-head__meta span {
  display: flex;
  flex-direction: column;
}

.paper-head__meta b {
  color: var(--ink);
  font-size: 14px;
  font-weight: 600;
}

.answer-instructions {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin-bottom: 12px;
  padding: 11px 14px;
  border-radius: 12px;
  color: var(--peri-deep);
  background: var(--peri-soft);
  font-size: 12px;
  line-height: 1.6;
}

.answer-instructions p {
  color: var(--ink-2);
}

.question-list {
  display: flex;
  flex-direction: column;
  gap: 11px;
}

.question-card {
  padding: 20px;
  border: 1px solid var(--line);
  border-radius: 17px;
  background: #fff;
  box-shadow: var(--shadow-xs);
}

.question-card.is-correct {
  border-color: #d5ebe4;
}

.question-card.is-wrong {
  border-color: #f0d9d3;
}

.question-head {
  display: flex;
  align-items: flex-start;
  gap: 11px;
}

.question-seq {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  flex: none;
  border-radius: 9px;
  color: var(--peri-deep);
  background: var(--peri-soft);
  font-family: var(--font-numbers);
  font-size: 12px;
  font-weight: 600;
}

.question-head > div {
  min-width: 0;
  flex: 1;
}

.question-type {
  color: var(--ink-4);
  font-size: 11px;
}

.question-head h3 {
  margin-top: 3px;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 16px;
  font-weight: 600;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.judge-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: none;
  padding: 4px 8px;
  border-radius: 99px;
  color: var(--mint-deep);
  background: var(--mint-soft);
  font-size: 11px;
  font-weight: 600;
}

.judge-badge.wrong {
  color: var(--coral-deep);
  background: var(--coral-soft);
}

.option-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin: 15px 0 0 45px;
  padding: 0;
  border: 0;
}

.option-row {
  display: flex;
  align-items: center;
  gap: 9px;
  min-height: 49px;
  padding: 9px 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fcfdfd;
  cursor: pointer;
  transition: border-color 0.18s, background-color 0.18s, box-shadow 0.18s;
}

.option-row:hover {
  border-color: color-mix(in srgb, var(--peri) 28%, var(--line));
  background: #f8faff;
}

.option-row.is-selected {
  border-color: color-mix(in srgb, var(--peri) 58%, var(--line));
  background: var(--peri-soft);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--peri) 10%, transparent);
}

.option-row input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.option-key {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  flex: none;
  border: 1px solid #dce2ee;
  border-radius: 8px;
  color: var(--ink-2);
  background: #fff;
  font-size: 12px;
  font-weight: 700;
}

.option-row.is-selected .option-key {
  border-color: var(--peri);
  color: #fff;
  background: var(--peri);
}

.option-text {
  flex: 1;
  color: var(--ink-2);
  font-size: 13px;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.option-check {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  flex: none;
  border-radius: 50%;
  color: transparent;
}

.option-row.is-selected .option-check {
  color: #fff;
  background: var(--peri);
}

.answer-review {
  margin: 14px 0 0 45px;
  padding: 13px 14px;
  border-radius: 12px;
  background: var(--surface-soft);
}

.review-line {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 12px;
}

.review-line + .review-line {
  margin-top: 5px;
}

.review-line span,
.review-analysis > span {
  min-width: 64px;
  color: var(--ink-4);
}

.review-line b {
  color: var(--ink-2);
  font-weight: 600;
}

.review-line.is-correct-answer b {
  color: var(--mint-deep);
}

.review-analysis {
  margin-top: 10px;
  padding-top: 9px;
  border-top: 1px dashed var(--line);
  font-size: 12px;
}

.review-analysis p {
  margin-top: 3px;
  color: var(--ink-2);
  line-height: 1.75;
  white-space: pre-wrap;
}

.submit-bar {
  position: sticky;
  bottom: 14px;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 15px;
  margin-top: 16px;
  padding: 13px 16px;
  border: 1px solid #dbe3f7;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.93);
  box-shadow: 0 18px 45px -30px rgba(41, 62, 124, 0.46);
  backdrop-filter: blur(12px);
}

.submit-bar div {
  display: flex;
  flex-direction: column;
}

.submit-bar b {
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 15px;
}

.submit-bar span {
  margin-top: 1px;
  color: var(--ink-3);
  font-size: 11px;
}

@media (max-width: 720px) {
  .result-hero {
    grid-template-columns: 44px minmax(0, 1fr);
    padding: 17px;
  }

  .result-hero__icon {
    width: 44px;
    height: 44px;
  }

  .result-hero__score {
    grid-column: 2;
    text-align: left;
  }

  .result-hero__actions {
    grid-column: 1 / -1;
    justify-content: flex-start;
  }

  .paper-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .paper-head__meta {
    width: 100%;
    justify-content: space-between;
    padding-top: 10px;
    border-top: 1px dashed var(--line);
  }

  .option-list {
    grid-template-columns: 1fr;
    margin-left: 0;
  }

  .answer-review {
    margin-left: 0;
  }

  .submit-bar {
    bottom: 8px;
    align-items: stretch;
    flex-direction: column;
  }

  .submit-bar .btn {
    width: 100%;
  }
}
</style>
