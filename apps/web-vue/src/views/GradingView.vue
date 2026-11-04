<script setup>
import {
  ArrowRight,
  BarChart3,
  CheckCircle2,
  CircleHelp,
  ClipboardCheck,
  FileSpreadsheet,
  RefreshCw,
} from "@lucide/vue";
import { computed, onMounted, ref } from "vue";
import { RouterLink, useRoute } from "vue-router";
import EmptyState from "../components/EmptyState.vue";
import MathText from "../components/MathText.vue";
import PageHeader from "../components/PageHeader.vue";
import SectionCard from "../components/SectionCard.vue";
import StatCard from "../components/StatCard.vue";
import { getAssignment, getAttempts } from "../services/api";

const route = useRoute();
const assignment = ref(null);
const attempts = ref([]);
const loading = ref(true);
const notice = ref("");

const submitted = computed(() =>
  attempts.value.filter(
    (attempt) =>
      attempt.submitted === true ||
      attempt.submitted === 1 ||
      attempt.submitted === "true",
  ),
);
const graded = computed(() =>
  submitted.value.filter(
    (attempt) => attempt.grade !== null && attempt.grade !== undefined,
  ),
);
const average = computed(() => {
  if (!graded.value.length) return 0;
  return Math.round(
    graded.value.reduce(
      (total, attempt) => total + Number(attempt.grade || 0),
      0,
    ) / graded.value.length,
  );
});
const questionOutcomes = computed(() =>
  (assignment.value?.questions || []).map((question, index) => {
    const answered = submitted.value.filter(
      (attempt) => attempt.responses?.[question.id] !== undefined,
    ).length;
    const correct = submitted.value.filter(
      (attempt) => attempt.responses?.[question.id]?.isCorrect === true,
    ).length;
    const unanswered = Math.max(submitted.value.length - answered, 0);
    return {
      id: question.id,
      index: index + 1,
      question: question.question,
      type: question.type,
      correct,
      answered,
      unanswered,
      correctRate: answered
        ? Math.round((correct / Math.max(submitted.value.length, 1)) * 100)
        : 0,
    };
  }),
);

async function load() {
  loading.value = true;
  try {
    assignment.value = await getAssignment(route.params.assignmentId);
    attempts.value = await getAttempts(route.params.assignmentId);
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeader
      eyebrow="确定性统计"
      title="批改与统计分析"
      :description="`${assignment?.name || '当前作业'}的对错和得分由 Java 后端根据标准答案计算；AI 不参与最终判分。`"
    >
      <template #actions>
        <button type="button" class="button button-secondary" :disabled="loading" @click="load">
          <RefreshCw :size="16" />
          刷新统计
        </button>
        <button type="button" class="button button-primary" @click="notice = 'Excel 导入接口将在 Java MVP 的批改模块中接入，当前页面先保留入口。'">
          <FileSpreadsheet :size="16" />
          导入批改数据
        </button>
      </template>
    </PageHeader>

    <p v-if="notice" class="alert" role="status">{{ notice }}</p>

    <div class="stat-grid">
      <StatCard label="提交人数" :value="submitted.length" unit="人" hint="已完成的作业提交" />
      <StatCard label="平均得分率" :value="average" unit="%" hint="基于标准答案的确定性得分" tone="success" />
      <StatCard label="未作答" :value="0" unit="次" hint="第一版规则仍需教师最终确认" tone="warning" />
      <StatCard label="重点错题" :value="questionOutcomes.filter((item) => item.correctRate < 60).length" unit="道" hint="正确率低于 60% 的题目" tone="accent" />
    </div>

    <div class="grading-grid">
      <SectionCard title="题目正确率" description="选择题和判断题按标准答案自动判分">
        <div v-if="loading" class="skeleton block-skeleton" />
        <EmptyState
          v-else-if="!submitted.length"
          title="还没有可统计的提交"
          description="学生提交作业后，这里会显示各题正确率、选项分布和未作答情况。"
        />
        <div v-else class="question-stats">
          <article v-for="item in questionOutcomes" :key="item.id" class="question-stat">
            <span class="question-index">{{ item.index }}</span>
            <div class="question-main">
              <div class="question-title-row">
                <span class="badge badge-primary">{{ item.type === "TRUE_FALSE" ? "判断题" : "单选题" }}</span>
                <span>{{ item.correctRate }}% 正确</span>
              </div>
              <p><MathText :text="item.question" /></p>
              <div class="progress"><span :style="{ width: `${item.correctRate}%` }"></span></div>
              <small>正确 {{ item.correct }} · 已答 {{ item.answered }} · 未答 {{ item.unanswered }}</small>
            </div>
          </article>
        </div>
      </SectionCard>

      <div class="side-stack">
        <SectionCard title="判分边界" description="避免 AI 覆盖确定性结果">
          <div class="boundary-list">
            <div><CheckCircle2 :size="17" /><span><strong>系统负责</strong><p>对错、得分、未作答与统计口径</p></span></div>
            <div><CircleHelp :size="17" /><span><strong>AI 负责</strong><p>错误模式、可能原因和讲评草稿</p></span></div>
            <div><ClipboardCheck :size="17" /><span><strong>教师负责</strong><p>审核、修改、确认并发布讲评</p></span></div>
          </div>
        </SectionCard>

        <RouterLink :to="`/review/${route.params.assignmentId}`" class="next-step">
          <span class="next-icon"><BarChart3 :size="18" /></span>
          <span><strong>下一步：生成讲评草稿</strong><small>讲评不会被直接发布，必须先由教师审核。</small></span>
          <ArrowRight :size="17" />
        </RouterLink>
      </div>
    </div>
  </div>
</template>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.grading-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 330px;
  gap: 20px;
}

.side-stack {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.block-skeleton {
  height: 320px;
}

.question-stats {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.question-stat {
  display: flex;
  gap: 13px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--surface-soft);
  padding: 14px;
}

.question-index {
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  place-items: center;
  border-radius: 10px;
  background: var(--primary-soft);
  color: var(--primary);
  font-size: 11px;
  font-weight: 700;
}

.question-main {
  min-width: 0;
  flex: 1;
}

.question-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--text-muted);
  font-size: 10px;
}

.question-main p {
  margin: 7px 0 12px;
  font-size: 12px;
  font-weight: 600;
}

.question-main small {
  display: block;
  margin-top: 7px;
  color: var(--text-muted);
  font-size: 10px;
}

.boundary-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.boundary-list > div {
  display: flex;
  gap: 10px;
  color: var(--primary);
}

.boundary-list span {
  color: var(--text);
}

.boundary-list strong {
  font-size: 11px;
}

.boundary-list p {
  margin-top: 2px;
  color: var(--text-muted);
  font-size: 10px;
}

.next-step {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  border: 1px solid color-mix(in srgb, var(--role) 18%, var(--line));
  border-radius: var(--radius-lg);
  background: linear-gradient(145deg, var(--role-soft), white);
  padding: 16px;
  color: var(--primary);
}

.next-icon {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  border-radius: 12px;
  background: white;
}

.next-step span:nth-child(2) {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.next-step strong {
  color: var(--text);
  font-size: 11px;
}

.next-step small {
  color: var(--text-muted);
  font-size: 10px;
}

@media (max-width: 1100px) {
  .grading-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 820px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .stat-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
