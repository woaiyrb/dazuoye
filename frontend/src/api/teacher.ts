import type {
  ClassStatus,
  ClassSummary,
  CorrectionProgressItem,
  CorrectionRecord,
  AiQuestionGenerationResult,
  AiQuestionImportResult,
  Doubt,
  GradingBatch,
  GradingImportResult,
  GradingRecord,
  GradingRevisionResult,
  HandoutDetail,
  HandoutItemStatus,
  HandoutItemTeacherView,
  HandoutItemVersion,
  HandoutLog,
  HomeworkCreateRequest,
  HomeworkDetail,
  HomeworkStatistics,
  HomeworkSummary,
  ImportRow,
  KnowledgePoint,
  PageResult,
  PublicTemplate,
  Question,
  QuestionCorrectionProgress,
  QuestionType,
  StudentSummary,
} from '@/types/models'
import { isMockMode, request } from './client'

export interface HomeworkQuery {
  status?: string
  keyword?: string
  page?: number
  pageSize?: number
}

export interface RecordQuery {
  batchId?: number
  keyword?: string
  isCorrect?: boolean
  seqNo?: number
  page?: number
  pageSize?: number
}

export interface QuestionPayload {
  seqNo: number
  questionType: QuestionType
  stem: string
  options?: Array<{ key: string; text: string }> | null
  standardAnswer?: string | null
  teacherAnalysis?: string | null
  score: number
  knowledgePointIds: number[]
  changeReason?: string | null
  aiDraftId?: number | null
}

export interface ConfirmItemPayload {
  content?: string | null
  expectedVersionId?: number | null
  changeNote?: string | null
}

export interface HandoutLogPage {
  items: HandoutLog[]
  page: number
  pageSize: number
  total: number
}

export interface CorrectionQuery {
  keyword?: string
  page?: number
  pageSize?: number
}

export interface DoubtQuery {
  status?: string
  keyword?: string
  page?: number
  pageSize?: number
}

export interface CorrectionProgressQuery {
  keyword?: string
  page?: number
  pageSize?: number
}

export const teacherApi = {
  getClasses() {
    return request<ClassSummary[]>('GET', '/teacher/classes')
  },

  getClassStudents(
    classId: number,
    params: { keyword?: string; includeDisabled?: boolean; page?: number; pageSize?: number } = {},
  ) {
    return request<PageResult<StudentSummary>>('GET', `/teacher/classes/${classId}/students`, { params })
  },

  createClass(payload: { name: string; grade?: string | null; subject?: string | null }) {
    return request<ClassSummary>('POST', '/teacher/classes', { data: payload })
  },

  updateClass(
    classId: number,
    payload: { name?: string; grade?: string; subject?: string; status?: ClassStatus },
  ) {
    return request<ClassSummary>('PUT', `/teacher/classes/${classId}`, { data: payload })
  },

  createStudent(classId: number, payload: { studentNo: string; name: string }) {
    return request<StudentSummary>('POST', `/teacher/classes/${classId}/students`, { data: payload })
  },

  updateStudent(
    classId: number,
    studentId: number,
    payload: { name?: string; status?: 'ACTIVE' | 'DISABLED' },
  ) {
    return request<StudentSummary>('PUT', `/teacher/classes/${classId}/students/${studentId}`, {
      data: payload,
    })
  },

  importStudents(classId: number, rows: Array<{ studentNo: string; name: string }>) {
    return request<{ created: number }>('POST', `/teacher/classes/${classId}/students/import`, {
      data: { rows },
    })
  },

  // 只读知识点列表，供题目关联使用；维护入口属于教研角色。
  getKnowledgePoints() {
    return request<KnowledgePoint[]>('GET', '/teacher/knowledge-points')
  },

  listApprovedTemplates() {
    return request<PublicTemplate[]>('GET', '/teacher/templates/approved')
  },

  listMyTemplates() {
    return request<PublicTemplate[]>('GET', '/teacher/templates/mine')
  },

  createTemplate(payload: { title: string; content: string; sections: string[] }) {
    return request<PublicTemplate>('POST', '/teacher/templates', { data: payload })
  },

  listHomeworks(params: HomeworkQuery = {}) {
    return request<PageResult<HomeworkSummary>>('GET', '/teacher/homeworks', { params })
  },

  getHomework(homeworkId: number) {
    return request<HomeworkDetail>('GET', `/teacher/homeworks/${homeworkId}`)
  },

  createHomework(payload: HomeworkCreateRequest) {
    return request<HomeworkDetail>('POST', '/teacher/homeworks', { data: payload })
  },

  updateHomework(
    homeworkId: number,
    payload: { title?: string; description?: string | null; dueAt?: string | null; expectedVersion: number },
  ) {
    return request<HomeworkDetail>('PUT', `/teacher/homeworks/${homeworkId}`, { data: payload })
  },

  publishHomework(homeworkId: number, expectedVersion: number, remark?: string) {
    return request<HomeworkDetail>('POST', `/teacher/homeworks/${homeworkId}/publish`, {
      data: { expectedVersion, remark: remark ?? null },
    })
  },

  archiveHomework(homeworkId: number, expectedVersion: number, remark?: string) {
    return request<HomeworkDetail>('POST', `/teacher/homeworks/${homeworkId}/archive`, {
      data: { expectedVersion, remark: remark ?? null },
    })
  },

  createQuestion(homeworkId: number, payload: QuestionPayload) {
    return request<Question>('POST', `/teacher/homeworks/${homeworkId}/questions`, { data: payload })
  },

  generateAiQuestions(
    homeworkId: number,
    payload: {
      topic: string
      learningObjectives?: string | null
      materials?: string | null
      difficulty: 'easy' | 'medium' | 'hard'
      singleChoiceCount: number
      trueFalseCount: number
      knowledgePointIds: number[]
    },
  ) {
    return request<AiQuestionGenerationResult>(
      'POST',
      `/teacher/homeworks/${homeworkId}/ai-questions/generate`,
      { data: payload, timeout: 150_000 },
    )
  },

  importAiQuestions(homeworkId: number, draftIds: number[]) {
    return request<AiQuestionImportResult>(
      'POST',
      `/teacher/homeworks/${homeworkId}/ai-questions/import`,
      { data: { draftIds } },
    )
  },

  updateQuestion(questionId: number, payload: QuestionPayload & { expectedRevisionNo: number }) {
    return request<Question>('PUT', `/teacher/questions/${questionId}`, { data: payload })
  },

  deleteQuestion(questionId: number) {
    return request<void>('DELETE', `/teacher/questions/${questionId}`)
  },

  listGradingBatches(homeworkId: number) {
    return request<GradingBatch[]>('GET', `/teacher/homeworks/${homeworkId}/grading-batches`)
  },

  listGradingRecords(homeworkId: number, params: RecordQuery = {}) {
    return request<PageResult<GradingRecord>>('GET', `/teacher/homeworks/${homeworkId}/grading-records`, {
      params,
    })
  },

  importGrading(
    homeworkId: number,
    file: File | null,
    options: { rows?: ImportRow[]; remark?: string; fileName?: string; confirmNewBatch?: boolean } = {},
  ) {
    if (isMockMode) {
      return request<GradingImportResult>('POST', `/teacher/homeworks/${homeworkId}/grading/import`, {
        data: {
          rows: options.rows ?? [],
          remark: options.remark ?? null,
          fileName: file?.name ?? options.fileName ?? '演示批次.csv',
        },
      })
    }
    const form = new FormData()
    if (file) form.append('file', file)
    if (options.remark) form.append('remark', options.remark)
    if (options.confirmNewBatch) form.append('confirmNewBatch', 'true')
    return request<GradingImportResult>('POST', `/teacher/homeworks/${homeworkId}/grading/import`, {
      data: form,
    })
  },

  importManualGrading(
    homeworkId: number,
    rows: Array<{
      studentNo: string
      seqNo: number
      studentAnswer?: string | null
      isCorrect: boolean
      teacherNote?: string | null
      errorTag?: string | null
    }>,
    remark?: string,
  ) {
    return request<GradingImportResult>('POST', `/teacher/homeworks/${homeworkId}/grading/manual`, {
      data: { rows, remark: remark ?? null, confirmNewBatch: true },
    })
  },

  updateGradingRecord(
    recordId: number,
    payload: {
      studentAnswer?: string | null
      isCorrect: boolean
      score: number
      teacherNote?: string | null
      expectedBatchId: number
      remark?: string
    },
  ) {
    return request<GradingRevisionResult>('PUT', `/teacher/grading-records/${recordId}`, {
      data: payload,
    })
  },

  getStatistics(homeworkId: number) {
    return request<HomeworkStatistics>('GET', `/teacher/homeworks/${homeworkId}/statistics`)
  },

  getHandout(homeworkId: number) {
    return request<HandoutDetail>('GET', `/teacher/homeworks/${homeworkId}/handout`)
  },

  generateHandout(
    homeworkId: number,
    payload: { questionIds?: number[]; regenerateConfirmed?: boolean; templateId?: number | null } = {},
  ) {
    return request<HandoutDetail>('POST', `/teacher/homeworks/${homeworkId}/handouts/generate`, {
      data: payload,
      timeout: 180_000,
    })
  },

  confirmHandoutItem(itemId: number, payload: ConfirmItemPayload) {
    return request<HandoutItemTeacherView>('POST', `/teacher/handout-items/${itemId}/confirm`, {
      data: payload,
    })
  },

  saveHandoutItem(
    itemId: number,
    payload: { content: string; expectedVersionId?: number | null; changeNote?: string | null },
  ) {
    return request<HandoutItemTeacherView>('POST', `/teacher/handout-items/${itemId}/save`, {
      data: payload,
    })
  },

  listHandoutItemVersions(itemId: number) {
    return request<HandoutItemVersion[]>('GET', `/teacher/handout-items/${itemId}/versions`)
  },

  excludeHandoutItem(itemId: number, reason: string, expectedVersionId?: number | null) {
    return request<HandoutItemTeacherView>('POST', `/teacher/handout-items/${itemId}/exclude`, {
      data: { reason, expectedVersionId: expectedVersionId ?? null },
    })
  },

  regenerateHandoutItem(itemId: number, expectedVersionId?: number | null) {
    return request<HandoutItemTeacherView>('POST', `/teacher/handout-items/${itemId}/regenerate`, {
      data: { expectedVersionId: expectedVersionId ?? null },
    })
  },

  publishHandout(handoutId: number, expectedVersion: number, remark?: string) {
    return request<HandoutDetail>('POST', `/teacher/handouts/${handoutId}/publish`, {
      data: { expectedVersion, remark: remark ?? null },
    })
  },

  withdrawHandout(handoutId: number, expectedVersion: number, remark?: string) {
    return request<HandoutDetail>('POST', `/teacher/handouts/${handoutId}/withdraw`, {
      data: { expectedVersion, remark: remark ?? null },
    })
  },

  getHandoutLogs(handoutId: number, params: { page?: number; pageSize?: number } = {}) {
    return request<HandoutLogPage>('GET', `/teacher/handouts/${handoutId}/logs`, { params })
  },

  listCorrections(homeworkId: number, params: CorrectionQuery = {}) {
    return request<PageResult<CorrectionRecord>>(
      'GET',
      `/teacher/homeworks/${homeworkId}/corrections`,
      { params },
    )
  },

  listCorrectionProgress(homeworkId: number, params: CorrectionProgressQuery = {}) {
    return request<PageResult<CorrectionProgressItem>>(
      'GET',
      `/teacher/homeworks/${homeworkId}/correction-progress`,
      { params },
    )
  },

  listQuestionCorrectionProgress(
    homeworkId: number,
    params: { page?: number; pageSize?: number } = {},
  ) {
    return request<PageResult<QuestionCorrectionProgress>>(
      'GET',
      `/teacher/homeworks/${homeworkId}/correction-progress/by-question`,
      { params },
    )
  },

  listDoubts(params: DoubtQuery = {}) {
    return request<PageResult<Doubt>>('GET', '/teacher/doubts', { params })
  },

  replyDoubt(doubtId: number, content: string) {
    return request<Doubt>('POST', `/teacher/doubts/${doubtId}/replies`, {
      data: { content },
    })
  },
}

export type { HandoutItemStatus }
