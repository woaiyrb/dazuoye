<script setup>
import {
  ArrowLeft,
  Check,
  CheckCircle2,
  ChevronDown,
  CircleAlert,
  Import,
  LoaderCircle,
  Sparkles,
} from "@lucide/vue";
import { computed, onMounted, reactive, ref } from "vue";
import { RouterLink, useRoute } from "vue-router";
import PageHeader from "../components/PageHeader.vue";
import MathText from "../components/MathText.vue";
import { getAssignment } from "../services/api";

const route = useRoute();
const assignment = ref(null);
const loadingAssignment = ref(true);
const generating = ref(false);
const importing = ref(false);
const error = ref("");
const notice = ref("");
const generated = ref([]);
const selectedIds = ref([]);

const settings = reactive({
  topic: "",
  difficulty: "medium",
  singleChoiceCount: 2,
  trueFalseCount: 1,
});
const materials = reactive({
  learningObjectives: "",
  materials: "",
});

const totalCount = computed(
  () =>
    Number(settings.singleChoiceCount || 0) +
    Number(settings.trueFalseCount || 0),
);
const selectedQuestions = computed(() =>
  generated.value.filter((question) => selectedIds.value.includes(question.id)),
);
const allSelected = computed(
  () =>
    generated.value.length > 0 &&
    selectedIds.value.length === generated.value.length,
);

const difficultyOptions = [
  { value: "easy", label: "基础" },
  { value: "medium", label: "进阶" },
  { value: "hard", label: "挑战" },
];

async function loadAssignment() {
  loadingAssignment.value = true;
  try {
    assignment.value = await getAssignment(route.params.assignmentId);
    settings.topic = assignment.value?.name || "初中数学基础练习";
  } finally {
    loadingAssignment.value = false;
  }
}

async function generateQuestions() {
  error.value = "";
  notice.value = "";
  if (!settings.topic.trim()) {
    error.value = "请输入出题主题。";
    return;
  }
  if (totalCount.value < 1 || totalCount.value > 50) {
    error.value = "一次生成数量应在 1 到 50 道之间。";
    return;
  }
  generating.value = true;
  try {
    const response = await fetch("/java-api/generator/generate", {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ...settings, ...materials }),
    });
    const body = await response.json().catch(() => null);
    if (!response.ok) {
      throw new Error(body?.message || "批量生成失败");
    }
    generated.value = body.questions || [];
    selectedIds.value = generated.value.map((question) => question.id);
    notice.value = `DeepTutor 已生成 ${body.generatedCount || generated.value.length} 道题，可先检查再导入。`;
  } catch (requestError) {
    error.value = requestError.message || "批量生成失败，请检查 DeepTutor。";
  } finally {
    generating.value = false;
  }
}

function toggleQuestion(id) {
  selectedIds.value = selectedIds.value.includes(id)
    ? selectedIds.value.filter((item) => item !== id)
    : [...selectedIds.value, id];
}

function toggleAll() {
  selectedIds.value = allSelected.value
    ? []
    : generated.value.map((question) => question.id);
}

async function importQuestions() {
  error.value = "";
  notice.value = "";
  if (!selectedQuestions.value.length) {
    error.value = "请至少选择一道题目。";
    return;
  }
  importing.value = true;
  try {
    const response = await fetch(
      `/java-api/generator/assignments/${route.params.assignmentId}/import`,
      {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(selectedQuestions.value),
      },
    );
    const body = await response.json().catch(() => null);
    if (!response.ok) {
      throw new Error(body?.message || "导入题目失败");
    }
    notice.value = `已导入 ${body.importedCount} 道题，可返回作业编辑页继续检查。`;
    selectedIds.value = [];
  } catch (requestError) {
    error.value =
      requestError.message || "题目已生成，但导入作业失败，请稍后重试。";
  } finally {
    importing.value = false;
  }
}

onMounted(loadAssignment);
</script>

<template>
  <div class="page generator-page">
    <PageHeader
      eyebrow="DeepTutor 批量出题"
      title="自动评分题目生成器"
      :description="`为《${assignment?.name || '当前作业'}》批量生成单选题和判断题。AI 生成后仍需教师检查，再导入作业。`"
    >
      <template #actions>
        <RouterLink :to="`/author/${route.params.assignmentId}`" class="button button-secondary">
          <ArrowLeft :size="16" />
          返回作业
        </RouterLink>
      </template>
    </PageHeader>

    <p v-if="error" class="alert alert-danger" role="alert">{{ error }}</p>
    <p v-else-if="notice" class="alert" role="status">{{ notice }}</p>

    <div class="generator-grid">
      <section class="generator-column card">
        <div class="column-header">
          <span class="column-index">1</span>
          <div><h2>出题设置</h2><p>告诉 DeepTutor 要生成什么</p></div>
        </div>
        <div class="column-body settings-body">
          <label>
            <span class="field-label">出题主题</span>
            <input v-model="settings.topic" class="input" placeholder="如：有理数加减法" />
          </label>

          <label>
            <span class="field-label">难度</span>
            <span class="select-wrap">
              <select v-model="settings.difficulty" class="select">
                <option v-for="option in difficultyOptions" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
              <ChevronDown :size="15" />
            </span>
          </label>

          <div class="type-counts">
            <label>
              <span><strong>单选题</strong><small>由标准答案自动判分</small></span>
              <input v-model.number="settings.singleChoiceCount" class="input count-input" type="number" min="0" max="50" />
            </label>
            <label>
              <span><strong>判断题</strong><small>正确或错误</small></span>
              <input v-model.number="settings.trueFalseCount" class="input count-input" type="number" min="0" max="50" />
            </label>
          </div>

          <div class="generation-total">
            <span>本次生成</span>
            <strong>{{ totalCount }} 道题</strong>
          </div>

          <button
            type="button"
            class="button button-primary generate-button"
            :disabled="generating || loadingAssignment"
            @click="generateQuestions"
          >
            <LoaderCircle v-if="generating" class="spinning" :size="17" />
            <Sparkles v-else :size="17" />
            {{ generating ? "DeepTutor 正在出题…" : generated.length ? "重新生成" : "批量生成题目" }}
          </button>
        </div>
      </section>

      <section class="generator-column card">
        <div class="column-header">
          <span class="column-index">2</span>
          <div><h2>教学材料</h2><p>提供目标和参考内容</p></div>
        </div>
        <div class="column-body materials-body">
          <div class="compact-tip">
            <CircleAlert :size="16" />
            <p>只发送教学内容和匿名统计数据，不发送学生姓名、学号。</p>
          </div>
          <label>
            <span class="field-label">学习目标</span>
            <textarea
              v-model="materials.learningObjectives"
              class="textarea"
              placeholder="例如：理解绝对值含义，能正确完成有理数加减法。"
            ></textarea>
          </label>
          <label class="materials-field">
            <span class="field-label">参考材料或教师解析</span>
            <textarea
              v-model="materials.materials"
              class="textarea"
              placeholder="粘贴知识点、教材内容或教师解析；内容越长，生成结果越贴近教学要求。"
            ></textarea>
          </label>
          <div class="generation-status" :class="{ active: generating, complete: generated.length }">
            <span class="status-orb">
              <LoaderCircle v-if="generating" class="spinning" :size="18" />
              <CheckCircle2 v-else-if="generated.length" :size="18" />
              <Sparkles v-else :size="18" />
            </span>
            <div>
              <strong>{{ generating ? "正在调用 DeepTutor deep_question" : generated.length ? "题目草稿已生成" : "等待生成" }}</strong>
              <p>{{ generating ? "批量题目通常需要几十秒，请保持页面打开。" : generated.length ? "请逐题检查题干、选项和答案。" : "填写设置后点击左侧“批量生成题目”。" }}</p>
            </div>
          </div>
        </div>
      </section>

      <section class="generator-column result-column card">
        <div class="column-header">
          <span class="column-index">3</span>
          <div><h2>结果预览</h2><p>选择确认后导入作业</p></div>
          <span v-if="generated.length" class="badge badge-primary">{{ generated.length }} 道</span>
        </div>
        <div class="column-body result-body">
          <div v-if="!generated.length" class="result-empty">
            <span><Sparkles :size="22" /></span>
            <strong>还没有生成题目</strong>
            <p>完成后，题干、选项和标准答案会在这里紧凑展示。</p>
          </div>
          <template v-else>
            <div class="result-toolbar">
              <label><input type="checkbox" :checked="allSelected" @change="toggleAll" />全选</label>
              <span>{{ selectedIds.length }} 道待导入</span>
            </div>
            <div class="result-list">
              <article
                v-for="(question, index) in generated"
                :key="question.id"
                class="generated-question"
                :class="{ selected: selectedIds.includes(question.id) }"
                @click="toggleQuestion(question.id)"
              >
                <div class="generated-heading">
                  <input
                    type="checkbox"
                    :checked="selectedIds.includes(question.id)"
                    :aria-label="`选择第 ${index + 1} 题`"
                    @click.stop
                    @change="toggleQuestion(question.id)"
                  />
                  <span class="badge badge-primary">{{ question.type === "TRUE_FALSE" ? "判断题" : "单选题" }}</span>
                  <small>{{ question.totalPoints }} 分</small>
                </div>
                <p><MathText :text="question.question" /></p>
                <div class="mini-choices">
                  <span v-for="choice in question.choices" :key="choice.id" :class="{ correct: choice.correct }">
                    <MathText :text="choice.label" />
                    <Check v-if="choice.correct" :size="12" />
                  </span>
                </div>
              </article>
            </div>
            <button
              type="button"
              class="button button-primary import-button"
              :disabled="importing || selectedIds.length === 0"
              @click="importQuestions"
            >
              <LoaderCircle v-if="importing" class="spinning" :size="17" />
              <Import v-else :size="17" />
              {{ importing ? "正在导入…" : `导入 ${selectedIds.length} 道题` }}
            </button>
            <RouterLink :to="`/author/${route.params.assignmentId}`" class="back-link">
              返回作业检查题目
            </RouterLink>
          </template>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.generator-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.generator-page :deep(.page-header) {
  margin-bottom: 4px;
}

.generator-grid {
  display: grid;
  min-height: 590px;
  grid-template-columns: minmax(250px, 0.82fr) minmax(300px, 0.95fr) minmax(340px, 1.2fr);
  gap: 14px;
}

.generator-column {
  display: flex;
  min-width: 0;
  flex-direction: column;
  overflow: hidden;
}

.column-header {
  display: flex;
  align-items: center;
  gap: 9px;
  border-bottom: 1px solid var(--border);
  background: #fbfdfc;
  padding: 14px 16px;
}

.column-index {
  display: grid;
  width: 26px;
  height: 26px;
  flex: 0 0 26px;
  place-items: center;
  border-radius: 8px;
  background: var(--primary);
  color: white;
  font-size: 10px;
  font-weight: 700;
}

.column-header div {
  min-width: 0;
  flex: 1;
}

.column-header h2 {
  font-size: 13px;
  font-weight: 720;
}

.column-header p {
  color: var(--text-muted);
  font-size: 10px;
}

.column-body {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 13px;
  padding: 14px 16px 16px;
}

.settings-body label,
.materials-body label {
  display: block;
}

.select-wrap {
  position: relative;
  display: block;
}

.select-wrap svg {
  position: absolute;
  top: 50%;
  right: 12px;
  pointer-events: none;
  color: var(--text-muted);
  transform: translateY(-50%);
}

.select-wrap select {
  appearance: none;
}

.type-counts {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.type-counts label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 9px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--surface-soft);
  padding: 10px;
}

.type-counts label > span {
  display: flex;
  flex-direction: column;
}

.type-counts strong {
  font-size: 11px;
}

.type-counts small {
  color: var(--text-muted);
  font-size: 10px;
}

.count-input {
  width: 60px;
  min-height: 38px;
  flex: 0 0 60px;
  text-align: center;
}

.generation-total {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-radius: 10px;
  background: var(--primary-soft);
  padding: 9px 11px;
  color: var(--text-secondary);
  font-size: 10px;
}

.generation-total strong {
  color: var(--primary);
  font-size: 12px;
}

.generate-button {
  width: 100%;
}

.compact-tip {
  display: flex;
  gap: 8px;
  border: 1px solid #f1e4cf;
  border-radius: 11px;
  background: var(--warning-soft);
  padding: 9px 10px;
  color: var(--warning);
}

.compact-tip p {
  color: var(--text-secondary);
  font-size: 10px;
  line-height: 1.55;
}

.materials-body .textarea {
  min-height: 100px;
  font-size: 11px;
}

.materials-body .materials-field {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
}

.materials-body .materials-field .textarea {
  min-height: 150px;
  flex: 1;
  resize: none;
}

.generation-status {
  display: flex;
  gap: 9px;
  border: 1px dashed var(--border-strong);
  border-radius: 12px;
  padding: 10px;
}

.generation-status.active {
  border-color: color-mix(in srgb, var(--role) 48%, var(--line));
  background: var(--primary-soft);
}

.generation-status.complete {
  border-color: #d8eee6;
  background: var(--success-soft);
}

.status-orb {
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  place-items: center;
  border-radius: 10px;
  background: white;
  color: var(--primary);
}

.generation-status strong {
  font-size: 11px;
}

.generation-status p {
  margin-top: 2px;
  color: var(--text-muted);
  font-size: 10px;
  line-height: 1.5;
}

.result-column .column-header {
  justify-content: space-between;
}

.result-body {
  overflow: hidden;
}

.result-empty {
  display: flex;
  min-height: 260px;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 1px dashed var(--border-strong);
  border-radius: 14px;
  background: var(--surface-soft);
  padding: 20px;
  text-align: center;
}

.result-empty span {
  display: grid;
  width: 44px;
  height: 44px;
  place-items: center;
  margin-bottom: 10px;
  border-radius: 13px;
  background: var(--primary-soft);
  color: var(--primary);
}

.result-empty strong {
  font-size: 11px;
}

.result-empty p {
  max-width: 230px;
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 10px;
  line-height: 1.6;
}

.result-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--text-muted);
  font-size: 10px;
}

.result-toolbar label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--text-secondary);
}

.result-list {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  overflow-y: auto;
  padding-right: 3px;
}

.generated-question {
  border: 1px solid var(--border);
  border-radius: 12px;
  background: #fbfdfc;
  padding: 10px;
  cursor: pointer;
  transition:
    border-color 150ms ease,
    background 150ms ease;
}

.generated-question.selected {
  border-color: color-mix(in srgb, var(--role) 42%, var(--line));
  background: var(--role-wash);
}

.generated-heading {
  display: flex;
  align-items: center;
  gap: 6px;
}

.generated-heading small {
  margin-left: auto;
  color: var(--text-muted);
  font-size: 9px;
}

.generated-question > p {
  margin-top: 7px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.55;
}

.mini-choices {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 7px;
}

.mini-choices span {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border-radius: 6px;
  background: var(--slate-soft);
  padding: 3px 6px;
  color: var(--text-secondary);
  font-size: 9px;
}

.mini-choices span.correct {
  background: var(--success-soft);
  color: var(--success);
}

.import-button {
  width: 100%;
}

.back-link {
  color: var(--primary);
  font-size: 10px;
  font-weight: 650;
  text-align: center;
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
  .generator-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .result-column {
    grid-column: 1 / -1;
    min-height: 420px;
  }
}

@media (max-width: 720px) {
  .generator-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .result-column {
    grid-column: auto;
  }
}
</style>
