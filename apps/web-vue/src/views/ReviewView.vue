<script setup>
import {
  Bot,
  Check,
  CheckCircle2,
  ChevronLeft,
  CircleAlert,
  Eye,
  FileCheck2,
  PenLine,
  Send,
  ShieldCheck,
} from "@lucide/vue";
import { onMounted, ref } from "vue";
import { RouterLink, useRoute } from "vue-router";
import PageHeader from "../components/PageHeader.vue";
import SectionCard from "../components/SectionCard.vue";
import { getAssignment, getAttempts, getUser } from "../services/api";

const route = useRoute();
const assignment = ref(null);
const loading = ref(true);
const saving = ref(false);
const generating = ref(false);
const notice = ref("");
const correctRate = ref(0);
const draft = ref({
  version: 1,
  status: "DRAFT",
  title: "",
  overview: "",
  commonErrors: "",
  possibleReasons: "",
  correctApproach: "",
  knowledgeSummary: "",
  practiceAdvice: "",
  evidence: "",
});

const statusLabels = {
  DRAFT: "AI 草稿",
  REVIEWED: "教师已确认",
  PUBLISHED: "已发布",
  WITHDRAWN: "已撤回",
};

async function load() {
  loading.value = true;
  try {
    const [assignmentData, attempts] = await Promise.all([
      getAssignment(route.params.assignmentId),
      getAttempts(route.params.assignmentId),
    ]);
    assignment.value = assignmentData;
    const grades = attempts
      .map((attempt) => Number(attempt.grade))
      .filter((grade) => Number.isFinite(grade));
    correctRate.value = grades.length
      ? Math.round(
          grades.reduce((total, grade) => total + grade, 0) / grades.length,
        )
      : 0;
    const response = await fetch(
      `/java-api/reviews/${route.params.assignmentId}`,
      { credentials: "include" },
    );
    if (response.ok) {
      draft.value = await response.json();
    } else {
      const user = await getUser().catch(() => null);
      draft.value = {
        ...draft.value,
        title: `${assignment.value?.name || "本次作业"}讲评`,
        overview: `本次作业共有 ${assignment.value?.questions?.length || 0} 道题，重点复习选择题和判断题涉及的基础知识。`,
        commonErrors: "根据统计数据，集中讲解错误率较高的题目。",
        possibleReasons: "可能原因：概念混淆、计算符号错误或审题不完整。",
        correctApproach: "先明确题目条件，再根据标准答案和教师解析逐步核对。",
        knowledgeSummary: "有理数加法、绝对值与一元一次方程。",
        practiceAdvice: "完成同类基础练习，并说明每一题的判断依据。",
        evidence: `作业：${assignment.value?.name || ""}；数据来源：标准答案与提交统计；操作人：${user?.userId || "教师"}。`,
      };
    }
  } finally {
    loading.value = false;
  }
}

async function saveDraft(status = "DRAFT") {
  saving.value = true;
  notice.value = "";
  try {
    const response = await fetch(
      `/java-api/reviews/${route.params.assignmentId}`,
      {
        method: "PUT",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...draft.value, status }),
      },
    );
    if (!response.ok) throw new Error("保存讲评失败");
    draft.value = await response.json();
    notice.value =
      status === "REVIEWED"
        ? "教师已确认，下一步可以发布。"
        : status === "PUBLISHED"
          ? "讲评已发布，学生端现在可以查看。"
          : "草稿已保存。";
  } catch (error) {
    notice.value = error.message || "保存失败，请检查 Java 后端。";
  } finally {
    saving.value = false;
  }
}

async function generateDraft() {
  generating.value = true;
  notice.value = "";
  try {
    const response = await fetch(
      `/java-api/reviews/${route.params.assignmentId}/generate`,
      {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          assignmentName: assignment.value?.name || "当前数学作业",
          questionCount: assignment.value?.questions?.length || 0,
          correctRate: correctRate.value,
          commonWrongQuestions: "请结合批改统计确认高频错误。",
          teacherMaterials: "教师解析与知识点说明",
        }),
      },
    );
    if (!response.ok) {
      const body = await response.json().catch(() => null);
      throw new Error(body?.message || "AI 讲评生成失败");
    }
    draft.value = await response.json();
    notice.value = "AI 草稿已生成，请教师核对后再确认发布。";
  } catch (error) {
    notice.value = `${error.message || "AI 生成失败"} 可继续手工填写并完成审核。`;
  } finally {
    generating.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeader
      eyebrow="教师审核"
      title="AI 讲评草稿与发布"
      :description="`${assignment?.name || '当前作业'} · AI 内容不会自动发布，必须经教师修改、确认后才能向学生展示。`"
    >
      <template #actions>
        <RouterLink :to="`/grading/${route.params.assignmentId}`" class="button button-secondary">
          <ChevronLeft :size="16" />
          返回批改统计
        </RouterLink>
        <button
          type="button"
          class="button button-primary"
          :disabled="saving || draft.status !== 'DRAFT'"
          @click="saveDraft('REVIEWED')"
        >
          <ShieldCheck :size="16" />
          {{
            draft.status === "DRAFT"
              ? "保存并确认"
              : draft.status === "REVIEWED"
                ? "教师已确认"
                : "讲评已发布"
          }}
        </button>
      </template>
    </PageHeader>

    <p v-if="notice" class="alert" role="status">{{ notice }}</p>

    <div class="review-grid">
      <div class="review-main">
        <SectionCard title="讲评基本信息" description="教师可以修改标题和整体总结">
          <div class="form-stack">
            <label><span class="field-label">讲评标题</span><input v-model="draft.title" class="input" /></label>
            <label><span class="field-label">整体情况</span><textarea v-model="draft.overview" class="textarea" /></label>
          </div>
        </SectionCard>

        <SectionCard title="错误分析与讲评" description="AI 只能基于题目、答案、教师材料和统计数据给出可能原因">
          <div class="form-grid">
            <label><span class="field-label">高频错误</span><textarea v-model="draft.commonErrors" class="textarea" /></label>
            <label><span class="field-label">可能原因</span><textarea v-model="draft.possibleReasons" class="textarea" /></label>
            <label><span class="field-label">正确思路</span><textarea v-model="draft.correctApproach" class="textarea" /></label>
            <label><span class="field-label">知识点总结</span><textarea v-model="draft.knowledgeSummary" class="textarea" /></label>
          </div>
        </SectionCard>

        <SectionCard title="学习与练习建议" description="练习建议与自动出题保持区分">
          <label><span class="field-label">练习建议</span><textarea v-model="draft.practiceAdvice" class="textarea" /></label>
        </SectionCard>

        <SectionCard title="生成依据" description="教师可检查 AI 使用了哪些材料">
          <label><span class="field-label">依据摘要</span><textarea v-model="draft.evidence" class="textarea evidence" /></label>
        </SectionCard>
      </div>

      <aside class="review-side">
        <SectionCard title="发布流程" description="状态变化会记录版本">
          <button
            type="button"
            class="button button-secondary full-width generate-button"
            :disabled="generating"
            @click="generateDraft"
          >
            <Bot :size="16" />
            {{ generating ? "正在生成…" : "生成 AI 草稿" }}
          </button>
          <ol class="workflow-list">
            <li class="done"><span><Bot :size="16" /></span><div><strong>AI 生成草稿</strong><small>不覆盖教师正式版本</small></div><Check :size="16" /></li>
            <li :class="{ done: draft.status === 'REVIEWED' || draft.status === 'PUBLISHED' }"><span><PenLine :size="16" /></span><div><strong>教师审核修改</strong><small>确认事实和措辞</small></div><CheckCircle2 v-if="draft.status !== 'DRAFT'" :size="16" /></li>
            <li :class="{ done: draft.status === 'PUBLISHED' }"><span><Send :size="16" /></span><div><strong>发布给学生</strong><small>学生查看后进入订正</small></div><CheckCircle2 v-if="draft.status === 'PUBLISHED'" :size="16" /></li>
          </ol>
          <div class="version-row"><span>当前状态</span><strong class="badge badge-primary">{{ statusLabels[draft.status] || draft.status }}</strong></div>
          <div class="version-row"><span>版本</span><strong>V{{ draft.version }}</strong></div>
          <button
            type="button"
            class="button button-primary full-width"
            :disabled="saving || draft.status === 'PUBLISHED'"
            @click="saveDraft('DRAFT')"
          >
            <FileCheck2 :size="16" />
            保存 AI 草稿
          </button>
          <button
            type="button"
            class="button button-primary full-width publish-button"
            :disabled="saving || draft.status !== 'REVIEWED'"
            @click="saveDraft('PUBLISHED')"
          >
            <Eye :size="16" />
            {{ draft.status === "PUBLISHED" ? "讲评已发布" : "发布教师确认版" }}
          </button>
        </SectionCard>

        <div class="rule-card">
          <span><CircleAlert :size="18" /></span>
          <div><strong>AI 边界</strong><p>可能原因必须明确标注为推测，不能当作确定事实；最终对错和得分始终由系统标准答案决定。</p></div>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.review-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 20px;
}

.review-main,
.review-side {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 20px;
}

.form-stack,
.form-grid {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.textarea {
  min-height: 110px;
  line-height: 1.7;
}

.evidence {
  min-height: 92px;
  background: var(--surface-soft);
}

.workflow-list {
  display: flex;
  flex-direction: column;
  gap: 0;
  margin: 0 0 18px;
  padding: 0;
  list-style: none;
}

.workflow-list li {
  position: relative;
  display: grid;
  min-height: 62px;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  color: var(--text-muted);
}

.workflow-list li::after {
  position: absolute;
  top: 48px;
  bottom: -5px;
  left: 16px;
  width: 1px;
  background: var(--border);
  content: "";
}

.workflow-list li:last-child::after {
  display: none;
}

.workflow-list li > span {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 10px;
  background: var(--surface-soft);
}

.workflow-list li.done {
  color: var(--success);
}

.workflow-list li.done > span {
  background: var(--success-soft);
}

.workflow-list div {
  display: flex;
  flex-direction: column;
}

.workflow-list strong {
  color: var(--text);
  font-size: 11px;
}

.workflow-list small {
  color: var(--text-muted);
  font-size: 9px;
}

.version-row {
  display: flex;
  min-height: 40px;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid var(--border);
  color: var(--text-muted);
  font-size: 11px;
}

.version-row strong {
  color: var(--text);
}

.full-width {
  width: 100%;
  margin-top: 10px;
}

.generate-button {
  margin: 0 0 16px;
}

.publish-button {
  background: var(--success);
}

.rule-card {
  display: flex;
  gap: 10px;
  border: 1px solid #f1e4cf;
  border-radius: var(--radius-lg);
  background: var(--warning-soft);
  padding: 15px;
  color: var(--warning);
}

.rule-card strong {
  color: var(--text);
  font-size: 11px;
}

.rule-card p {
  margin-top: 3px;
  color: var(--text-secondary);
  font-size: 10px;
  line-height: 1.7;
}

@media (max-width: 1080px) {
  .review-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 720px) {
  .form-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
