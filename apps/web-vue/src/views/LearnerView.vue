<script setup>
import {
  ArrowLeft,
  ArrowRight,
  BookOpen,
  Check,
  CheckCircle2,
  CircleAlert,
  Clock3,
  FileCheck2,
  LoaderCircle,
  RotateCcw,
  Send,
  Sigma,
  X,
} from "@lucide/vue";
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import MathText from "../components/MathText.vue";

const route = useRoute();
const loading = ref(true);
const submitting = ref(false);
const error = ref("");
const assignment = ref(null);
const answers = reactive({});
const result = ref(null);
const reviewDraft = ref(null);
const corrections = ref([]);
const correctionSending = ref({});
const correctionChoices = reactive({});
const correctionNotes = reactive({});

const answeredCount = computed(() =>
  Object.values(answers).filter((value) => value !== "").length,
);
const questionCount = computed(() => assignment.value?.questions?.length || 0);
const progress = computed(() =>
  questionCount.value
    ? Math.round((answeredCount.value / questionCount.value) * 100)
    : 0,
);
const wrongQuestions = computed(
  () => result.value?.questions?.filter((question) => !question.correct) || [],
);
const publishedReview = computed(() =>
  reviewDraft.value?.status === "PUBLISHED" ? reviewDraft.value : null,
);

function choiceClass(questionId, choiceId) {
  return {
    selected: String(answers[questionId] || "") === String(choiceId),
  };
}

async function loadAssignment() {
  loading.value = true;
  error.value = "";
  try {
    const response = await fetch(
      `/java-api/student/assignments/${route.params.assignmentId}/questions`,
      { credentials: "include" },
    );
    const body = await response.json().catch(() => null);
    if (!response.ok) {
      throw new Error(body?.message || "作业加载失败");
    }
    assignment.value = body;
    const savedResult = await fetch(
      `/java-api/student/assignments/${route.params.assignmentId}/result`,
      { credentials: "include" },
    );
    if (savedResult.ok) {
      result.value = await savedResult.json();
      await loadPublishedReview();
      await loadCorrections();
    }
  } catch (requestError) {
    error.value = requestError.message || "暂时无法加载作业。";
  } finally {
    loading.value = false;
  }
}

async function submitAssignment() {
  if (answeredCount.value !== questionCount.value) {
    error.value = "请完成全部题目后再提交。";
    return;
  }
  submitting.value = true;
  error.value = "";
  try {
    const response = await fetch(
      `/java-api/student/assignments/${route.params.assignmentId}/submit`,
      {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ answers }),
      },
    );
    const body = await response.json().catch(() => null);
    if (!response.ok) {
      throw new Error(body?.message || "提交失败，请稍后重试。");
    }
    result.value = body;
    await Promise.all([loadPublishedReview(), loadCorrections()]);
    window.scrollTo({ top: 0, behavior: "smooth" });
  } catch (requestError) {
    error.value = requestError.message || "提交失败，请稍后重试。";
  } finally {
    submitting.value = false;
  }
}

async function loadPublishedReview() {
  const response = await fetch(
    `/java-api/reviews/${route.params.assignmentId}`,
    { credentials: "include" },
  ).catch(() => null);
  if (response?.ok) {
    reviewDraft.value = await response.json();
  }
}

async function loadCorrections() {
  const response = await fetch(
    `/java-api/student/assignments/${route.params.assignmentId}/corrections`,
    { credentials: "include" },
  ).catch(() => null);
  if (response?.ok) {
    corrections.value = await response.json();
  }
}

async function submitCorrection(question) {
  const choiceId = correctionChoices[question.questionId];
  if (!choiceId) {
    error.value = `请为第 ${question.questionId} 题重新选择答案。`;
    return;
  }
  correctionSending.value[question.questionId] = true;
  error.value = "";
  try {
    const response = await fetch(
      `/java-api/student/assignments/${route.params.assignmentId}/corrections`,
      {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          questionId: question.questionId,
          choiceId: Number(choiceId),
          explanation: correctionNotes[question.questionId] || "",
        }),
      },
    );
    const body = await response.json().catch(() => null);
    if (!response.ok) {
      throw new Error(body?.message || "订正提交失败。");
    }
    corrections.value = [body, ...corrections.value];
  } catch (requestError) {
    error.value = requestError.message || "订正提交失败。";
  } finally {
    correctionSending.value[question.questionId] = false;
  }
}

function choicesFor(questionId) {
  return (
    assignment.value?.questions?.find(
      (question) => question.id === questionId,
    )?.choices || []
  );
}

onMounted(loadAssignment);
</script>

<template>
  <main class="learner-page" data-role="student">
    <header class="learner-header">
      <div class="learner-brand">
        <span><Sigma :size="20" /></span>
        <div><strong>Mark 学习中心</strong><small>初中数学作业</small></div>
      </div>
      <div class="learner-header-right">
        <span class="badge badge-primary">学生端</span>
        <button type="button" class="icon-button" aria-label="返回登录页" @click="$router.push('/login')">
          <ArrowLeft :size="18" />
        </button>
      </div>
    </header>

    <div v-if="loading" class="learner-loading">
      <div class="skeleton learner-skeleton" />
    </div>

    <div v-else-if="error && !assignment" class="learner-error">
      <span><CircleAlert :size="23" /></span>
      <h1>作业暂时无法打开</h1>
      <p>{{ error }}</p>
      <button type="button" class="button button-primary" @click="loadAssignment">
        重新加载
      </button>
    </div>

    <div v-else-if="assignment" class="learner-content">
      <section class="assignment-hero">
        <div class="assignment-hero-copy">
          <p class="page-eyebrow">待完成作业</p>
          <h1>{{ assignment.name }}</h1>
          <p>{{ assignment.introduction }}</p>
        </div>
        <div class="assignment-facts">
          <span><Clock3 :size="17" /><strong>{{ assignment.timeEstimateMinutes }} 分钟</strong></span>
          <span><FileCheck2 :size="17" /><strong>{{ questionCount }} 道题</strong></span>
          <span><CheckCircle2 :size="17" /><strong>{{ assignment.passingGrade }}% 通过</strong></span>
        </div>
      </section>

      <p v-if="error" class="alert alert-danger" role="alert">{{ error }}</p>

      <template v-if="!result">
        <div class="progress-panel">
          <div>
            <strong>作答进度</strong>
            <span>{{ answeredCount }} / {{ questionCount }}</span>
          </div>
          <div class="progress"><span :style="{ width: `${progress}%` }"></span></div>
          <p>{{ assignment.instructions }}</p>
        </div>

        <form class="question-stack" @submit.prevent="submitAssignment">
          <article
            v-for="(question, index) in assignment.questions"
            :key="question.id"
            class="card student-question"
          >
            <div class="question-heading">
              <span class="question-number">{{ index + 1 }}</span>
              <div>
                <div class="question-tags">
                  <span class="badge badge-primary">
                    {{ question.type === "TRUE_FALSE" ? "判断题" : "单选题" }}
                  </span>
                  <span>{{ question.totalPoints }} 分</span>
                </div>
                <h2><MathText :text="question.question" /></h2>
              </div>
            </div>
            <div class="choice-list">
              <label
                v-for="choice in question.choices"
                :key="choice.id"
                class="choice-card"
                :class="choiceClass(question.id, choice.id)"
              >
                <input
                  v-model="answers[question.id]"
                  type="radio"
                  :name="`question-${question.id}`"
                  :value="String(choice.id)"
                />
                <span class="choice-letter">
                  {{ question.type === "TRUE_FALSE" ? (choice.label === "正确" ? "对" : "错") : String.fromCharCode(64 + choice.id) }}
                </span>
                <span><MathText :text="choice.label" /></span>
                <Check v-if="choiceClass(question.id, choice.id).selected" :size="17" />
              </label>
            </div>
          </article>

          <div class="submit-bar card">
            <div>
              <strong>{{ progress === 100 ? "可以提交了" : "请完成全部题目" }}</strong>
              <span>提交后由 Java 后端根据标准答案判分。</span>
            </div>
            <button type="submit" class="button button-primary" :disabled="submitting || progress < 100">
              <LoaderCircle v-if="submitting" class="spinning" :size="17" />
              <Send v-else :size="17" />
              {{ submitting ? "正在判分…" : "提交作业" }}
            </button>
          </div>
        </form>
      </template>

      <template v-else>
        <section class="result-summary card">
          <div class="result-mark" :class="{ passed: result.passed }">
            <CheckCircle2 v-if="result.passed" :size="28" />
            <CircleAlert v-else :size="28" />
          </div>
          <div>
            <p>{{ result.passed ? "作业已通过" : "本次作业未通过" }}</p>
            <h2>{{ result.score }} / {{ result.maxScore }} 分</h2>
            <span>正确率 {{ result.scoreRate }}% · 提交于 {{ new Date(result.submittedAt).toLocaleString("zh-CN") }}</span>
          </div>
          <span class="result-rate">{{ result.scoreRate }}%</span>
        </section>

        <div class="result-grid">
          <section class="card result-questions">
            <div class="card-header"><div><h2 class="card-title">逐题结果</h2><p class="card-description">对错和得分由标准答案确定</p></div></div>
            <div class="card-body result-question-list">
              <article v-for="(question, index) in result.questions" :key="question.questionId">
                <span class="result-icon" :class="{ correct: question.correct }">
                  <Check v-if="question.correct" :size="17" />
                  <X v-else :size="17" />
                </span>
                <div>
                  <span>第 {{ index + 1 }} 题 · {{ question.pointsAwarded }} / {{ question.maxPoints }} 分</span>
                  <p><MathText :text="question.question" /></p>
                  <small>你的答案：<MathText :text="question.selectedAnswer" /> · 标准答案：<MathText :text="question.correctAnswer" /></small>
                </div>
              </article>
            </div>
          </section>

          <section class="card review-card">
            <div class="card-header"><div><h2 class="card-title">教师讲评</h2><p class="card-description">只有教师确认并发布后才显示</p></div></div>
            <div class="card-body">
              <template v-if="publishedReview">
                <h3>{{ publishedReview.title }}</h3>
                <p>{{ publishedReview.overview }}</p>
                <div class="review-block"><strong>常见错误</strong><p>{{ publishedReview.commonErrors }}</p></div>
                <div class="review-block"><strong>可能原因</strong><p>{{ publishedReview.possibleReasons }}</p></div>
                <div class="review-block"><strong>正确思路</strong><p>{{ publishedReview.correctApproach }}</p></div>
                <div class="review-block"><strong>练习建议</strong><p>{{ publishedReview.practiceAdvice }}</p></div>
              </template>
              <div v-else class="review-waiting">
                <BookOpen :size="21" />
                <p>讲评尚未发布，请等待教师确认。</p>
              </div>
            </div>
          </section>
        </div>

        <section v-if="wrongQuestions.length" class="card correction-section">
          <div class="card-header">
            <div><h2 class="card-title">订正错题</h2><p class="card-description">重新选择答案并说明错误原因，教师确认后才算完成</p></div>
            <span class="badge badge-warning">{{ wrongQuestions.length }} 道待订正</span>
          </div>
          <div class="card-body correction-list">
            <article v-for="question in wrongQuestions" :key="question.questionId">
              <div class="correction-title"><RotateCcw :size="17" /><strong><MathText :text="question.question" /></strong></div>
              <div class="correction-choices">
                <label v-for="choice in choicesFor(question.questionId)" :key="choice.id">
                  <input v-model="correctionChoices[question.questionId]" type="radio" :name="`correction-${question.questionId}`" :value="choice.id" />
                  <MathText :text="choice.label" />
                </label>
              </div>
              <textarea v-model="correctionNotes[question.questionId]" class="textarea" placeholder="说明这次错误的原因和订正思路（选填）"></textarea>
              <div class="correction-actions">
                <span v-if="corrections.some((item) => item.questionId === question.questionId)" class="badge badge-success">
                  已提交，等待教师确认
                </span>
                <button
                  type="button"
                  class="button button-primary"
                  :disabled="correctionSending[question.questionId]"
                  @click="submitCorrection(question)"
                >
                  <LoaderCircle v-if="correctionSending[question.questionId]" class="spinning" :size="16" />
                  <ArrowRight v-else :size="16" />
                  提交订正
                </button>
              </div>
            </article>
          </div>
        </section>
      </template>
    </div>
  </main>
</template>

<style scoped>
.learner-page {
  min-height: 100dvh;
  background:
    radial-gradient(62rem 30rem at 50% -18rem, rgb(107 140 245 / 0.14), transparent),
    var(--bg);
}

.learner-header {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  min-height: 70px;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border);
  background: rgb(255 255 255 / 0.9);
  padding: 10px max(20px, calc((100vw - 1120px) / 2));
  backdrop-filter: blur(16px);
}

.learner-brand,
.learner-header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.learner-brand > span {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 11px;
  background: var(--primary);
  color: white;
}

.learner-brand div {
  display: flex;
  flex-direction: column;
}

.learner-brand strong {
  font-size: 13px;
}

.learner-brand small {
  color: var(--text-muted);
  font-size: 9px;
}

.learner-loading {
  width: min(100% - 32px, 920px);
  margin: 48px auto;
}

.learner-skeleton {
  height: 560px;
  border-radius: 24px;
}

.learner-error {
  display: flex;
  min-height: 70dvh;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px;
  text-align: center;
}

.learner-error > span {
  display: grid;
  width: 54px;
  height: 54px;
  place-items: center;
  border-radius: 16px;
  background: var(--danger-soft);
  color: var(--danger);
}

.learner-error h1 {
  margin-top: 13px;
  font-size: 22px;
}

.learner-error p {
  margin: 6px 0 18px;
  color: var(--text-secondary);
  font-size: 13px;
}

.learner-content {
  width: min(100% - 32px, 1040px);
  margin: 0 auto;
  padding: 36px 0 56px;
}

.assignment-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 28px;
  border: 1px solid color-mix(in srgb, var(--role) 18%, var(--line));
  border-radius: 24px;
  background: linear-gradient(145deg, var(--role-soft), #fff 60%);
  padding: clamp(24px, 4vw, 38px);
}

.assignment-hero-copy h1 {
  margin-top: 5px;
  font-size: clamp(28px, 4vw, 40px);
  letter-spacing: -0.035em;
}

.assignment-hero-copy > p:last-child {
  max-width: 680px;
  margin-top: 10px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.8;
}

.assignment-facts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.assignment-facts span {
  display: inline-flex;
  min-height: 38px;
  align-items: center;
  gap: 6px;
  border: 1px solid color-mix(in srgb, var(--role) 20%, var(--line));
  border-radius: 11px;
  background: rgb(255 255 255 / 0.8);
  padding: 8px 10px;
  color: var(--primary);
  font-size: 10px;
}

.assignment-facts strong {
  color: var(--text-secondary);
}

.progress-panel {
  margin: 20px 0;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: white;
  padding: 16px 18px;
}

.progress-panel > div:first-child {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 11px;
}

.progress-panel > div:first-child span,
.progress-panel > p {
  color: var(--text-muted);
}

.progress-panel > p {
  margin-top: 9px;
  font-size: 10px;
}

.question-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.student-question {
  padding: 20px;
}

.question-heading {
  display: flex;
  gap: 13px;
}

.question-number {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  place-items: center;
  border-radius: 11px;
  background: var(--primary-soft);
  color: var(--primary);
  font-size: 12px;
  font-weight: 700;
}

.question-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-muted);
  font-size: 10px;
}

.question-heading h2 {
  margin-top: 7px;
  font-size: 15px;
  line-height: 1.6;
}

.choice-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 9px;
  margin-top: 16px;
}

.choice-card {
  display: flex;
  min-height: 50px;
  align-items: center;
  gap: 9px;
  border: 1px solid var(--border);
  border-radius: 13px;
  background: #fbfdfc;
  padding: 10px 12px;
  cursor: pointer;
  font-size: 12px;
  transition:
    border-color 160ms ease,
    background 160ms ease;
}

.choice-card:hover {
  border-color: color-mix(in srgb, var(--role) 42%, var(--line));
}

.choice-card.selected {
  border-color: color-mix(in srgb, var(--role) 48%, var(--line));
  background: var(--primary-soft);
  color: var(--primary-strong);
}

.choice-card input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.choice-letter {
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  place-items: center;
  border-radius: 8px;
  background: white;
  color: var(--text-secondary);
  font-size: 10px;
  font-weight: 700;
}

.choice-card > span:nth-of-type(2) {
  flex: 1;
}

.choice-card > svg {
  color: var(--primary);
}

.submit-bar {
  position: sticky;
  bottom: 16px;
  z-index: 15;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 14px 16px;
  box-shadow: var(--shadow-lg);
}

.submit-bar > div {
  display: flex;
  flex-direction: column;
}

.submit-bar strong {
  font-size: 12px;
}

.submit-bar span {
  color: var(--text-muted);
  font-size: 10px;
}

.result-summary {
  display: grid;
  grid-template-columns: 54px minmax(0, 1fr) auto;
  align-items: center;
  gap: 15px;
  margin-top: 20px;
  padding: 20px;
}

.result-mark {
  display: grid;
  width: 54px;
  height: 54px;
  place-items: center;
  border-radius: 16px;
  background: var(--danger-soft);
  color: var(--danger);
}

.result-mark.passed {
  background: var(--success-soft);
  color: var(--success);
}

.result-summary p,
.result-summary span {
  color: var(--text-muted);
  font-size: 11px;
}

.result-summary h2 {
  margin: 2px 0;
  font-size: 24px;
}

.result-rate {
  color: var(--primary) !important;
  font-size: 22px !important;
  font-weight: 750;
}

.result-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(320px, 0.9fr);
  gap: 20px;
  margin-top: 20px;
}

.result-question-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.result-question-list article {
  display: flex;
  gap: 11px;
  border: 1px solid var(--border);
  border-radius: 13px;
  background: var(--surface-soft);
  padding: 12px;
}

.result-icon {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  place-items: center;
  border-radius: 9px;
  background: var(--danger-soft);
  color: var(--danger);
}

.result-icon.correct {
  background: var(--success-soft);
  color: var(--success);
}

.result-question-list article > div {
  min-width: 0;
}

.result-question-list span,
.result-question-list small {
  color: var(--text-muted);
  font-size: 10px;
}

.result-question-list p {
  margin: 3px 0;
  font-size: 11px;
  font-weight: 600;
}

.review-card h3 {
  font-size: 15px;
}

.review-card h3 + p,
.review-block p {
  margin-top: 5px;
  color: var(--text-secondary);
  font-size: 11px;
  line-height: 1.8;
  white-space: pre-wrap;
}

.review-block {
  margin-top: 15px;
  border-top: 1px solid var(--border);
  padding-top: 12px;
}

.review-block strong {
  font-size: 11px;
}

.review-waiting {
  display: flex;
  min-height: 140px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 1px dashed var(--border-strong);
  border-radius: 13px;
  color: var(--text-muted);
  text-align: center;
}

.review-waiting p {
  margin-top: 7px;
  font-size: 11px;
}

.correction-section {
  margin-top: 20px;
}

.correction-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.correction-list article {
  border: 1px solid var(--border);
  border-radius: 15px;
  background: var(--surface-soft);
  padding: 15px;
}

.correction-title {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  color: var(--warning);
}

.correction-title strong {
  color: var(--text);
  font-size: 12px;
}

.correction-choices {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0;
}

.correction-choices label {
  display: inline-flex;
  min-height: 38px;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: white;
  padding: 7px 10px;
  font-size: 11px;
  cursor: pointer;
}

.correction-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 8px;
}

.spinning {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 900px) {
  .assignment-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .result-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  .learner-content {
    padding-top: 20px;
  }

  .assignment-hero {
    padding: 22px;
  }

  .choice-list {
    grid-template-columns: minmax(0, 1fr);
  }

  .submit-bar {
    align-items: stretch;
    flex-direction: column;
    bottom: 8px;
  }

  .submit-bar .button {
    width: 100%;
  }

  .result-summary {
    grid-template-columns: 46px minmax(0, 1fr);
  }

  .result-rate {
    grid-column: 2;
  }
}
</style>
