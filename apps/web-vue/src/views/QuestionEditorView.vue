<script setup>
import {
  ArrowLeft,
  Check,
  ChevronDown,
  ClipboardList,
  Copy,
  FileQuestion,
  GripVertical,
  Plus,
  Save,
  Sparkles,
  Trash2,
} from "@lucide/vue";
import { computed, onMounted, reactive, ref } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import PageHeader from "../components/PageHeader.vue";
import MathText from "../components/MathText.vue";

const route = useRoute();
const router = useRouter();
const assignment = ref(null);
const loading = ref(true);
const saving = ref(false);
const error = ref("");
const notice = ref("");
const selectedId = ref(null);
const draft = reactive(emptyDraft());

const selectedQuestion = computed(() =>
  assignment.value?.questions?.find((question) => question.id === selectedId.value),
);
const questionCount = computed(() => assignment.value?.questions?.length || 0);
const totalPoints = computed(() =>
  (assignment.value?.questions || []).reduce(
    (sum, question) => sum + Number(question.totalPoints || 0),
    0,
  ),
);

function emptyDraft() {
  return {
    id: null,
    type: "SINGLE_CORRECT",
    question: "",
    totalPoints: 1,
    authorComment: "",
    randomizedChoices: false,
    choices: [
      { id: 1, label: "", correct: true, points: 1, feedback: "" },
      { id: 2, label: "", correct: false, points: 0, feedback: "" },
    ],
  };
}

function copyQuestion(question) {
  Object.assign(draft, {
    id: question.id,
    type: question.type,
    question: question.question,
    totalPoints: question.totalPoints,
    authorComment: question.authorComment || "",
    randomizedChoices: question.randomizedChoices,
    choices: question.choices.map((choice) => ({ ...choice })),
  });
  selectedId.value = question.id;
}

async function loadEditor() {
  loading.value = true;
  error.value = "";
  try {
    const response = await fetch(
      `/java-api/editor/assignments/${route.params.assignmentId}`,
      { credentials: "include" },
    );
    const body = await response.json().catch(() => null);
    if (!response.ok) throw new Error(body?.message || "题目加载失败");
    assignment.value = body;
    if (body.questions?.length) copyQuestion(body.questions[0]);
  } catch (requestError) {
    error.value = requestError.message || "题目加载失败。";
  } finally {
    loading.value = false;
  }
}

function addQuestion(type = "SINGLE_CORRECT") {
  Object.assign(draft, emptyDraft(), { type });
  if (type === "TRUE_FALSE") {
    draft.question = "";
    draft.choices = [
      { id: 1, label: "正确", correct: true, points: 1, feedback: "" },
      { id: 2, label: "错误", correct: false, points: 0, feedback: "" },
    ];
  }
  selectedId.value = null;
  error.value = "";
  notice.value = "正在创建新题目";
}

function changeType(type) {
  draft.type = type;
  if (type === "TRUE_FALSE") {
    draft.choices = [
      { id: 1, label: "正确", correct: true, points: draft.totalPoints, feedback: "" },
      { id: 2, label: "错误", correct: false, points: 0, feedback: "" },
    ];
  } else if (draft.choices.length < 2) {
    draft.choices = emptyDraft().choices;
  }
}

function addChoice() {
  draft.choices.push({
    id: draft.choices.length + 1,
    label: "",
    correct: false,
    points: 0,
    feedback: "",
  });
}

function removeChoice(index) {
  if (draft.choices.length <= 2) return;
  const removed = draft.choices.splice(index, 1)[0];
  if (removed.correct && draft.choices[0]) draft.choices[0].correct = true;
  draft.choices.forEach((choice, choiceIndex) => {
    choice.id = choiceIndex + 1;
    choice.points = choice.correct ? draft.totalPoints : 0;
  });
}

function selectCorrect(index) {
  draft.choices.forEach((choice, choiceIndex) => {
    choice.correct = choiceIndex === index;
    choice.points = choice.correct ? draft.totalPoints : 0;
  });
}

function duplicateQuestion() {
  if (!selectedQuestion.value) return;
  addQuestion(selectedQuestion.value.type);
  draft.question = selectedQuestion.value.question;
  draft.totalPoints = selectedQuestion.value.totalPoints;
  draft.authorComment = selectedQuestion.value.authorComment;
  draft.choices = selectedQuestion.value.choices.map((choice, index) => ({
    ...choice,
    id: index + 1,
  }));
  notice.value = "已基于当前题目创建副本，保存后生效";
}

async function saveQuestion() {
  error.value = "";
  notice.value = "";
  if (!draft.question.trim()) {
    error.value = "请输入题干。";
    return;
  }
  if (!draft.choices.some((choice) => choice.correct)) {
    error.value = "请设置一个正确答案。";
    return;
  }
  saving.value = true;
  try {
    const isNew = draft.id === null;
    const url = isNew
      ? `/java-api/editor/assignments/${route.params.assignmentId}/questions`
      : `/java-api/editor/assignments/${route.params.assignmentId}/questions/${draft.id}`;
    const response = await fetch(url, {
      method: isNew ? "POST" : "PUT",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        type: draft.type,
        question: draft.question,
        totalPoints: draft.totalPoints,
        authorComment: draft.authorComment,
        randomizedChoices: draft.randomizedChoices,
        choices: draft.choices,
      }),
    });
    const body = await response.json().catch(() => null);
    if (!response.ok) throw new Error(body?.message || "保存题目失败");
    if (isNew) {
      assignment.value.questions.push(body);
    } else {
      const index = assignment.value.questions.findIndex(
        (question) => question.id === body.id,
      );
      if (index >= 0) assignment.value.questions[index] = body;
    }
    copyQuestion(body);
    notice.value = "题目已保存到数据库。";
  } catch (requestError) {
    error.value = requestError.message || "保存题目失败。";
  } finally {
    saving.value = false;
  }
}

async function deleteQuestion() {
  if (draft.id === null) {
    addQuestion();
    return;
  }
  if (!window.confirm("确认删除这道题吗？")) return;
  try {
    const response = await fetch(
      `/java-api/editor/assignments/${route.params.assignmentId}/questions/${draft.id}`,
      { method: "DELETE", credentials: "include" },
    );
    if (!response.ok) throw new Error("删除题目失败");
    assignment.value.questions = assignment.value.questions.filter(
      (question) => question.id !== draft.id,
    );
    if (assignment.value.questions.length) {
      copyQuestion(assignment.value.questions[0]);
    } else {
      addQuestion();
    }
    notice.value = "题目已删除。";
  } catch (requestError) {
    error.value = requestError.message || "删除题目失败。";
  }
}

onMounted(loadEditor);
</script>

<template>
  <div class="page editor-page">
    <PageHeader
      eyebrow="题目编辑器"
      :title="assignment?.name || '题目设计'"
      description="紧凑三列编辑：左侧题目列表、中间题目内容、右侧设置与批量工具。"
    >
      <template #actions>
        <RouterLink :to="`/author/${route.params.assignmentId}`" class="button button-secondary">
          <ArrowLeft :size="16" />
          返回作业
        </RouterLink>
        <RouterLink :to="`/author/${route.params.assignmentId}/generate`" class="button button-secondary">
          <Sparkles :size="16" />
          批量生成
        </RouterLink>
        <button type="button" class="button button-primary" :disabled="saving" @click="saveQuestion">
          <Save :size="16" />
          {{ saving ? "保存中…" : "保存题目" }}
        </button>
      </template>
    </PageHeader>

    <p v-if="error" class="alert alert-danger" role="alert">{{ error }}</p>
    <p v-else-if="notice" class="alert" role="status">{{ notice }}</p>

    <div v-if="loading" class="editor-loading">
      <div class="skeleton" v-for="index in 3" :key="index" />
    </div>

    <div v-else class="question-editor-grid">
      <aside class="question-nav card">
        <div class="panel-heading">
          <div><h2>题目列表</h2><span>{{ questionCount }} 道 · {{ totalPoints }} 分</span></div>
          <button type="button" class="icon-button" aria-label="新建单选题" @click="addQuestion()">
            <Plus :size="18" />
          </button>
        </div>
        <div class="question-list">
          <button
            v-for="(question, index) in assignment?.questions"
            :key="question.id"
            type="button"
            class="question-list-item"
            :class="{ active: selectedId === question.id }"
            @click="copyQuestion(question)"
          >
            <GripVertical class="drag-icon" :size="14" />
            <span class="list-index">{{ index + 1 }}</span>
            <span class="list-copy">
              <strong><MathText :text="question.question" /></strong>
              <small>{{ question.type === "TRUE_FALSE" ? "判断题" : "单选题" }} · {{ question.totalPoints }} 分</small>
            </span>
          </button>
          <button v-if="!assignment?.questions?.length" type="button" class="empty-list" @click="addQuestion()">
            <FileQuestion :size="20" />
            添加第一道题
          </button>
        </div>
        <div class="new-question-actions">
          <button type="button" @click="addQuestion('SINGLE_CORRECT')"><ClipboardList :size="15" />新增单选题</button>
          <button type="button" @click="addQuestion('TRUE_FALSE')"><Check :size="15" />新增判断题</button>
        </div>
      </aside>

      <main class="question-main card">
        <div class="editor-toolbar">
          <div class="type-control">
            <span>题型</span>
            <select :value="draft.type" @change="changeType($event.target.value)">
              <option value="SINGLE_CORRECT">单选题</option>
              <option value="TRUE_FALSE">判断题</option>
            </select>
            <ChevronDown :size="14" />
          </div>
          <div class="toolbar-actions">
            <button type="button" class="icon-button" title="复制题目" @click="duplicateQuestion"><Copy :size="17" /></button>
            <button type="button" class="icon-button danger" title="删除题目" @click="deleteQuestion"><Trash2 :size="17" /></button>
          </div>
        </div>

        <div class="editor-fields">
          <label>
            <span class="field-label">题干</span>
            <textarea v-model="draft.question" class="textarea question-input" placeholder="输入题目内容，数学公式可使用 $...$" />
          </label>
          <div class="compact-fields">
            <label>
              <span class="field-label">分值</span>
              <input v-model.number="draft.totalPoints" class="input" type="number" min="1" max="100" />
            </label>
            <label>
              <span class="field-label">作者评论（学生不可见）</span>
              <input v-model="draft.authorComment" class="input" placeholder="填写判分依据或出题说明" />
            </label>
          </div>

          <div class="options-table">
            <div class="options-header">
              <span>正确答案</span>
              <span>选项内容</span>
              <span>反馈</span>
              <span></span>
            </div>
            <div v-for="(choice, index) in draft.choices" :key="index" class="option-row">
              <label class="correct-radio">
                <input
                  type="radio"
                  :checked="choice.correct"
                  :name="`correct-${draft.id || 'new'}`"
                  @change="selectCorrect(index)"
                />
                <span></span>
              </label>
              <div class="choice-input-wrap">
                <span>{{ draft.type === "TRUE_FALSE" ? (choice.label === "正确" ? "对" : "错") : String.fromCharCode(65 + index) }}</span>
                <input
                  v-model="choice.label"
                  class="input"
                  :disabled="draft.type === 'TRUE_FALSE'"
                  placeholder="选项内容"
                />
              </div>
              <input v-model="choice.feedback" class="input feedback-input" placeholder="可选反馈" />
              <button
                type="button"
                class="icon-button danger"
                :disabled="draft.type === 'TRUE_FALSE' || draft.choices.length <= 2"
                @click="removeChoice(index)"
              >
                <Trash2 :size="15" />
              </button>
            </div>
            <button
              v-if="draft.type === 'SINGLE_CORRECT'"
              type="button"
              class="add-option"
              @click="addChoice"
            >
              <Plus :size="14" />添加选项
            </button>
          </div>
        </div>
      </main>

      <aside class="editor-side">
        <section class="card side-card">
          <h2>题目设置</h2>
          <label class="switch-row">
            <span><strong>随机排列选项</strong><small>每次答题顺序可不同</small></span>
            <input v-model="draft.randomizedChoices" type="checkbox" />
          </label>
          <div class="settings-summary">
            <span>当前题型</span><strong>{{ draft.type === "TRUE_FALSE" ? "判断题" : "单选题" }}</strong>
          </div>
          <div class="settings-summary">
            <span>正确选项</span><strong>{{ draft.choices.find((choice) => choice.correct)?.label || "未设置" }}</strong>
          </div>
        </section>

        <section class="card side-card">
          <h2>批量工具</h2>
          <button type="button" class="side-action" @click="router.push(`/author/${route.params.assignmentId}/generate`)">
            <span><Sparkles :size="16" /></span>
            <div><strong>DeepTutor 批量生成</strong><small>三列紧凑生成单选题和判断题</small></div>
          </button>
          <button type="button" class="side-action" @click="duplicateQuestion">
            <span><Copy :size="16" /></span>
            <div><strong>复制当前题目</strong><small>快速创建相似题</small></div>
          </button>
        </section>

        <button type="button" class="button button-primary side-save" :disabled="saving" @click="saveQuestion">
          <Save :size="16" />
          {{ saving ? "正在保存…" : "保存到数据库" }}
        </button>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.editor-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.editor-page :deep(.page-header) {
  margin-bottom: 2px;
}

.editor-loading {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr) 280px;
  gap: 14px;
}

.editor-loading .skeleton {
  height: 580px;
}

.question-editor-grid {
  display: grid;
  min-height: 620px;
  grid-template-columns: minmax(220px, 0.72fr) minmax(420px, 1.65fr) minmax(250px, 0.78fr);
  align-items: start;
  gap: 14px;
}

.question-nav,
.question-main {
  min-width: 0;
}

.question-nav {
  position: sticky;
  top: 86px;
  display: flex;
  max-height: calc(100dvh - 116px);
  flex-direction: column;
  overflow: hidden;
}

.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border);
  padding: 13px;
}

.panel-heading > div {
  display: flex;
  flex-direction: column;
}

.panel-heading h2,
.side-card h2 {
  font-size: 12px;
}

.panel-heading span {
  color: var(--text-muted);
  font-size: 9px;
}

.question-list {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
  padding: 8px;
}

.question-list-item {
  display: flex;
  min-height: 50px;
  align-items: center;
  gap: 6px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  padding: 7px 8px;
  color: var(--text-secondary);
  text-align: left;
}

.question-list-item:hover {
  background: var(--bg);
}

.question-list-item.active {
  border-color: color-mix(in srgb, var(--role) 20%, var(--line));
  background: var(--primary-soft);
}

.drag-icon {
  flex: 0 0 14px;
  color: var(--ink-4);
}

.list-index {
  display: grid;
  width: 25px;
  height: 25px;
  flex: 0 0 25px;
  place-items: center;
  border-radius: 7px;
  background: white;
  color: var(--primary);
  font-size: 9px;
  font-weight: 700;
}

.list-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.list-copy strong {
  overflow: hidden;
  font-size: 10px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.list-copy small {
  color: var(--text-muted);
  font-size: 8px;
}

.empty-list {
  display: flex;
  min-height: 150px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 7px;
  border: 1px dashed var(--border-strong);
  border-radius: 12px;
  background: var(--surface-soft);
  color: var(--text-muted);
  font-size: 10px;
}

.new-question-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  border-top: 1px solid var(--border);
  padding: 9px;
}

.new-question-actions button {
  display: flex;
  min-height: 36px;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: white;
  color: var(--text-secondary);
  font-size: 9px;
}

.new-question-actions button:hover {
  border-color: color-mix(in srgb, var(--role) 42%, var(--line));
  color: var(--primary);
}

.question-main {
  min-height: 620px;
}

.editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border);
  padding: 10px 13px;
}

.type-control {
  position: relative;
  display: flex;
  align-items: center;
  gap: 7px;
}

.type-control > span {
  color: var(--text-muted);
  font-size: 9px;
}

.type-control select {
  min-height: 34px;
  appearance: none;
  border: 1px solid var(--border);
  border-radius: 9px;
  background: white;
  padding: 6px 28px 6px 10px;
  color: var(--text);
  font-size: 10px;
  font-weight: 650;
}

.type-control svg {
  position: absolute;
  right: 8px;
  pointer-events: none;
  color: var(--text-muted);
}

.toolbar-actions {
  display: flex;
  gap: 3px;
}

.toolbar-actions .icon-button,
.option-row .icon-button {
  width: 34px;
  height: 34px;
}

.danger:hover {
  background: var(--danger-soft);
  color: var(--danger);
}

.editor-fields {
  display: flex;
  flex-direction: column;
  gap: 13px;
  padding: 15px;
}

.question-input {
  min-height: 105px;
  font-size: 12px;
  line-height: 1.7;
  resize: vertical;
}

.compact-fields {
  display: grid;
  grid-template-columns: 90px minmax(0, 1fr);
  gap: 9px;
}

.compact-fields .input {
  min-height: 38px;
  font-size: 10px;
}

.options-table {
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: 12px;
}

.options-header,
.option-row {
  display: grid;
  grid-template-columns: 44px minmax(170px, 1.2fr) minmax(130px, 0.8fr) 36px;
  align-items: center;
  gap: 6px;
}

.options-header {
  min-height: 34px;
  background: var(--surface-soft);
  padding: 0 8px;
  color: var(--text-muted);
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.option-row {
  border-top: 1px solid var(--line);
  padding: 7px 8px;
}

.correct-radio {
  display: flex;
  align-items: center;
  justify-content: center;
}

.correct-radio input {
  position: absolute;
  opacity: 0;
}

.correct-radio span {
  display: block;
  width: 17px;
  height: 17px;
  border: 1.5px solid var(--ink-4);
  border-radius: 50%;
}

.correct-radio input:checked + span {
  border: 5px solid var(--primary);
}

.choice-input-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
}

.choice-input-wrap > span {
  display: grid;
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
  place-items: center;
  border-radius: 7px;
  background: var(--surface-soft);
  color: var(--text-muted);
  font-size: 9px;
  font-weight: 700;
}

.option-row .input {
  min-height: 34px;
  padding: 6px 9px;
  font-size: 10px;
}

.feedback-input {
  color: var(--text-secondary);
}

.add-option {
  display: flex;
  min-height: 34px;
  align-items: center;
  gap: 4px;
  border: 0;
  border-top: 1px dashed var(--border);
  background: #fbfdfc;
  padding: 6px 50px;
  color: var(--primary);
  font-size: 9px;
}

.editor-side {
  position: sticky;
  top: 86px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.side-card {
  padding: 13px;
}

.side-card h2 {
  margin-bottom: 10px;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border-bottom: 1px solid var(--border);
  padding-bottom: 11px;
}

.switch-row > span {
  display: flex;
  flex-direction: column;
}

.switch-row strong {
  font-size: 10px;
}

.switch-row small {
  color: var(--text-muted);
  font-size: 8px;
}

.switch-row input {
  width: 32px;
  accent-color: var(--primary);
}

.settings-summary {
  display: flex;
  min-height: 34px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  border-bottom: 1px solid var(--line);
  color: var(--text-muted);
  font-size: 9px;
}

.settings-summary:last-child {
  border-bottom: 0;
}

.settings-summary strong {
  max-width: 120px;
  overflow: hidden;
  color: var(--text);
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side-action {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 8px;
  border: 0;
  border-radius: 9px;
  background: transparent;
  padding: 8px 4px;
  text-align: left;
}

.side-action:hover {
  background: var(--surface-soft);
}

.side-action > span {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  place-items: center;
  border-radius: 9px;
  background: var(--primary-soft);
  color: var(--primary);
}

.side-action div {
  display: flex;
  flex-direction: column;
}

.side-action strong {
  font-size: 9px;
}

.side-action small {
  color: var(--text-muted);
  font-size: 8px;
}

.side-save {
  width: 100%;
}

@media (max-width: 1180px) {
  .question-editor-grid {
    grid-template-columns: 220px minmax(0, 1fr);
  }

  .editor-side {
    position: static;
    grid-column: 1 / -1;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .side-save {
    align-self: end;
  }
}

@media (max-width: 760px) {
  .question-editor-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .question-nav,
  .editor-side {
    position: static;
  }

  .question-nav {
    max-height: 300px;
  }

  .editor-side {
    grid-template-columns: minmax(0, 1fr);
  }

  .options-header {
    display: none;
  }

  .option-row {
    grid-template-columns: 34px minmax(0, 1fr) 34px;
  }

  .feedback-input {
    grid-column: 2 / -1;
  }
}
</style>
