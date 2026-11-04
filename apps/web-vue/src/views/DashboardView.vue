<script setup>
import {
  ArrowUpRight,
  BrainCircuit,
  BookOpen,
  CalendarDays,
  CheckCircle2,
  ClipboardCheck,
  Clock3,
  RotateCcw,
  RefreshCw,
  TrendingUp,
} from "@lucide/vue";
import { computed, onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import PageHeader from "../components/PageHeader.vue";
import SectionCard from "../components/SectionCard.vue";
import StatCard from "../components/StatCard.vue";
import {
  getAssignment,
  getAssignmentInsights,
  getAssignments,
  getAttempts,
  getUser,
} from "../services/api";

const loading = ref(true);
const error = ref("");
const user = ref(null);
const assignment = ref(null);
const attempts = ref([]);
const insights = ref(null);
const reviewDraft = ref(null);
const updatedAt = ref(null);

const displayName = computed(() => {
  const local = (user.value?.userId || "teacher").split("@")[0];
  return ["user", "teacher", "admin"].includes(local.toLowerCase())
    ? "教师用户"
    : local;
});

const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 6) return "夜深了";
  if (hour < 12) return "上午好";
  if (hour < 14) return "中午好";
  if (hour < 18) return "下午好";
  return "晚上好";
});

const submittedCount = computed(
  () =>
    attempts.value.filter(
      (attempt) =>
        attempt.submitted === true ||
        attempt.submitted === 1 ||
        attempt.submitted === "true",
    ).length,
);
const gradedCount = computed(
  () =>
    attempts.value.filter(
      (attempt) => attempt.grade !== null && attempt.grade !== undefined,
    ).length,
);
const pendingReviewCount = computed(() =>
  ["DRAFT", "REVIEWED"].includes(reviewDraft.value?.status) ? 1 : 0,
);
const averageGrade = computed(() => {
  const grades = attempts.value
    .map((attempt) => Number(attempt.grade))
    .filter((grade) => Number.isFinite(grade));
  if (!grades.length) return 0;
  return Math.round(grades.reduce((sum, grade) => sum + grade, 0) / grades.length);
});
const uniqueLearners = computed(
  () => new Set(attempts.value.map((attempt) => attempt.userId).filter(Boolean)).size,
);
const questionCount = computed(() => assignment.value?.questions?.length || 0);
const completionRate = computed(() =>
  attempts.value.length
    ? Math.round((submittedCount.value / attempts.value.length) * 100)
    : 0,
);
const weakQuestions = computed(() =>
  (insights.value?.questions || [])
    .filter((question) => (question.correctPercentage ?? 100) < 50)
    .sort(
      (left, right) =>
        (left.correctPercentage ?? 0) - (right.correctPercentage ?? 0),
    )
    .slice(0, 4),
);
const recentAttempts = computed(() =>
  [...attempts.value]
    .sort(
      (left, right) =>
        new Date(right.createdAt || 0).getTime() -
        new Date(left.createdAt || 0).getTime(),
    )
    .slice(0, 5),
);
const weeklyBars = computed(() => {
  const result = [];
  for (let index = 6; index >= 0; index -= 1) {
    const date = new Date();
    date.setDate(date.getDate() - index);
    const key = date.toDateString();
    const count = attempts.value.filter((attempt) => {
      const createdAt = attempt.createdAt
        ? new Date(attempt.createdAt)
        : null;
      return createdAt && createdAt.toDateString() === key;
    }).length;
    result.push({
      label: ["日", "一", "二", "三", "四", "五", "六"][date.getDay()],
      count,
    });
  }
  return result;
});
const maxBar = computed(() =>
  Math.max(...weeklyBars.value.map((bar) => bar.count), 1),
);

async function loadDashboard() {
  loading.value = true;
  error.value = "";
  try {
    const session = await getUser();
    user.value = session;
    const assignments = await getAssignments();
    const targetId = session?.assignmentId || assignments[0]?.id || 1;
    const [assignmentResult, attemptRows, reviewResult] = await Promise.all([
      getAssignment(targetId),
      getAttempts(targetId),
      fetch(`/java-api/reviews/${targetId}`, { credentials: "include" })
        .then((response) => (response.ok ? response.json() : null))
        .catch(() => null),
    ]);
    assignment.value = assignmentResult;
    attempts.value = attemptRows;
    insights.value = attemptRows.length
      ? await getAssignmentInsights(targetId)
      : null;
    reviewDraft.value = reviewResult;
    updatedAt.value = new Date();
  } catch (requestError) {
    error.value =
      requestError?.message || "工作台数据暂时无法加载，请稍后重试。";
  } finally {
    loading.value = false;
  }
}

onMounted(loadDashboard);
</script>

<template>
  <div class="page dashboard-page">
    <PageHeader
      eyebrow="今日教学概览"
      :title="`${greeting}，${displayName}`"
      :description="`聚焦当前作业与学情数据。${assignment?.name ? `当前正在查看《${assignment.name}》。` : '选择作业后可查看完整数据。'}`"
    >
      <template #actions>
        <button
          type="button"
          class="button button-secondary"
          :disabled="loading"
          @click="loadDashboard"
        >
          <RefreshCw :size="16" :class="{ spinning: loading }" />
          刷新数据
        </button>
        <RouterLink
          :to="`/author/${assignment?.id || user?.assignmentId || 1}`"
          class="button button-primary"
        >
          <BookOpen :size="16" />
          进入作业编辑
        </RouterLink>
      </template>
    </PageHeader>

    <div v-if="error" class="alert alert-danger" role="alert">
      {{ error }}
    </div>

    <template v-else>
      <div class="stat-grid" :aria-busy="loading">
        <template v-if="loading">
          <div v-for="index in 4" :key="index" class="skeleton stat-skeleton" />
        </template>
        <template v-else>
          <StatCard
            label="已提交"
            :value="submittedCount"
            unit="份"
            :hint="`共收到 ${attempts.length} 次作答记录`"
          />
          <StatCard
            label="待审核讲评"
            :value="pendingReviewCount"
            unit="份"
            :hint="
              reviewDraft?.status === 'PUBLISHED'
                ? '教师确认版已发布'
                : 'AI 草稿或手工讲评等待审核'
            "
            tone="warning"
          />
          <StatCard
            label="参与学生"
            :value="uniqueLearners"
            unit="人"
            :hint="`${gradedCount} 份已有评分`"
            tone="success"
          />
          <StatCard
            label="平均得分率"
            :value="averageGrade"
            unit="%"
            :hint="`完成率 ${completionRate}%`"
            tone="accent"
          />
        </template>
      </div>

      <div class="dashboard-grid">
        <div class="main-column">
          <SectionCard
            title="近 7 天提交趋势"
            description="按提交时间统计每日作答数量"
          >
            <template #actions>
              <span class="badge badge-primary">
                <CalendarDays :size="13" />
                最近 7 天
              </span>
            </template>
            <div v-if="loading" class="skeleton chart-skeleton" />
            <div v-else class="bar-chart" role="img" aria-label="近七天提交数量柱状图">
              <div
                v-for="bar in weeklyBars"
                :key="bar.label"
                class="bar-column"
              >
                <span class="bar-value">{{ bar.count }}</span>
                <div class="bar-track">
                  <span
                    class="bar-fill"
                    :style="{
                      height: `${Math.max((bar.count / maxBar) * 100, bar.count ? 8 : 2)}%`,
                    }"
                  ></span>
                </div>
                <span class="bar-label">周{{ bar.label }}</span>
              </div>
            </div>
          </SectionCard>

          <SectionCard
            title="当前作业"
            description="快速查看作业内容、题目结构与发布状态"
          >
            <template #actions>
              <RouterLink
                :to="`/author/${assignment?.id || 1}`"
                class="text-link"
              >
                查看详情
                <ArrowUpRight :size="14" />
              </RouterLink>
            </template>
            <div v-if="loading" class="skeleton assignment-skeleton" />
            <div v-else class="assignment-overview">
              <div class="assignment-icon">
                <ClipboardCheck :size="23" />
              </div>
              <div class="assignment-copy">
                <div class="assignment-title-row">
                  <h3>{{ assignment?.name || "尚未创建作业" }}</h3>
                  <span
                    class="badge"
                    :class="assignment?.published ? 'badge-success' : 'badge-warning'"
                  >
                    {{ assignment?.published ? "已发布" : "草稿" }}
                  </span>
                </div>
                <p>
                  {{
                    assignment?.introduction ||
                    "创建作业后，可在这里查看题目、作答与批改进度。"
                  }}
                </p>
                <div class="assignment-meta">
                  <span><BookOpen :size="14" />{{ questionCount }} 道题目</span>
                  <span><Clock3 :size="14" />{{ assignment?.timeEstimateMinutes || 1 }} 分钟</span>
                  <span><CheckCircle2 :size="14" />选择与判断题型</span>
                </div>
              </div>
            </div>
          </SectionCard>

          <SectionCard
            title="薄弱题目"
            description="正确率低于 50% 的题目会优先显示在这里"
          >
            <div v-if="loading" class="skeleton list-skeleton" />
            <div v-else-if="!weakQuestions.length" class="empty-inline">
              当前没有明显薄弱的题目，整体掌握情况稳定。
            </div>
            <ul v-else class="weak-list">
              <li v-for="question in weakQuestions" :key="question.id">
                <div>
                  <p>{{ question.question || `题目 #${question.id}` }}</p>
                  <span>{{ question.responseCount || 0 }} 次作答</span>
                </div>
                <strong>{{ Math.round(question.correctPercentage || 0) }}%</strong>
                <div class="progress">
                  <span
                    :style="{ width: `${Math.max(question.correctPercentage || 0, 3)}%` }"
                  ></span>
                </div>
              </li>
            </ul>
          </SectionCard>
        </div>

        <aside class="side-column">
          <SectionCard title="快捷操作" description="常用教学流程">
            <div class="quick-actions">
              <RouterLink :to="`/grading/${assignment?.id || 1}`">
                <span class="quick-icon primary"><ClipboardCheck :size="17" /></span>
                <span><strong>查看批改统计</strong><small>正确率、选项分布与重点错题</small></span>
                <ArrowUpRight :size="15" />
              </RouterLink>
              <RouterLink :to="`/review/${assignment?.id || 1}`">
                <span class="quick-icon success"><BrainCircuit :size="17" /></span>
                <span><strong>审核 AI 讲评</strong><small>修改、确认并发布讲评内容</small></span>
                <ArrowUpRight :size="15" />
              </RouterLink>
              <RouterLink to="/corrections">
                <span class="quick-icon warning"><RotateCcw :size="17" /></span>
                <span><strong>跟踪学生订正</strong><small>查看待订正、已提交与教师确认</small></span>
                <ArrowUpRight :size="15" />
              </RouterLink>
            </div>
          </SectionCard>

          <SectionCard title="最近活动" description="最新作答与提交">
            <div v-if="loading" class="skeleton activity-skeleton" />
            <div v-else-if="!recentAttempts.length" class="empty-inline">
              暂无新的作答记录。
            </div>
            <ol v-else class="activity-list">
              <li v-for="attempt in recentAttempts" :key="attempt.id">
                <span class="activity-dot"><CheckCircle2 :size="14" /></span>
                <div>
                  <p>
                    <strong>{{ (attempt.userId || "学生").split("@")[0] }}</strong>
                    {{ attempt.submitted ? "提交了作业" : "开始了作答" }}
                  </p>
                  <small>{{ attempt.createdAt ? new Date(attempt.createdAt).toLocaleString("zh-CN") : "刚刚" }}</small>
                </div>
              </li>
            </ol>
          </SectionCard>

          <div class="insight-banner">
            <TrendingUp :size="19" />
            <div>
              <strong>数据更新时间</strong>
              <span>{{ updatedAt ? updatedAt.toLocaleString("zh-CN") : "等待刷新" }}</span>
            </div>
          </div>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.stat-skeleton {
  height: 142px;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 330px;
  gap: 20px;
}

.main-column,
.side-column {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 20px;
}

.chart-skeleton {
  height: 240px;
}

.bar-chart {
  display: grid;
  height: 240px;
  grid-template-columns: repeat(7, minmax(36px, 1fr));
  align-items: end;
  gap: 14px;
  border-bottom: 1px solid var(--border);
  background-image: linear-gradient(to bottom, var(--line) 1px, transparent 1px);
  background-size: 100% 25%;
  padding: 16px 10px 0;
}

.bar-column {
  display: flex;
  min-width: 0;
  height: 100%;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: 7px;
}

.bar-track {
  display: flex;
  width: min(40px, 72%);
  flex: 1;
  align-items: flex-end;
  overflow: hidden;
  border-radius: 9px 9px 4px 4px;
  background: color-mix(in srgb, var(--peri) 8%, white);
}

.bar-fill {
  width: 100%;
  min-height: 3px;
  border-radius: inherit;
  background: linear-gradient(180deg, color-mix(in srgb, var(--role) 55%, white), var(--primary));
  transition: height 280ms ease;
}

.bar-value,
.bar-label {
  color: var(--text-muted);
  font-size: 10px;
}

.bar-value {
  min-height: 16px;
  color: var(--text-secondary);
  font-weight: 650;
}

.assignment-skeleton {
  height: 128px;
}

.assignment-overview {
  display: flex;
  gap: 15px;
}

.assignment-icon {
  display: grid;
  width: 52px;
  height: 52px;
  flex: 0 0 52px;
  place-items: center;
  border-radius: 15px;
  background: var(--primary-soft);
  color: var(--primary);
}

.assignment-copy {
  min-width: 0;
  flex: 1;
}

.assignment-title-row {
  display: flex;
  align-items: center;
  gap: 9px;
}

.assignment-title-row h3 {
  font-size: 15px;
  font-weight: 720;
}

.assignment-copy > p {
  display: -webkit-box;
  overflow: hidden;
  margin-top: 5px;
  color: var(--text-secondary);
  font-size: 13px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.assignment-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 13px;
  margin-top: 13px;
  color: var(--text-muted);
  font-size: 11px;
}

.assignment-meta span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.text-link {
  display: inline-flex;
  min-height: 34px;
  align-items: center;
  gap: 4px;
  color: var(--primary);
  font-size: 12px;
  font-weight: 650;
}

.list-skeleton {
  height: 150px;
}

.empty-inline {
  border: 1px dashed var(--border);
  border-radius: 12px;
  background: var(--surface-soft);
  padding: 18px;
  color: var(--text-muted);
  font-size: 12px;
}

.weak-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.weak-list li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 5px 16px;
}

.weak-list p {
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.weak-list span {
  color: var(--text-muted);
  font-size: 11px;
}

.weak-list strong {
  color: var(--warning);
  font-size: 13px;
}

.weak-list .progress {
  grid-column: 1 / -1;
  margin-top: 4px;
}

.weak-list .progress > span {
  background: linear-gradient(90deg, #efb083, var(--accent));
}

.quick-actions {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.quick-actions a {
  display: grid;
  min-height: 58px;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  border-radius: 12px;
  padding: 8px;
  transition: background 160ms ease;
}

.quick-actions a:hover {
  background: var(--surface-soft);
}

.quick-actions a > span:nth-child(2) {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.quick-actions strong {
  font-size: 12px;
}

.quick-actions small {
  color: var(--text-muted);
  font-size: 10px;
}

.quick-actions a > svg {
  color: var(--text-muted);
}

.quick-icon {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  border-radius: 11px;
}

.quick-icon.primary {
  background: var(--primary-soft);
  color: var(--primary);
}

.quick-icon.success {
  background: var(--success-soft);
  color: var(--success);
}

.quick-icon.warning {
  background: var(--warning-soft);
  color: var(--warning);
}

.activity-skeleton {
  height: 130px;
}

.activity-list {
  display: flex;
  flex-direction: column;
  gap: 13px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.activity-list li {
  display: flex;
  gap: 10px;
}

.activity-dot {
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  place-items: center;
  border-radius: 9px;
  background: var(--success-soft);
  color: var(--success);
}

.activity-list p {
  font-size: 11px;
}

.activity-list small {
  color: var(--text-muted);
  font-size: 10px;
}

.insight-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid color-mix(in srgb, var(--role) 18%, var(--line));
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--role-soft), #fff);
  padding: 14px 16px;
  color: var(--primary);
}

.insight-banner div {
  display: flex;
  flex-direction: column;
}

.insight-banner strong {
  color: var(--text);
  font-size: 11px;
}

.insight-banner span {
  color: var(--text-muted);
  font-size: 10px;
}

.spinning {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1180px) {
  .dashboard-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .side-column {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .insight-banner {
    grid-column: 1 / -1;
  }
}

@media (max-width: 880px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 680px) {
  .stat-grid,
  .side-column {
    grid-template-columns: minmax(0, 1fr);
  }

  .bar-chart {
    gap: 6px;
  }
}
</style>
