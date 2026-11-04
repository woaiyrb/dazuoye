<script setup>
import {
  ArrowLeft,
  BookOpen,
  Check,
  CheckCircle2,
  ChevronRight,
  CircleHelp,
  ClipboardCheck,
  Clock3,
  Eye,
  FileText,
  ListChecks,
  Save,
  Settings2,
  Sparkles,
  UploadCloud,
} from "@lucide/vue";
import { computed, onMounted, ref } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import EmptyState from "../components/EmptyState.vue";
import MathText from "../components/MathText.vue";
import PageHeader from "../components/PageHeader.vue";
import SectionCard from "../components/SectionCard.vue";
import StatCard from "../components/StatCard.vue";
import { formatDate, getAssignment } from "../services/api";

const route = useRoute();
const router = useRouter();
const assignment = ref(null);
const loading = ref(true);
const error = ref("");
const activeTab = ref("overview");
const legacyWebUrl =
  import.meta.env.VITE_LEGACY_WEB_URL || "http://localhost:3010";

const tabs = [
  { id: "overview", label: "作业概览", icon: ClipboardCheck },
  { id: "questions", label: "题目设计", icon: ListChecks },
  { id: "settings", label: "评分与规则", icon: Settings2 },
  { id: "review", label: "发布检查", icon: CheckCircle2 },
];

const questionCount = computed(() => assignment.value?.questions?.length || 0);
const totalPoints = computed(() =>
  (assignment.value?.questions || []).reduce(
    (sum, question) => sum + Number(question.totalPoints || 0),
    0,
  ),
);
const singleChoiceQuestions = computed(
  () =>
    (assignment.value?.questions || []).filter(
      (question) => question.type === "SINGLE_CORRECT",
    ).length,
);
const trueFalseQuestions = computed(
  () =>
    (assignment.value?.questions || []).filter(
      (question) => question.type === "TRUE_FALSE",
    ).length,
);

const typeLabels = {
  TEXT: "文本作答",
  SINGLE_CORRECT: "单选题",
  MULTIPLE_CORRECT: "多选题",
  TRUE_FALSE: "判断题",
  URL: "链接提交",
  UPLOAD: "文件上传",
  LINK_FILE: "链接文件",
  CODE: "代码题",
};

async function loadAssignment() {
  loading.value = true;
  error.value = "";
  try {
    assignment.value = await getAssignment(route.params.assignmentId);
  } catch (requestError) {
    error.value =
      requestError?.message || "作业内容加载失败，请检查服务是否正常运行。";
  } finally {
    loading.value = false;
  }
}

function openLegacy(path = "") {
  window.open(
    `${legacyWebUrl}/author/${route.params.assignmentId}${path}`,
    "_blank",
    "noopener,noreferrer",
  );
}

function openQuestionEditor() {
  router.push(`/author/${route.params.assignmentId}/questions`);
}

onMounted(loadAssignment);
</script>

<template>
  <div class="page author-page">
    <PageHeader
      eyebrow="作业编辑中心"
      :title="assignment?.name || '作业编辑'"
      :description="assignment?.introduction || '统一管理作业内容、题目、评分方式和发布状态。'"
    >
      <template #actions>
        <RouterLink
          :to="`/author/${route.params.assignmentId}/generate`"
          class="button button-secondary"
        >
          <Sparkles :size="16" />
          批量生成题目
        </RouterLink>
        <RouterLink to="/dashboard" class="button button-secondary">
          <ArrowLeft :size="16" />
          返回工作台
        </RouterLink>
        <button
          type="button"
          class="button button-secondary"
          @click="openLegacy()"
        >
          <Eye :size="16" />
          预览作业
        </button>
        <button
          type="button"
          class="button button-primary"
          @click="activeTab = 'review'"
        >
          <UploadCloud :size="16" />
          发布检查
        </button>
      </template>
    </PageHeader>

    <div v-if="error" class="alert alert-danger" role="alert">
      {{ error }}
    </div>

    <div v-if="loading" class="author-loading">
      <div class="skeleton hero-skeleton" />
      <div class="skeleton content-skeleton" />
    </div>

    <template v-else-if="assignment">
      <section class="workflow-card">
        <div class="workflow-summary">
          <div class="workflow-mark">
            <Sparkles :size="19" />
          </div>
          <div>
            <strong>完整编辑流程</strong>
            <span>按步骤完成内容、题目、规则和发布检查</span>
          </div>
        </div>
        <nav class="workflow-tabs" aria-label="作业编辑步骤">
          <button
            v-for="(tab, index) in tabs"
            :key="tab.id"
            type="button"
            class="workflow-tab"
            :class="{ active: activeTab === tab.id }"
            @click="tab.id === 'questions' ? openQuestionEditor() : (activeTab = tab.id)"
          >
            <span class="workflow-index">
              <component :is="tab.icon" :size="16" />
            </span>
            <span class="workflow-label">{{ tab.label }}</span>
            <ChevronRight
              v-if="index < tabs.length - 1"
              class="workflow-chevron"
              :size="15"
            />
          </button>
        </nav>
      </section>

      <div class="author-stats">
        <StatCard
          label="题目总数"
          :value="questionCount"
          unit="道"
          hint="包含所有题型与选做题"
        />
        <StatCard
          label="总分"
          :value="totalPoints"
          unit="分"
          hint="按题目分值自动汇总"
          tone="success"
        />
        <StatCard
          label="单选题"
          :value="singleChoiceQuestions"
          unit="道"
          hint="由 Java 后端按标准答案判分"
          tone="warning"
        />
        <StatCard
          label="判断题"
          :value="trueFalseQuestions"
          unit="道"
          hint="正确或错误，标准答案确定得分"
          tone="accent"
        />
      </div>

      <div v-if="activeTab === 'overview'" class="author-grid">
        <div class="author-main">
          <SectionCard
            title="作业说明"
            description="学生进入作业后看到的引导信息"
          >
            <template #actions>
              <button
                type="button"
                class="button button-secondary compact-button"
                @click="openLegacy('/config')"
              >
                <FileText :size="15" />
                编辑内容
              </button>
            </template>
            <div class="content-article">
              <p>{{ assignment.introduction || "暂无作业简介。" }}</p>
              <h3>作答说明</h3>
              <p>{{ assignment.instructions || "暂无额外作答说明。" }}</p>
            </div>
          </SectionCard>

          <SectionCard
            title="题目概览"
            description="检查题干、题型、分值与参考答案结构"
          >
            <template #actions>
              <button
                type="button"
                class="button button-secondary compact-button"
                @click="openQuestionEditor"
              >
                查看全部
                <ChevronRight :size="14" />
              </button>
            </template>
            <div v-if="!assignment.questions?.length">
              <EmptyState
                title="还没有题目"
                description="进入完整编辑页后可添加单选、多选、判断、文本作答等题型。"
              />
            </div>
            <div v-else class="question-list">
              <article
                v-for="(question, index) in assignment.questions.slice(0, 4)"
                :key="question.id"
                class="question-row"
              >
                <span class="question-index">{{ index + 1 }}</span>
                <div class="question-copy">
                  <div class="question-meta">
                    <span class="badge badge-primary">
                      {{ typeLabels[question.type] || question.type }}
                    </span>
                    <span>{{ question.totalPoints || 0 }} 分</span>
                  </div>
                  <p><MathText :text="question.question" /></p>
                </div>
                <button
                  type="button"
                  class="icon-button"
                  aria-label="编辑这道题"
                  @click="openQuestionEditor"
                >
                  <ChevronRight :size="17" />
                </button>
              </article>
            </div>
          </SectionCard>
        </div>

        <aside class="author-side">
          <SectionCard title="发布状态" description="当前作业是否可供学生作答">
            <div class="publish-state">
              <span
                class="publish-ring"
                :class="{ published: assignment.published }"
              >
                <Check v-if="assignment.published" :size="24" />
                <CircleHelp v-else :size="24" />
              </span>
              <strong>{{ assignment.published ? "作业已发布" : "仍为草稿" }}</strong>
              <p>
                {{
                  assignment.published
                    ? "学生已经可以从课程平台进入这份作业。"
                    : "完成内容和发布检查后，再正式向学生发布。"
                }}
              </p>
              <button
                type="button"
                class="button button-primary"
                @click="activeTab = 'review'"
              >
                {{ assignment.published ? "查看发布详情" : "开始发布检查" }}
              </button>
            </div>
          </SectionCard>

          <SectionCard title="作业信息" description="时间、次数与显示规则">
            <dl class="detail-list">
              <div>
                <dt><Clock3 :size="14" />建议用时</dt>
                <dd>{{ assignment.timeEstimateMinutes || 1 }} 分钟</dd>
              </div>
              <div>
                <dt><BookOpen :size="14" />作答次数</dt>
                <dd>
                  {{ assignment.numAttempts === -1 ? "不限次数" : `${assignment.numAttempts || 1} 次` }}
                </dd>
              </div>
              <div>
                <dt><CheckCircle2 :size="14" />通过分数</dt>
                <dd>{{ assignment.passingGrade || 60 }}%</dd>
              </div>
              <div>
                <dt><Save :size="14" />最近更新</dt>
                <dd>{{ formatDate(assignment.updatedAt) }}</dd>
              </div>
            </dl>
          </SectionCard>

          <div class="compatibility-card">
            <div class="compatibility-icon"><Settings2 :size="18" /></div>
            <div>
              <strong>高级编辑功能</strong>
              <p>版本历史、文件上传、预览和复杂评分规则暂时保留在旧编辑页。</p>
              <button type="button" @click="openLegacy()">
                打开兼容编辑页
                <ChevronRight :size="14" />
              </button>
            </div>
          </div>
        </aside>
      </div>

      <SectionCard
        v-else-if="activeTab === 'questions'"
        title="全部题目"
        description="题干字段已自动解码，统一使用中文界面展示"
      >
        <EmptyState
          v-if="!assignment.questions?.length"
          title="还没有题目"
          description="请进入完整编辑页添加题目。"
        />
        <div v-else class="question-list full">
          <article
            v-for="(question, index) in assignment.questions"
            :key="question.id"
            class="question-row"
          >
            <span class="question-index">{{ index + 1 }}</span>
            <div class="question-copy">
              <div class="question-meta">
                <span class="badge badge-primary">
                  {{ typeLabels[question.type] || question.type }}
                </span>
                <span>{{ question.totalPoints || 0 }} 分</span>
              </div>
              <p><MathText :text="question.question" /></p>
              <small v-if="question.scoring?.rubrics?.length">
                已配置 {{ question.scoring.rubrics.length }} 组评分标准
              </small>
            </div>
            <button
              type="button"
              class="button button-secondary compact-button"
              @click="openQuestionEditor"
            >
              编辑
            </button>
          </article>
        </div>
      </SectionCard>

      <SectionCard
        v-else-if="activeTab === 'settings'"
        title="评分与规则"
        description="统一展示当前作业规则，复杂设置仍可在完整编辑页调整"
      >
        <div class="settings-grid">
          <div class="setting-block">
            <span class="setting-icon"><Settings2 :size="18" /></span>
            <div><strong>判分方式</strong><p>Java 后端按标准答案判分</p></div>
          </div>
          <div class="setting-block">
            <span class="setting-icon"><Clock3 :size="18" /></span>
            <div><strong>冷却时间</strong><p>{{ assignment.retakeAttemptCoolDownMinutes || 0 }} 分钟</p></div>
          </div>
          <div class="setting-block">
            <span class="setting-icon"><ListChecks :size="18" /></span>
            <div><strong>题目顺序</strong><p>{{ assignment.displayOrder === "RANDOM" ? "随机顺序" : "固定顺序" }}</p></div>
          </div>
          <div class="setting-block">
            <span class="setting-icon"><CheckCircle2 :size="18" /></span>
            <div><strong>通过条件</strong><p>至少达到 {{ assignment.passingGrade || 60 }}%</p></div>
          </div>
        </div>
        <div class="tab-footer">
          <button
            type="button"
            class="button button-primary"
            @click="openLegacy('/config')"
          >
            打开完整规则设置
          </button>
        </div>
      </SectionCard>

      <SectionCard
        v-else
        title="发布检查"
        description="逐项确认内容是否完整，再决定是否向学生开放"
      >
        <div class="review-list">
          <div class="review-item">
            <span class="review-icon success"><Check :size="17" /></span>
            <div><strong>作业基本信息</strong><p>标题与说明已填写。</p></div>
          </div>
          <div class="review-item">
            <span class="review-icon" :class="questionCount ? 'success' : 'warning'">
              <Check v-if="questionCount" :size="17" />
              <CircleHelp v-else :size="17" />
            </span>
            <div>
              <strong>题目完整性</strong>
              <p>{{ questionCount ? `共 ${questionCount} 道题目。` : "尚未添加题目。" }}</p>
            </div>
          </div>
          <div class="review-item">
            <span class="review-icon" :class="questionCount ? 'success' : 'warning'">
              <Check v-if="questionCount" :size="17" />
              <CircleHelp v-else :size="17" />
            </span>
            <div>
              <strong>AI 讲评准备</strong>
              <p>AI 仅分析错误模式和生成讲评草稿，最终对错与得分由系统确定。</p>
            </div>
          </div>
        </div>
        <div class="tab-footer review-actions">
          <button type="button" class="button button-secondary" @click="openLegacy('/review')">
            旧版发布检查
          </button>
          <button type="button" class="button button-primary" @click="openLegacy()">
            打开完整发布流程
          </button>
        </div>
      </SectionCard>
    </template>
  </div>
</template>

<style scoped>
.author-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.author-loading {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.hero-skeleton {
  height: 100px;
}

.content-skeleton {
  height: 420px;
}

.workflow-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface);
  padding: 12px 14px 12px 18px;
  box-shadow: var(--shadow-sm);
}

.workflow-summary {
  display: flex;
  min-width: 190px;
  align-items: center;
  gap: 10px;
}

.workflow-mark {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  place-items: center;
  border-radius: 11px;
  background: var(--primary-soft);
  color: var(--primary);
}

.workflow-summary div:last-child {
  display: flex;
  flex-direction: column;
}

.workflow-summary strong {
  font-size: 12px;
}

.workflow-summary span {
  color: var(--text-muted);
  font-size: 10px;
}

.workflow-tabs {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  justify-content: flex-end;
  gap: 2px;
}

.workflow-tab {
  display: flex;
  min-height: 44px;
  align-items: center;
  gap: 7px;
  border: 0;
  border-radius: 11px;
  background: transparent;
  padding: 7px 9px;
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 650;
  white-space: nowrap;
}

.workflow-tab:hover {
  background: var(--bg);
}

.workflow-tab.active {
  background: var(--primary-soft);
  color: var(--primary-strong);
}

.workflow-index {
  display: grid;
  width: 26px;
  height: 26px;
  place-items: center;
  border-radius: 8px;
  background: var(--surface-soft);
  color: var(--text-muted);
}

.workflow-tab.active .workflow-index {
  background: var(--primary);
  color: white;
}

.workflow-chevron {
  margin-left: 5px;
  color: var(--ink-4);
}

.author-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.author-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 20px;
}

.author-main,
.author-side {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 20px;
}

.compact-button {
  min-height: 36px;
  padding: 7px 11px;
  font-size: 11px;
}

.content-article p {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
}

.content-article h3 {
  margin: 20px 0 7px;
  font-size: 13px;
}

.question-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.question-list.full {
  gap: 12px;
}

.question-row {
  display: flex;
  align-items: center;
  gap: 13px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: #fbfdfc;
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
  font-size: 12px;
  font-weight: 700;
}

.question-copy {
  min-width: 0;
  flex: 1;
}

.question-meta {
  display: flex;
  align-items: center;
  gap: 9px;
  color: var(--text-muted);
  font-size: 10px;
}

.question-copy p {
  display: -webkit-box;
  overflow: hidden;
  margin-top: 6px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.question-copy small {
  display: block;
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 10px;
}

.publish-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 4px 6px;
  text-align: center;
}

.publish-ring {
  display: grid;
  width: 58px;
  height: 58px;
  place-items: center;
  margin-bottom: 13px;
  border: 1px solid #f1e2cc;
  border-radius: 18px;
  background: var(--warning-soft);
  color: var(--warning);
}

.publish-ring.published {
  border-color: #d8eee6;
  background: var(--success-soft);
  color: var(--success);
}

.publish-state strong {
  font-size: 14px;
}

.publish-state p {
  margin: 6px 0 15px;
  color: var(--text-muted);
  font-size: 11px;
  line-height: 1.6;
}

.detail-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
}

.detail-list div {
  display: flex;
  min-height: 42px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid var(--line);
}

.detail-list div:last-child {
  border-bottom: 0;
}

.detail-list dt {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--text-muted);
  font-size: 11px;
}

.detail-list dd {
  margin: 0;
  font-size: 11px;
  font-weight: 650;
}

.compatibility-card {
  display: flex;
  gap: 11px;
  border: 1px solid color-mix(in srgb, var(--role) 18%, var(--line));
  border-radius: var(--radius-lg);
  background: linear-gradient(145deg, var(--role-soft), #fff);
  padding: 16px;
}

.compatibility-icon {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  place-items: center;
  border-radius: 10px;
  background: white;
  color: var(--primary);
}

.compatibility-card strong {
  font-size: 12px;
}

.compatibility-card p {
  margin: 4px 0 9px;
  color: var(--text-secondary);
  font-size: 10px;
  line-height: 1.6;
}

.compatibility-card button {
  display: inline-flex;
  min-height: 34px;
  align-items: center;
  gap: 4px;
  border: 0;
  background: transparent;
  padding: 0;
  color: var(--primary);
  font-size: 11px;
  font-weight: 700;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.setting-block {
  display: flex;
  gap: 12px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--surface-soft);
  padding: 15px;
}

.setting-icon,
.review-icon {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  place-items: center;
  border-radius: 11px;
  background: white;
  color: var(--primary);
}

.setting-block strong,
.review-item strong {
  font-size: 12px;
}

.setting-block p,
.review-item p {
  margin-top: 3px;
  color: var(--text-muted);
  font-size: 11px;
}

.tab-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
  border-top: 1px solid var(--border);
  padding-top: 16px;
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.review-item {
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--surface-soft);
  padding: 14px;
}

.review-icon.success {
  background: var(--success-soft);
  color: var(--success);
}

.review-icon.warning {
  background: var(--warning-soft);
  color: var(--warning);
}

.review-actions {
  gap: 10px;
}

@media (max-width: 1180px) {
  .workflow-card {
    align-items: flex-start;
    flex-direction: column;
  }

  .workflow-tabs {
    width: 100%;
    justify-content: flex-start;
    overflow-x: auto;
    padding-bottom: 2px;
  }

  .author-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .author-side {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .compatibility-card {
    grid-column: 1 / -1;
  }
}

@media (max-width: 820px) {
  .author-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .author-stats,
  .author-side,
  .settings-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .workflow-label {
    display: none;
  }

  .workflow-tab {
    flex: 1;
    justify-content: center;
  }

  .workflow-chevron {
    display: none;
  }
}
</style>
