<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { teacherApi, type QuestionPayload } from '@/api/teacher'
import { errorMessage } from '@/api/errors'
import type {
  AiQuestionDraft,
  HomeworkDetail,
  KnowledgePoint,
  Question,
  QuestionOption,
  QuestionType,
} from '@/types/models'
import { useTeacherWorkspace } from '@/stores/teacherWorkspace'
import { useUiStore } from '@/stores/ui'
import { formatDateTime, toDateInput, toIsoDate } from '@/utils/format'
import { QUESTION_TYPE } from '@/types/labels'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import AppIcon from '@/components/AppIcon.vue'
import AppEmpty from '@/components/AppEmpty.vue'
import AppModal from '@/components/AppModal.vue'

const route = useRoute()
const router = useRouter()
const workspace = useTeacherWorkspace()
const ui = useUiStore()

const homework = ref<HomeworkDetail | null>(null)
const knowledgePoints = ref<KnowledgePoint[]>([])
const loading = ref(true)
const loadError = ref('')

const questionModalOpen = ref(false)
const editingQuestionId = ref<number | null>(null)
const questionSaving = ref(false)
const questionError = ref('')
const inlineAiGenerating = ref(false)
const inlineAiError = ref('')
const inlineAiNotice = ref('')
const inlineAiTopic = ref('')
const activeAiDraftId = ref<number | null>(null)

const aiModalOpen = ref(false)
const aiGenerating = ref(false)
const aiImporting = ref(false)
const aiError = ref('')
const aiWarnings = ref<string[]>([])
const aiDrafts = ref<AiQuestionDraft[]>([])
const aiSelectedIds = ref<number[]>([])
const aiForm = reactive({
  topic: '',
  difficulty: 'medium' as 'easy' | 'medium' | 'hard',
  singleChoiceCount: 3,
  trueFalseCount: 1,
  learningObjectives: '',
  materials: '',
  knowledgePointIds: [] as number[],
})

const metaModalOpen = ref(false)
const metaSaving = ref(false)
const metaError = ref('')
const metaForm = reactive({ title: '', dueDate: '', description: '' })

const questionForm = reactive({
  seqNo: 1,
  questionType: 'SINGLE_CHOICE' as QuestionType,
  stem: '',
  options: [] as QuestionOption[],
  standardAnswer: 'A',
  teacherAnalysis: '',
  score: 5,
  knowledgePointIds: [] as number[],
})

const homeworkId = computed(() => Number(route.params.id))
const skillPoints = computed(() => {
  const secondLevel = knowledgePoints.value.filter((kp) => kp.kpLevel === 2)
  return secondLevel.length ? secondLevel : knowledgePoints.value
})
const aiKnowledgePoints = computed(() => skillPoints.value)
const aiSelectedAll = computed(
  () =>
    aiDrafts.value.length > 0 &&
    aiDrafts.value.every((draft) => aiSelectedIds.value.includes(draft.id)),
)
const kpMap = computed(() => new Map(knowledgePoints.value.map((kp) => [kp.id, kp.name])))
const totalScore = computed(() =>
  (homework.value?.questions ?? []).reduce((sum, question) => sum + question.score, 0),
)
const evidenceCoverage = computed(() => {
  const questions = homework.value?.questions ?? []
  if (!questions.length) return 0
  const complete = questions.filter(
    (question) => Boolean(question.standardAnswer) && Boolean(question.teacherAnalysis),
  ).length
  return Math.round((complete / questions.length) * 100)
})
const canEdit = computed(() => homework.value?.status !== 'ARCHIVED')
const canDeleteQuestions = computed(
  () => homework.value?.status === 'DRAFT' && !homework.value?.currentGradingBatchId,
)
const sortedQuestions = computed(() =>
  [...(homework.value?.questions ?? [])].sort((a, b) => a.seqNo - b.seqNo),
)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const [detail, kpList] = await Promise.all([
      teacherApi.getHomework(homeworkId.value),
      teacherApi.getKnowledgePoints(),
    ])
    homework.value = detail
    knowledgePoints.value = kpList
    workspace.selectHomework(detail.id)
    workspace.loaded = false
    await workspace.load(true)
  } catch (cause) {
    loadError.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

function defaultOptions(): QuestionOption[] {
  return ['A', 'B', 'C', 'D'].map((key) => ({ key, text: '' }))
}

function nextSeqNo(): number {
  const questions = homework.value?.questions ?? []
  return questions.length ? Math.max(...questions.map((question) => question.seqNo)) + 1 : 1
}

function openCreateQuestion(): void {
  editingQuestionId.value = null
  questionError.value = ''
  questionForm.seqNo = nextSeqNo()
  questionForm.questionType = 'SINGLE_CHOICE'
  questionForm.stem = ''
  questionForm.options = defaultOptions()
  questionForm.standardAnswer = 'A'
  questionForm.teacherAnalysis = ''
  questionForm.score = 5
  questionForm.knowledgePointIds = skillPoints.value[0] ? [skillPoints.value[0].id] : []
  inlineAiError.value = ''
  inlineAiNotice.value = ''
  inlineAiTopic.value = homework.value?.title ?? ''
  activeAiDraftId.value = null
  questionModalOpen.value = true
}

function openAiGenerator(): void {
  if (!homework.value) return
  aiError.value = ''
  aiWarnings.value = []
  aiDrafts.value = []
  aiSelectedIds.value = []
  aiForm.topic = homework.value.title
  aiForm.difficulty = 'medium'
  aiForm.singleChoiceCount = 3
  aiForm.trueFalseCount = 1
  aiForm.learningObjectives = ''
  aiForm.materials = ''
  aiForm.knowledgePointIds = aiKnowledgePoints.value[0]
    ? [aiKnowledgePoints.value[0].id]
    : []
  aiModalOpen.value = true
}

async function generateAiQuestions(): Promise<void> {
  if (!homework.value) return
  if (!aiForm.topic.trim()) {
    aiError.value = '请填写出题主题。'
    return
  }
  const total = Number(aiForm.singleChoiceCount) + Number(aiForm.trueFalseCount)
  if (!Number.isFinite(total) || total < 1 || total > 12) {
    aiError.value = '每次请生成 1 到 12 道题。'
    return
  }
  aiGenerating.value = true
  aiError.value = ''
  try {
    const result = await teacherApi.generateAiQuestions(homework.value.id, {
      topic: aiForm.topic.trim(),
      learningObjectives: aiForm.learningObjectives.trim() || null,
      materials: aiForm.materials.trim() || null,
      difficulty: aiForm.difficulty,
      singleChoiceCount: Math.max(0, Number(aiForm.singleChoiceCount)),
      trueFalseCount: Math.max(0, Number(aiForm.trueFalseCount)),
      knowledgePointIds: [...aiForm.knowledgePointIds],
    })
    const existing = new Set(aiDrafts.value.map((draft) => draft.id))
    aiDrafts.value.push(
      ...result.drafts.filter((draft) => !existing.has(draft.id)),
    )
    aiSelectedIds.value = [
      ...new Set([
        ...aiSelectedIds.value,
        ...result.drafts
          .filter((draft) => draft.status === 'READY')
          .map((draft) => draft.id),
      ]),
    ]
    aiWarnings.value = result.warnings
    ui.toast(
      'success',
      'DeepTutor 已生成候选题',
      `本次得到 ${result.generatedCount} 道，请核对后加入作业。`,
    )
  } catch (cause) {
    aiError.value = errorMessage(cause)
  } finally {
    aiGenerating.value = false
  }
}

function toggleAllAiDrafts(event: Event): void {
  const checked = (event.target as HTMLInputElement).checked
  aiSelectedIds.value = checked
    ? aiDrafts.value
        .filter((draft) => draft.status === 'READY')
        .map((draft) => draft.id)
    : []
}

async function importAiQuestions(): Promise<void> {
  if (!homework.value) return
  if (!aiSelectedIds.value.length) {
    aiError.value = '请至少勾选一道题目。'
    return
  }
  aiImporting.value = true
  aiError.value = ''
  try {
    const result = await teacherApi.importAiQuestions(
      homework.value.id,
      aiSelectedIds.value,
    )
    if (!result.importedCount) {
      throw new Error('没有可导入的题目，请重新生成。')
    }
    ui.toast(
      'success',
      `已加入 ${result.importedCount} 道题`,
      '题目仍需教师核对答案和解析后才会进入批改与讲评依据。',
    )
    aiModalOpen.value = false
    await load()
  } catch (cause) {
    aiError.value = errorMessage(cause)
  } finally {
    aiImporting.value = false
  }
}

function aiAnswerText(draft: AiQuestionDraft): string {
  if (draft.questionType === 'TRUE_FALSE') {
    return draft.standardAnswer === 'FALSE' ? '错误' : '正确'
  }
  return draft.standardAnswer || '未标记'
}

function openEditQuestion(question: Question): void {
  editingQuestionId.value = question.id
  questionError.value = ''
  questionForm.seqNo = question.seqNo
  questionForm.questionType = question.questionType
  questionForm.stem = question.stem
  questionForm.options = question.options
    ? question.options.map((option) => ({ ...option }))
    : defaultOptions()
  questionForm.standardAnswer = question.standardAnswer ?? (question.questionType === 'TRUE_FALSE' ? 'TRUE' : 'A')
  questionForm.teacherAnalysis = question.teacherAnalysis ?? ''
  questionForm.score = question.score
  questionForm.knowledgePointIds = [...question.knowledgePointIds]
  inlineAiError.value = ''
  inlineAiNotice.value = ''
  activeAiDraftId.value = null
  questionModalOpen.value = true
}

async function generateInlineQuestion(): Promise<void> {
  if (!homework.value || editingQuestionId.value) return
  inlineAiGenerating.value = true
  inlineAiError.value = ''
  inlineAiNotice.value = ''
  try {
    const topic =
      inlineAiTopic.value.trim() ||
      questionForm.stem.trim() ||
      homework.value.title
    const result = await teacherApi.generateAiQuestions(homework.value.id, {
      topic,
      learningObjectives: questionForm.stem.trim() || null,
      materials: questionForm.teacherAnalysis.trim() || null,
      difficulty: 'medium',
      singleChoiceCount: questionForm.questionType === 'SINGLE_CHOICE' ? 1 : 0,
      trueFalseCount: questionForm.questionType === 'TRUE_FALSE' ? 1 : 0,
      knowledgePointIds: [...questionForm.knowledgePointIds],
    })
    const draft = result.drafts[0]
    if (!draft) {
      throw new Error('DeepTutor 没有返回可用题目，请更换主题后重试。')
    }
    questionForm.questionType = draft.questionType
    questionForm.stem = draft.stem
    questionForm.options =
      draft.questionType === 'SINGLE_CHOICE'
        ? (draft.options ?? defaultOptions()).map((option) => ({ ...option }))
        : defaultOptions()
    questionForm.standardAnswer =
      draft.questionType === 'TRUE_FALSE'
        ? draft.standardAnswer === 'FALSE'
          ? 'FALSE'
          : 'TRUE'
        : draft.standardAnswer || 'A'
    questionForm.teacherAnalysis = draft.teacherAnalysis ?? ''
    questionForm.score = draft.score || 5
    questionForm.knowledgePointIds = draft.knowledgePointIds.length
      ? [...draft.knowledgePointIds]
      : [...questionForm.knowledgePointIds]
    activeAiDraftId.value = draft.id
    inlineAiNotice.value =
      `DeepTutor 已生成第 ${draft.id} 号候选题并填入表单。请核对标准答案和解析后再添加。`
  } catch (cause) {
    inlineAiError.value = errorMessage(cause)
  } finally {
    inlineAiGenerating.value = false
  }
}

function onTypeChange(): void {
  if (questionForm.questionType === 'TRUE_FALSE') {
    questionForm.standardAnswer = questionForm.standardAnswer === 'FALSE' ? 'FALSE' : 'TRUE'
  } else {
    if (questionForm.options.length < 2) questionForm.options = defaultOptions()
    if (!questionForm.options.some((option) => option.key === questionForm.standardAnswer)) {
      questionForm.standardAnswer = questionForm.options[0]?.key ?? 'A'
    }
  }
}

function addOption(): void {
  if (questionForm.options.length >= 8) return
  const key = String.fromCharCode(65 + questionForm.options.length)
  questionForm.options.push({ key, text: '' })
}

function removeOption(index: number): void {
  if (questionForm.options.length <= 2) return
  questionForm.options.splice(index, 1)
  questionForm.options.forEach((option, optionIndex) => {
    option.key = String.fromCharCode(65 + optionIndex)
  })
  if (!questionForm.options.some((option) => option.key === questionForm.standardAnswer)) {
    questionForm.standardAnswer = questionForm.options[0]?.key ?? 'A'
  }
}

function validateQuestion(): string {
  if (!questionForm.stem.trim()) return '请填写题干。'
  if (!Number.isFinite(questionForm.seqNo) || questionForm.seqNo < 1) return '题号必须大于 0。'
  if (!questionForm.score || questionForm.score <= 0) return '分值必须大于 0。'
  if (!questionForm.knowledgePointIds.length) return '至少关联 1 个知识点。'
  if (questionForm.questionType === 'SINGLE_CHOICE') {
    if (questionForm.options.length < 2) return '单选题至少需要 2 个选项。'
    if (questionForm.options.some((option) => !option.text.trim())) return '选项内容不能为空。'
    if (!questionForm.options.some((option) => option.key === questionForm.standardAnswer)) {
      return '请选择正确选项。'
    }
  } else if (!['TRUE', 'FALSE'].includes(questionForm.standardAnswer)) {
    return '请选择判断题答案。'
  }
  return ''
}

async function saveQuestion(): Promise<void> {
  questionError.value = validateQuestion()
  if (questionError.value) return
  if (!homework.value) return
  questionSaving.value = true
  try {
    const payload: QuestionPayload = {
      seqNo: Number(questionForm.seqNo),
      questionType: questionForm.questionType,
      stem: questionForm.stem.trim(),
      options:
        questionForm.questionType === 'TRUE_FALSE'
          ? null
          : questionForm.options.map((option) => ({ key: option.key, text: option.text.trim() })),
      standardAnswer: questionForm.standardAnswer || null,
      teacherAnalysis: questionForm.teacherAnalysis.trim() || null,
      score: Number(questionForm.score),
      knowledgePointIds: [...questionForm.knowledgePointIds],
      changeReason: homework.value.status === 'PUBLISHED' ? '教师修订题目依据' : null,
      aiDraftId: editingQuestionId.value ? null : activeAiDraftId.value,
    }
    if (editingQuestionId.value) {
      await teacherApi.updateQuestion(editingQuestionId.value, {
        ...payload,
        expectedRevisionNo:
          homework.value.questions.find((question) => question.id === editingQuestionId.value)?.revisionNo ?? 1,
      })
      ui.toast('success', '题目已修订', '新的修订版本已记录，旧批改事实不变。')
    } else {
      await teacherApi.createQuestion(homework.value.id, payload)
      ui.toast('success', '题目已添加', '标准答案与教师解析将作为 AI 生成讲评的依据。')
    }
    questionModalOpen.value = false
    await load()
  } catch (cause) {
    questionError.value = errorMessage(cause)
  } finally {
    questionSaving.value = false
  }
}

async function removeQuestion(question: Question): Promise<void> {
  const accepted = await ui.confirm({
    title: `删除第 ${question.seqNo} 题？`,
    message: '只有尚未导入批改的作业草稿可以删除题目。删除后题号会自动重排，此操作不可撤销。',
    confirmText: '删除题目',
    tone: 'danger',
  })
  if (!accepted) return
  try {
    await teacherApi.deleteQuestion(question.id)
    ui.toast('success', '题目已删除', '剩余题号已自动重排。')
    await load()
  } catch (cause) {
    ui.toast('error', '删除失败', errorMessage(cause))
  }
}

function openMeta(): void {
  if (!homework.value) return
  metaError.value = ''
  metaForm.title = homework.value.title
  metaForm.dueDate = toDateInput(homework.value.dueAt)
  metaForm.description = homework.value.description ?? ''
  metaModalOpen.value = true
}

async function saveMeta(): Promise<void> {
  if (!homework.value) return
  if (!metaForm.title.trim()) {
    metaError.value = '作业名称不能为空。'
    return
  }
  metaSaving.value = true
  try {
    homework.value = await teacherApi.updateHomework(homework.value.id, {
      title: metaForm.title.trim(),
      description: metaForm.description.trim() || null,
      dueAt: metaForm.dueDate ? toIsoDate(metaForm.dueDate) : null,
      expectedVersion: homework.value.version,
    })
    metaModalOpen.value = false
    ui.toast('success', '作业信息已更新')
    await workspace.load(true)
  } catch (cause) {
    metaError.value = errorMessage(cause)
  } finally {
    metaSaving.value = false
  }
}

async function publishHomework(): Promise<void> {
  if (!homework.value) return
  const accepted = await ui.confirm({
    title: '发布这份作业？',
    message: '发布后可以开始导入批改数据。题目仍可修订，但会留下修订版本记录。',
    confirmText: '发布作业',
  })
  if (!accepted) return
  try {
    homework.value = await teacherApi.publishHomework(
      homework.value.id,
      homework.value.version,
      '教师发布作业',
    )
    ui.toast('success', '作业已发布', '现在可以导入 Excel/CSV 批改数据。')
    await workspace.load(true)
  } catch (cause) {
    ui.toast('error', '发布失败', errorMessage(cause))
  }
}

async function archiveHomework(): Promise<void> {
  if (!homework.value) return
  const accepted = await ui.confirm({
    title: '归档这份作业？',
    message: '归档后不再出现在进行中列表，历史批改、讲评与订正记录仍会保留。',
    confirmText: '确认归档',
    tone: 'danger',
  })
  if (!accepted) return
  try {
    homework.value = await teacherApi.archiveHomework(homework.value.id, homework.value.version, '教师归档')
    ui.toast('success', '作业已归档', '历史数据仍可追溯。')
    await workspace.load(true)
  } catch (cause) {
    ui.toast('error', '归档失败', errorMessage(cause))
  }
}

function goGrading(): void {
  if (!homework.value) return
  void router.push(`/teacher/homeworks/${homework.value.id}/grading`)
}

function goStatistics(): void {
  if (!homework.value) return
  void router.push(`/teacher/homeworks/${homework.value.id}/statistics`)
}

function goReview(): void {
  if (!homework.value) return
  void router.push(`/teacher/homeworks/${homework.value.id}/review`)
}

function answerText(question: Question): string {
  if (!question.standardAnswer) return '未填写'
  if (question.standardAnswer === 'TRUE') return '正确'
  if (question.standardAnswer === 'FALSE') return '错误'
  return question.standardAnswer
}

function kpNames(question: Question): string[] {
  return question.knowledgePointIds.map((id) => kpMap.value.get(id) ?? `知识点 #${id}`)
}

watch(
  () => route.params.id,
  () => {
    void load()
  },
)

onMounted(load)
</script>

<template>
  <div class="homework-detail-page">
    <div v-if="loading" class="card loading-card">
      <span class="skeleton" style="width: 260px; height: 10px" />
    </div>

    <div v-else-if="loadError" class="card">
      <AppEmpty icon="alert-circle" title="无法打开这份作业" :description="loadError">
        <button class="btn btn--ghost btn--sm" @click="router.push('/teacher/homeworks')">返回作业列表</button>
      </AppEmpty>
    </div>

    <template v-else-if="homework">
      <RouterLink to="/teacher/homeworks" class="back-link">
        <AppIcon name="arrow-left" :size="14" />
        返回作业列表
      </RouterLink>

      <PageHeader
        :eyebrow="`HOMEWORK · ${homework.className}`"
        :title="homework.title"
        :subtitle="homework.description || '还没有填写作业说明。'"
      >
        <template #meta>
          <div class="header-meta">
            <StatusBadge :status="homework.status" />
            <span><AppIcon name="users" :size="13" />{{ homework.className }} · {{ homework.studentCount }} 人</span>
            <span><AppIcon name="book" :size="13" />{{ homework.questionCount }} 题 · 共 {{ totalScore }} 分</span>
            <span>更新于 {{ formatDateTime(homework.updatedAt) }}</span>
          </div>
        </template>
        <template #actions>
          <button class="btn btn--ghost btn--sm" :disabled="!canEdit" @click="openMeta">
            <AppIcon name="edit" :size="14" />
            编辑信息
          </button>
          <button
            class="btn btn--soft btn--sm ai-entry-button"
            :disabled="!canEdit"
            @click="openAiGenerator"
          >
            <AppIcon name="sparkles" :size="15" />
            AI 出题
          </button>
          <button class="btn btn--primary btn--sm" :disabled="!canEdit" @click="openCreateQuestion">
            <AppIcon name="plus" :size="15" />
            添加题目
          </button>
        </template>
      </PageHeader>

      <div class="detail-layout">
        <div class="detail-main">
          <div class="summary-band">
            <div class="summary-cell">
              <span>题目数量</span>
              <b>{{ homework.questionCount }}<small> 题</small></b>
            </div>
            <div class="summary-cell">
              <span>作业总分</span>
              <b>{{ totalScore }}<small> 分</small></b>
            </div>
            <div class="summary-cell">
              <span>当前批改批次</span>
              <b>{{ homework.currentGradingBatchVersion ? `V${homework.currentGradingBatchVersion}` : '未导入' }}</b>
            </div>
            <div class="summary-cell">
              <span>依据完整度</span>
              <b>{{ evidenceCoverage }}<small>%</small></b>
              <div class="summary-progress">
                <i :style="{ width: `${evidenceCoverage}%` }" />
              </div>
            </div>
          </div>

          <section class="questions-section">
            <div class="section-head">
              <div>
                <h3 class="section-title">题目与依据</h3>
                <p class="section-desc">
                  第一版仅支持单选题与判断题；标准答案、教师解析和知识点会进入 AI 依据快照。
                </p>
              </div>
              <span class="question-count">{{ homework.questionCount }} 题</span>
            </div>

            <div v-if="sortedQuestions.length" class="question-list">
              <article v-for="question in sortedQuestions" :key="question.id" class="question-card">
                <div class="question-index">
                  <span class="numbers">{{ String(question.seqNo).padStart(2, '0') }}</span>
                  <small>题</small>
                </div>
                <div class="question-body">
                  <div class="question-head">
                    <div class="question-labels">
                      <StatusBadge
                        :status="question.questionType"
                        :label="QUESTION_TYPE[question.questionType].label"
                        :tone="QUESTION_TYPE[question.questionType].tone"
                      />
                      <span class="tag">{{ question.score }} 分</span>
                      <span class="tag tag--mono">R{{ question.revisionNo }}</span>
                    </div>
                    <div v-if="canEdit" class="question-actions">
                      <button class="btn btn--quiet btn--xs" type="button" @click="openEditQuestion(question)">
                        <AppIcon name="edit" :size="12" />
                        修订
                      </button>
                      <button
                        class="btn btn--quiet btn--xs delete-btn"
                        type="button"
                        :disabled="!canDeleteQuestions"
                        :title="canDeleteQuestions ? '删除题目' : '已发布或已有批改的作业不可删除题目'"
                        @click="removeQuestion(question)"
                      >
                        <AppIcon name="trash" :size="12" />
                        删除
                      </button>
                    </div>
                  </div>

                  <p class="question-stem">{{ question.stem }}</p>

                  <div v-if="question.options?.length" class="option-grid">
                    <div
                      v-for="option in question.options"
                      :key="option.key"
                      class="option-item"
                      :class="{ 'is-correct': option.key === question.standardAnswer }"
                    >
                      <span class="option-key">{{ option.key }}</span>
                      <span>{{ option.text }}</span>
                      <AppIcon
                        v-if="option.key === question.standardAnswer"
                        name="check"
                        :size="13"
                      />
                    </div>
                  </div>

                  <div class="answer-row">
                    <div class="answer-chip">
                      <span>标准答案</span>
                      <b :class="{ 'is-missing': !question.standardAnswer }">{{ answerText(question) }}</b>
                    </div>
                    <div v-if="kpNames(question).length" class="kp-chips">
                      <span v-for="name in kpNames(question)" :key="name" class="kp-chip">
                        <AppIcon name="layers" :size="10" />
                        {{ name }}
                      </span>
                    </div>
                  </div>

                  <div v-if="question.teacherAnalysis" class="analysis-box">
                    <span>教师解析</span>
                    <p>{{ question.teacherAnalysis }}</p>
                  </div>
                  <div v-else class="missing-evidence">
                    <AppIcon name="alert-triangle" :size="14" />
                    <div>
                      <b>缺少教师解析或标准答案</b>
                      <span>AI 将标记为「依据不足」，不会生成确定性讲解；可在此补充后重新生成。</span>
                    </div>
                  </div>
                </div>
              </article>
            </div>

            <AppEmpty
              v-else
              icon="book"
              title="这份作业还没有题目"
              description="添加第一道单选题或判断题。发布作业前至少需要 1 道题。"
            >
              <button class="btn btn--primary btn--sm" :disabled="!canEdit" @click="openCreateQuestion">
                添加第一道题
              </button>
            </AppEmpty>
          </section>
        </div>

        <aside class="detail-side">
          <section class="card side-card">
            <div class="section-head">
              <div>
                <h3 class="section-title">作业档案</h3>
                <p class="section-desc">学生只会在作业已发布后进入后续链路。</p>
              </div>
            </div>
            <dl class="meta-list">
              <div>
                <dt>班级</dt>
                <dd>{{ homework.className }} · {{ homework.studentCount }} 人</dd>
              </div>
              <div>
                <dt>创建时间</dt>
                <dd>{{ formatDateTime(homework.createdAt) }}</dd>
              </div>
              <div>
                <dt>发布时间</dt>
                <dd>{{ homework.publishedAt ? formatDateTime(homework.publishedAt) : '尚未发布' }}</dd>
              </div>
              <div>
                <dt>截止时间</dt>
                <dd>{{ homework.dueAt ? formatDateTime(homework.dueAt) : '未设置' }}</dd>
              </div>
              <div>
                <dt>讲评状态</dt>
                <dd>
                  <StatusBadge v-if="homework.handoutStatus" :status="homework.handoutStatus" />
                  <span v-else class="muted">尚未生成</span>
                </dd>
              </div>
            </dl>
          </section>

          <section class="card side-card lifecycle-card">
            <h3 class="section-title">下一步</h3>
            <div class="lifecycle-list">
              <button
                class="lifecycle-step"
                :class="{ 'is-current': homework.status === 'DRAFT' }"
                type="button"
                :disabled="homework.status !== 'DRAFT'"
                @click="publishHomework"
              >
                <span class="lifecycle-num">1</span>
                <div>
                  <b>发布作业</b>
                  <small>{{ homework.status === 'DRAFT' ? '发布后才能导入批改' : '已完成' }}</small>
                </div>
                <AppIcon name="chevron-right" :size="14" />
              </button>
              <button
                class="lifecycle-step"
                :class="{ 'is-current': homework.status === 'PUBLISHED' && !homework.currentGradingBatchId }"
                type="button"
                :disabled="homework.status !== 'PUBLISHED'"
                @click="goGrading"
              >
                <span class="lifecycle-num">2</span>
                <div>
                  <b>导入批改</b>
                  <small>{{ homework.currentGradingBatchVersion ? `当前批次 V${homework.currentGradingBatchVersion}` : 'Excel / CSV 整批导入' }}</small>
                </div>
                <AppIcon name="chevron-right" :size="14" />
              </button>
              <button
                class="lifecycle-step"
                :class="{ 'is-current': Boolean(homework.currentGradingBatchId) }"
                type="button"
                :disabled="!homework.currentGradingBatchId"
                @click="goStatistics"
              >
                <span class="lifecycle-num">3</span>
                <div>
                  <b>查看错误统计</b>
                  <small>按题目与知识点自动计算</small>
                </div>
                <AppIcon name="chevron-right" :size="14" />
              </button>
              <button
                class="lifecycle-step"
                :class="{ 'is-current': Boolean(homework.currentGradingBatchId) }"
                type="button"
                :disabled="!homework.currentGradingBatchId"
                @click="goReview"
              >
                <span class="lifecycle-num">4</span>
                <div>
                  <b>审核并发布讲评</b>
                  <small>学生只能在发布后看到 READY 条目</small>
                </div>
                <AppIcon name="chevron-right" :size="14" />
              </button>
            </div>
          </section>

          <section class="card side-card caution-card">
            <div class="caution-icon"><AppIcon name="shield" :size="17" /></div>
            <h4>依据变更提醒</h4>
            <p>
              若当前作业已有批改批次，再修改题干或标准答案，学生端已发布讲评的依据可能已变化。
              建议在讲评审核台重新生成受影响条目并再次确认。
            </p>
            <button class="btn btn--ghost btn--sm" type="button" @click="goReview">打开讲评审核台</button>
          </section>

          <button
            v-if="homework.status === 'PUBLISHED'"
            class="archive-link"
            type="button"
            @click="archiveHomework"
          >
            <AppIcon name="inbox" :size="14" />
            归档这份作业
          </button>
        </aside>
      </div>

      <AppModal
        v-model="questionModalOpen"
        :title="editingQuestionId ? '修订题目' : '添加题目'"
        subtitle="标准答案与教师解析会一并进入 AI 依据快照，未填写则标记依据不足。"
        size="lg"
      >
        <div class="question-form">
          <section v-if="!editingQuestionId" class="inline-ai-generator">
            <div class="inline-ai-head">
              <span class="inline-ai-icon"><AppIcon name="sparkles" :size="18" /></span>
              <div>
                <strong>DeepTutor 快速生成<br class="inline-ai-mobile-break" />这道题</strong>
                <p>
                  按当前题型生成 1 道候选题，并自动填入下方表单；不会直接加入作业。
                </p>
              </div>
            </div>
            <div class="inline-ai-controls">
              <label class="inline-ai-topic">
                <span class="sr-only">AI 出题主题</span>
                <input
                  v-model="inlineAiTopic"
                  class="input"
                  maxlength="300"
                  placeholder="AI 出题主题，例如：一元一次方程去括号"
                />
              </label>
              <button
                class="btn btn--primary btn--sm"
                type="button"
                :disabled="inlineAiGenerating"
                @click="generateInlineQuestion"
              >
                <AppIcon
                  :name="inlineAiGenerating ? 'refresh' : 'sparkles'"
                  :size="15"
                  :class="{ spinning: inlineAiGenerating }"
                />
                {{ inlineAiGenerating ? '正在生成…' : 'AI 生成题目' }}
              </button>
            </div>
            <p
              v-if="inlineAiNotice"
              class="inline-ai-notice"
              role="status"
            >
              <AppIcon name="check-circle" :size="14" />
              {{ inlineAiNotice }}
            </p>
            <p
              v-if="inlineAiError"
              class="inline-ai-error"
              role="alert"
            >
              <AppIcon name="alert-circle" :size="14" />
              {{ inlineAiError }}
            </p>
          </section>

          <div class="form-grid">
            <div class="field">
              <label for="q-seq">题号</label>
              <input id="q-seq" v-model.number="questionForm.seqNo" class="input" type="number" min="1" max="999" />
            </div>
            <div class="field">
              <label for="q-type">题型</label>
              <select id="q-type" v-model="questionForm.questionType" class="select" @change="onTypeChange">
                <option value="SINGLE_CHOICE">单选题</option>
                <option value="TRUE_FALSE">判断题</option>
              </select>
            </div>
            <div class="field field--full">
              <label for="q-stem">题干</label>
              <textarea
                id="q-stem"
                v-model="questionForm.stem"
                class="textarea"
                placeholder="请输入完整题干…"
                maxlength="5000"
              />
            </div>
          </div>

          <template v-if="questionForm.questionType === 'SINGLE_CHOICE'">
            <div class="option-editor">
              <div class="option-editor__head">
                <span>选项与正确答案</span>
                <button class="btn btn--quiet btn--xs" type="button" :disabled="questionForm.options.length >= 8" @click="addOption">
                  <AppIcon name="plus" :size="12" />
                  添加选项
                </button>
              </div>
              <div
                v-for="(option, index) in questionForm.options"
                :key="option.key"
                class="option-edit-row"
                :class="{ 'is-answer': option.key === questionForm.standardAnswer }"
              >
                <label class="option-answer-radio">
                  <input v-model="questionForm.standardAnswer" type="radio" :value="option.key" />
                  <span>{{ option.key }}</span>
                </label>
                <input v-model="option.text" class="input" :placeholder="`选项 ${option.key}`" />
                <button
                  class="icon-btn"
                  type="button"
                  title="删除该选项"
                  :disabled="questionForm.options.length <= 2"
                  @click="removeOption(index)"
                >
                  <AppIcon name="x" :size="14" />
                </button>
              </div>
              <p class="field-hint">左侧圆点用于选择标准答案。</p>
            </div>
          </template>

          <div v-else class="field">
            <label>判断题答案</label>
            <div class="truth-options">
              <label :class="{ 'is-active': questionForm.standardAnswer === 'TRUE' }">
                <input v-model="questionForm.standardAnswer" type="radio" value="TRUE" />
                正确
              </label>
              <label :class="{ 'is-active': questionForm.standardAnswer === 'FALSE' }">
                <input v-model="questionForm.standardAnswer" type="radio" value="FALSE" />
                错误
              </label>
            </div>
          </div>

          <div class="form-grid">
            <div class="field">
              <label for="q-score">分值</label>
              <input id="q-score" v-model.number="questionForm.score" class="input" type="number" min="0.5" step="0.5" />
            </div>
            <div class="field">
              <label for="q-kp">知识点（可多选）</label>
              <div id="q-kp" class="kp-selector">
                <label v-for="kp in skillPoints" :key="kp.id" class="check">
                  <input v-model="questionForm.knowledgePointIds" type="checkbox" :value="kp.id" />
                  {{ kp.name }}
                </label>
              </div>
            </div>
            <div class="field field--full">
              <label for="q-analysis">教师解析（AI 依据）</label>
              <textarea
                id="q-analysis"
                v-model="questionForm.teacherAnalysis"
                class="textarea"
                placeholder="说明易错点与正确思路，避免能力或态度定性。"
                maxlength="5000"
              />
              <span class="field-hint">不填写时，AI 条目会显示「依据不足」，需教师手工补写讲评。</span>
            </div>
          </div>

          <div v-if="questionError" class="error-banner">
            <AppIcon name="alert-circle" :size="15" />
            {{ questionError }}
          </div>
        </div>
        <template #footer>
          <button class="btn btn--ghost" type="button" @click="questionModalOpen = false">取消</button>
          <button class="btn btn--primary" type="button" :disabled="questionSaving" @click="saveQuestion">
            <AppIcon v-if="questionSaving" name="refresh" :size="15" />
            {{ editingQuestionId ? '保存修订' : '添加题目' }}
          </button>
        </template>
      </AppModal>

      <AppModal
        v-model="aiModalOpen"
        title="DeepTutor AI 出题"
        subtitle="先生成候选题，教师核对并勾选后才加入作业。AI 不会直接发布题目。"
        size="lg"
      >
        <div class="ai-question-workspace">
          <div class="ai-request-grid">
            <div class="field field--full">
              <label for="ai-topic">出题主题</label>
              <input
                id="ai-topic"
                v-model="aiForm.topic"
                class="input"
                maxlength="300"
                placeholder="例如：有理数加减法易错题"
              />
            </div>
            <div class="field">
              <label for="ai-difficulty">难度</label>
              <select id="ai-difficulty" v-model="aiForm.difficulty" class="select">
                <option value="easy">基础</option>
                <option value="medium">进阶</option>
                <option value="hard">挑战</option>
              </select>
            </div>
            <div class="field">
              <label for="ai-single-count">单选题数量</label>
              <input
                id="ai-single-count"
                v-model.number="aiForm.singleChoiceCount"
                class="input"
                type="number"
                min="0"
                max="10"
              />
            </div>
            <div class="field">
              <label for="ai-true-count">判断题数量</label>
              <input
                id="ai-true-count"
                v-model.number="aiForm.trueFalseCount"
                class="input"
                type="number"
                min="0"
                max="10"
              />
            </div>
            <div class="field field--full">
              <label for="ai-objectives">学习目标（可选）</label>
              <input
                id="ai-objectives"
                v-model="aiForm.learningObjectives"
                class="input"
                maxlength="1000"
                placeholder="例如：能正确判断符号，并说明绝对值含义"
              />
            </div>
            <div class="field field--full">
              <label for="ai-materials">参考材料或教师要求（可选）</label>
              <textarea
                id="ai-materials"
                v-model="aiForm.materials"
                class="textarea ai-materials"
                maxlength="5000"
                placeholder="可粘贴教材知识点、易错点或希望覆盖的题型。"
              />
            </div>
            <div v-if="aiKnowledgePoints.length" class="field field--full">
              <label>知识点（可多选）</label>
              <div class="kp-selector ai-kp-selector">
                <label v-for="kp in aiKnowledgePoints" :key="kp.id" class="check">
                  <input
                    v-model="aiForm.knowledgePointIds"
                    type="checkbox"
                    :value="kp.id"
                  />
                  {{ kp.name }}
                </label>
              </div>
            </div>
          </div>

          <div class="info-banner ai-policy-note">
            <AppIcon name="sparkles" :size="16" />
            <span>
              DeepTutor 只生成候选题。标准答案、选项和解析会保存到数据库；加入作业后仍可逐题修订。
            </span>
          </div>

          <div v-if="aiWarnings.length" class="info-banner info-banner--warm" role="status">
            <AppIcon name="alert-circle" :size="16" />
            <span>{{ aiWarnings.join(' ') }}</span>
          </div>

          <div v-if="aiError" class="error-banner" role="alert">
            <AppIcon name="alert-circle" :size="15" />
            {{ aiError }}
          </div>

          <section v-if="aiDrafts.length" class="ai-preview">
            <div class="ai-preview-head">
              <div>
                <h3>候选题预览</h3>
                <p>已生成 {{ aiDrafts.length }} 道，已选择 {{ aiSelectedIds.length }} 道。</p>
              </div>
              <label class="check">
                <input
                  type="checkbox"
                  :checked="aiSelectedAll"
                  @change="toggleAllAiDrafts"
                />
                全选
              </label>
            </div>
            <div class="ai-draft-list">
              <label
                v-for="(draft, index) in aiDrafts"
                :key="draft.id"
                class="ai-draft-card"
                :class="{ 'is-selected': aiSelectedIds.includes(draft.id) }"
              >
                <input
                  v-model="aiSelectedIds"
                  type="checkbox"
                  :value="draft.id"
                  :disabled="draft.status !== 'READY'"
                />
                <div class="ai-draft-main">
                  <div class="ai-draft-meta">
                    <span class="ai-draft-index">{{ String(index + 1).padStart(2, '0') }}</span>
                    <StatusBadge
                      :status="draft.questionType"
                      :label="QUESTION_TYPE[draft.questionType].label"
                      :tone="QUESTION_TYPE[draft.questionType].tone"
                    />
                    <span class="tag">{{ draft.score }} 分</span>
                    <span class="tag tag--mono">{{ draft.sourceModel || 'DeepTutor' }}</span>
                  </div>
                  <p class="ai-draft-stem">{{ draft.stem }}</p>
                  <div v-if="draft.options?.length" class="ai-options">
                    <span
                      v-for="option in draft.options"
                      :key="option.key"
                      :class="{ 'is-answer': option.key === draft.standardAnswer }"
                    >
                      <b>{{ option.key }}</b>{{ option.text }}
                    </span>
                  </div>
                  <div class="ai-answer-row">
                    <span>标准答案</span>
                    <b>{{ aiAnswerText(draft) }}</b>
                  </div>
                  <p v-if="draft.teacherAnalysis" class="ai-analysis">
                    {{ draft.teacherAnalysis }}
                  </p>
                </div>
              </label>
            </div>
          </section>

          <div v-else class="ai-empty">
            <span><AppIcon name="sparkles" :size="23" /></span>
            <strong>还没有候选题</strong>
            <p>填写主题和数量后调用 DeepTutor 生成。生成通常需要数秒。</p>
          </div>
        </div>
        <template #footer>
          <button
            class="btn btn--ghost"
            type="button"
            :disabled="aiGenerating || aiImporting"
            @click="aiModalOpen = false"
          >
            取消
          </button>
          <button
            :class="aiDrafts.length ? 'btn btn--soft' : 'btn btn--primary'"
            type="button"
            :disabled="aiGenerating || aiImporting"
            @click="generateAiQuestions"
          >
            <AppIcon
              :name="aiGenerating ? 'refresh' : 'sparkles'"
              :size="15"
              :class="{ spinning: aiGenerating }"
            />
            {{ aiGenerating ? 'DeepTutor 生成中…' : aiDrafts.length ? '再生成一批' : '开始生成' }}
          </button>
          <button
            v-if="aiDrafts.length"
            class="btn btn--primary"
            type="button"
            :disabled="aiImporting || !aiSelectedIds.length"
            @click="importAiQuestions"
          >
            <AppIcon
              :name="aiImporting ? 'refresh' : 'plus'"
              :size="15"
              :class="{ spinning: aiImporting }"
            />
            {{ aiImporting ? '正在加入…' : `加入 ${aiSelectedIds.length} 道题` }}
          </button>
        </template>
      </AppModal>

      <AppModal v-model="metaModalOpen" title="编辑作业信息" size="md">
        <div class="form-grid">
          <div class="field field--full">
            <label for="meta-title">作业名称</label>
            <input id="meta-title" v-model="metaForm.title" class="input" maxlength="128" />
          </div>
          <div class="field field--full">
            <label for="meta-due">截止日期</label>
            <input id="meta-due" v-model="metaForm.dueDate" class="input" type="date" />
          </div>
          <div class="field field--full">
            <label for="meta-desc">作业说明</label>
            <textarea id="meta-desc" v-model="metaForm.description" class="textarea" maxlength="5000" />
          </div>
        </div>
        <div v-if="metaError" class="error-banner" style="margin-top: 14px">
          <AppIcon name="alert-circle" :size="15" />
          {{ metaError }}
        </div>
        <template #footer>
          <button class="btn btn--ghost" type="button" @click="metaModalOpen = false">取消</button>
          <button class="btn btn--primary" type="button" :disabled="metaSaving" @click="saveMeta">保存</button>
        </template>
      </AppModal>
    </template>
  </div>
</template>

<style scoped>
.homework-detail-page {
  max-width: 1260px;
}

.back-link {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-bottom: 12px;
  color: var(--ink-3);
  font-size: 10.5px;
  transition: color 0.18s;
}

.back-link::after {
  content: '';
  position: absolute;
  inset: -12px -8px;
}

.back-link:hover {
  color: var(--role-deep);
}

.header-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px 14px;
  margin-top: 11px;
  color: var(--ink-3);
  font-size: 10px;
}

.header-meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 306px;
  gap: 18px;
  align-items: start;
}

.detail-main {
  min-width: 0;
}

.summary-band {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1px;
  margin-bottom: 20px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--line);
}

.summary-cell {
  position: relative;
  padding: 13px 16px;
  background: #fff;
}

.summary-cell > span {
  display: block;
  color: var(--ink-3);
  font-size: 9px;
}

.summary-cell > b {
  display: block;
  margin-top: 3px;
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 21px;
  font-weight: 600;
  line-height: 1.1;
}

.summary-cell > b small {
  font-family: var(--font-body);
  font-size: 8.5px;
  font-weight: 400;
  color: var(--ink-3);
}

.summary-progress {
  position: absolute;
  right: 16px;
  bottom: 14px;
  left: 16px;
  height: 3px;
  overflow: hidden;
  border-radius: 99px;
  background: #edf4f1;
}

.summary-progress i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--role);
}

.questions-section {
  padding: 5px 0;
}

.question-count {
  display: inline-flex;
  align-items: center;
  padding: 3px 9px;
  border-radius: 999px;
  color: var(--ink-3);
  background: var(--surface-soft);
  font-size: 9.5px;
}

.question-list {
  display: flex;
  flex-direction: column;
  gap: 11px;
}

.question-card {
  display: flex;
  gap: 15px;
  padding: 19px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: #fff;
  box-shadow: var(--shadow-xs);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.question-card:hover {
  border-color: color-mix(in srgb, var(--role) 20%, var(--line));
  box-shadow: var(--shadow-sm);
}

.question-index {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 34px;
  flex: none;
  padding-top: 2px;
}

.question-index .numbers {
  color: var(--ink-3);
  font-family: var(--font-numbers);
  font-size: 24px;
  font-weight: 500;
  line-height: 1;
}

.question-index small {
  margin-top: 4px;
  color: var(--ink-4);
  font-size: 8px;
}

.question-body {
  min-width: 0;
  flex: 1;
}

.question-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.question-labels {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 5px;
}

.question-labels :deep(.badge) {
  min-height: 20px;
  padding: 0 7px;
  font-size: 9px;
}

.question-labels .tag {
  padding: 2px 6px;
  font-size: 8.5px;
}

.question-actions {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.18s;
}

.question-card:hover .question-actions {
  opacity: 1;
}

.question-actions .btn {
  color: var(--ink-3);
}

.delete-btn:hover:not(:disabled) {
  color: var(--coral-deep);
  background: var(--coral-soft);
}

.question-stem {
  margin-top: 9px;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 13.5px;
  line-height: 1.9;
  white-space: pre-wrap;
}

.option-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  margin-top: 11px;
}

.option-item {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 7px 9px;
  border: 1px solid var(--line);
  border-radius: 8px;
  color: var(--ink-2);
  font-size: 10.5px;
  line-height: 1.55;
}

.option-item.is-correct {
  border-color: #cae9de;
  color: #347e6e;
  background: #f3fbf7;
}

.option-key {
  display: grid;
  place-items: center;
  width: 19px;
  height: 19px;
  flex: none;
  border-radius: 5px;
  color: var(--ink-3);
  background: var(--surface-soft);
  font-family: var(--font-mono);
  font-size: 8.5px;
}

.option-item.is-correct .option-key {
  color: #347e6e;
  background: #dff3ea;
}

.option-item .icon {
  margin-left: auto;
  color: var(--mint);
}

.answer-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 11px;
}

.answer-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 9px;
  border-radius: 6px;
  color: var(--ink-3);
  background: var(--surface-soft);
  font-size: 9px;
}

.answer-chip b {
  color: var(--mint-deep);
  font-family: var(--font-serif);
  font-size: 11px;
}

.answer-chip b.is-missing {
  color: var(--coral-deep);
}

.kp-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.kp-chip {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px 7px;
  border-radius: 999px;
  color: #6e77a0;
  background: #f3f5fc;
  font-size: 8.5px;
}

.analysis-box {
  margin-top: 11px;
  padding: 9px 11px;
  border-left: 2px solid #bfdfd4;
  border-radius: 0 7px 7px 0;
  background: #f8fcfa;
}

.analysis-box span {
  color: var(--ink-3);
  font-size: 8.5px;
  letter-spacing: 0.05em;
}

.analysis-box p {
  margin-top: 2px;
  color: var(--ink-2);
  font-family: var(--font-serif);
  font-size: 10.5px;
  line-height: 1.8;
  white-space: pre-wrap;
}

.missing-evidence {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin-top: 11px;
  padding: 8px 10px;
  border: 1px solid #f2e4cf;
  border-radius: 8px;
  color: #997244;
  background: #fdf9f1;
}

.missing-evidence .icon {
  flex: none;
  margin-top: 2px;
}

.missing-evidence b {
  display: block;
  font-size: 9.5px;
}

.missing-evidence span {
  display: block;
  margin-top: 1px;
  color: #b0916d;
  font-size: 9px;
  line-height: 1.6;
}

.detail-side {
  position: sticky;
  top: 84px;
  display: flex;
  flex-direction: column;
  gap: 13px;
}

.side-card {
  padding: 18px;
}

.side-card .section-title {
  font-size: 14px;
}

.side-card .section-desc {
  font-size: 10.5px;
}

.meta-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 4px;
}

.meta-list div {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.meta-list dt {
  flex: none;
  color: var(--ink-3);
  font-size: 9.5px;
}

.meta-list dd {
  margin: 0;
  color: var(--ink-2);
  font-size: 10px;
  line-height: 1.6;
  text-align: right;
}

.meta-list :deep(.badge) {
  min-height: 18px;
  padding: 0 6px;
  font-size: 8.5px;
}

.lifecycle-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: 4px;
}

.lifecycle-step {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 8px 7px;
  border-radius: 9px;
  text-align: left;
  transition: background 0.18s;
}

.lifecycle-step:hover:not(:disabled) {
  background: var(--surface-soft);
}

.lifecycle-step:disabled {
  opacity: 0.58;
}

.lifecycle-step.is-current {
  background: var(--role-wash);
}

.lifecycle-num {
  display: grid;
  place-items: center;
  width: 23px;
  height: 23px;
  flex: none;
  border: 1px solid var(--line);
  border-radius: 50%;
  color: var(--ink-3);
  background: #fff;
  font-family: var(--font-numbers);
  font-size: 9px;
}

.lifecycle-step.is-current .lifecycle-num {
  border-color: transparent;
  color: #fff;
  background: var(--role);
}

.lifecycle-step div {
  min-width: 0;
  flex: 1;
}

.lifecycle-step b {
  display: block;
  color: var(--ink-2);
  font-size: 10.5px;
  font-weight: 600;
}

.lifecycle-step small {
  display: block;
  margin-top: 1px;
  overflow: hidden;
  color: var(--ink-4);
  font-size: 8.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lifecycle-step .icon {
  color: var(--ink-4);
}

.caution-card {
  background: linear-gradient(145deg, #fffdf9, #fdf8ef);
  border-color: #f1e5d3;
}

.caution-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  margin-bottom: 8px;
  border-radius: 10px;
  color: var(--apricot-deep);
  background: rgba(255, 255, 255, 0.6);
}

.caution-card h4 {
  color: #88643a;
  font-size: 11.5px;
}

.caution-card p {
  margin: 5px 0 10px;
  color: #a48661;
  font-size: 9.5px;
  line-height: 1.75;
}

.archive-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  align-self: center;
  color: var(--ink-4);
  font-size: 9.5px;
}

.archive-link:hover {
  color: var(--coral-deep);
}

.ai-entry-button {
  border-color: color-mix(in srgb, var(--lav) 20%, var(--line));
  color: var(--lav-deep);
  background: var(--surface-lav);
}

.inline-ai-generator {
  padding: 13px;
  border: 1px solid color-mix(in srgb, var(--lav) 20%, var(--line));
  border-radius: 15px;
  background:
    radial-gradient(circle at 100% 0, rgb(149 131 221 / 0.12), transparent 48%),
    var(--surface-lav);
}

.inline-ai-head {
  display: flex;
  align-items: flex-start;
  gap: 9px;
}

.inline-ai-icon {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  place-items: center;
  border-radius: 10px;
  background: #fff;
  color: var(--lav);
  box-shadow: var(--shadow-xs);
}

.inline-ai-head strong {
  display: block;
  color: var(--lav-deep);
  font-size: 12px;
}

.inline-ai-head p {
  margin-top: 2px;
  color: var(--ink-2);
  font-size: 9.5px;
  line-height: 1.6;
}

.inline-ai-mobile-break {
  display: none;
}

.inline-ai-controls {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  margin-top: 10px;
}

.inline-ai-controls .input {
  min-height: 38px;
  font-size: 11px;
}

.inline-ai-notice,
.inline-ai-error {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 8px;
  font-size: 9.5px;
  line-height: 1.55;
}

.inline-ai-notice {
  color: var(--mint-deep);
}

.inline-ai-error {
  color: var(--coral-deep);
}

.ai-question-workspace {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.ai-request-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.ai-request-grid .field--full {
  grid-column: 1 / -1;
}

.ai-materials {
  min-height: 92px;
}

.ai-kp-selector {
  max-height: 108px;
  overflow-y: auto;
  padding: 9px 10px;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: var(--surface-soft);
}

.ai-policy-note {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  border-color: color-mix(in srgb, var(--lav) 18%, var(--line));
  background: var(--surface-lav);
  color: var(--lav-deep);
}

.ai-policy-note :deep(.icon),
.info-banner > :deep(.icon) {
  flex: none;
  margin-top: 2px;
}

.ai-preview {
  padding-top: 2px;
}

.ai-preview-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 10px;
}

.ai-preview-head h3 {
  font-size: 13px;
  font-weight: 800;
}

.ai-preview-head p {
  margin-top: 3px;
  color: var(--ink-3);
  font-size: 10.5px;
}

.ai-draft-list {
  display: flex;
  max-height: min(42vh, 440px);
  flex-direction: column;
  gap: 8px;
  overflow-y: auto;
  padding-right: 3px;
}

.ai-draft-card {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: #fbfdfc;
  cursor: pointer;
  transition:
    border-color 0.18s,
    background 0.18s;
}

.ai-draft-card:hover {
  border-color: color-mix(in srgb, var(--lav) 30%, var(--line));
}

.ai-draft-card.is-selected {
  border-color: color-mix(in srgb, var(--lav) 42%, var(--line));
  background: var(--surface-lav);
}

.ai-draft-card > input {
  width: 17px;
  height: 17px;
  margin-top: 3px;
  accent-color: var(--lav);
}

.ai-draft-main {
  min-width: 0;
}

.ai-draft-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.ai-draft-index {
  color: var(--ink-3);
  font-family: var(--font-numbers);
  font-size: 13px;
  font-weight: 600;
}

.ai-draft-stem {
  margin-top: 7px;
  color: var(--ink);
  font-size: 12px;
  font-weight: 700;
  line-height: 1.7;
}

.ai-options {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 7px;
}

.ai-options span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 7px;
  border-radius: 7px;
  background: var(--surface-soft);
  color: var(--ink-2);
  font-size: 9.5px;
}

.ai-options span.is-answer {
  background: var(--mint-soft);
  color: var(--mint-deep);
}

.ai-options b {
  font-size: 8.5px;
}

.ai-answer-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  color: var(--ink-3);
  font-size: 9.5px;
}

.ai-answer-row b {
  color: var(--mint-deep);
  font-size: 10.5px;
}

.ai-analysis {
  margin-top: 7px;
  padding: 7px 9px;
  border-radius: 9px;
  background: rgb(255 255 255 / 0.72);
  color: var(--ink-2);
  font-size: 10px;
  line-height: 1.65;
}

.ai-empty {
  display: flex;
  min-height: 190px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 1px dashed var(--line-strong);
  border-radius: 16px;
  background: var(--surface-soft);
  text-align: center;
}

.ai-empty > span {
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  margin-bottom: 10px;
  border-radius: 15px;
  background: var(--surface-lav);
  color: var(--lav);
}

.ai-empty strong {
  font-size: 12px;
}

.ai-empty p {
  max-width: 340px;
  margin-top: 4px;
  color: var(--ink-3);
  font-size: 10.5px;
  line-height: 1.65;
}

.spinning {
  animation: ai-spin 0.9s linear infinite;
}

@keyframes ai-spin {
  to {
    transform: rotate(360deg);
  }
}

.question-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.option-editor {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.option-editor__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--ink-2);
  font-size: 11px;
  font-weight: 700;
}

.option-edit-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.option-answer-radio {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 33px;
  height: 33px;
  flex: none;
  border: 1px solid var(--line);
  border-radius: 50%;
  background: #fff;
  cursor: pointer;
}

.option-answer-radio input {
  position: absolute;
  opacity: 0;
}

.option-answer-radio span {
  color: var(--ink-3);
  font-family: var(--font-mono);
  font-size: 10px;
}

.option-edit-row.is-answer .option-answer-radio {
  border-color: var(--role);
  background: var(--role-soft);
}

.option-edit-row.is-answer .option-answer-radio span {
  color: var(--role-deep);
}

.option-edit-row .input {
  height: 37px;
}

.truth-options {
  display: flex;
  gap: 8px;
}

.truth-options label {
  flex: 1;
  padding: 9px;
  border: 1px solid var(--line);
  border-radius: 10px;
  color: var(--ink-2);
  font-size: 11.5px;
  text-align: center;
  cursor: pointer;
}

.truth-options label.is-active {
  border-color: var(--role);
  color: var(--role-deep);
  background: var(--role-soft);
}

.truth-options input {
  position: absolute;
  opacity: 0;
}

.kp-selector {
  display: flex;
  flex-wrap: wrap;
  gap: 7px 12px;
  padding: 9px 11px;
  border: 1px solid var(--line);
  border-radius: 11px;
  background: #fdfefd;
}

.kp-selector .check {
  font-size: 10px;
}

@media (max-width: 1050px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }

  .detail-side {
    position: static;
    display: grid;
    grid-template-columns: 1fr 1fr;
  }

  .caution-card {
    grid-column: 1 / -1;
  }

  .archive-link {
    grid-column: 1 / -1;
    align-self: start;
  }
}

@media (max-width: 720px) {
  .summary-band {
    grid-template-columns: 1fr 1fr;
  }

  .question-card {
    padding: 15px;
    gap: 8px;
  }

  .question-index {
    width: 27px;
  }

  .question-index .numbers {
    font-size: 20px;
  }

  .option-grid {
    grid-template-columns: 1fr;
  }

  .question-actions {
    opacity: 1;
  }

  .detail-side {
    display: flex;
  }

  .question-form .form-grid {
    grid-template-columns: 1fr 1fr;
  }

  .question-form .form-grid .field--full {
    grid-column: 1 / -1;
  }

  .ai-request-grid {
    grid-template-columns: 1fr 1fr;
  }

  .ai-request-grid .field--full {
    grid-column: 1 / -1;
  }

  .ai-preview-head {
    align-items: flex-start;
  }

  .ai-draft-list {
    max-height: 48vh;
  }

  .inline-ai-controls {
    grid-template-columns: minmax(0, 1fr);
  }

  .inline-ai-mobile-break {
    display: block;
  }
}
</style>
