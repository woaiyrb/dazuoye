export type RoleCode = 'TEACHER' | 'STUDENT' | 'RESEARCH'
export type PortalRole = RoleCode
export type UserStatus = 'ACTIVE' | 'DISABLED'
export type ClassStatus = 'ACTIVE' | 'ARCHIVED'
export type AuthMode = 'SESSION' | 'JWT'

export interface ApiResponse<T> {
  code: string
  message: string
  data?: T
  traceId?: string
  timestamp?: string
}

export interface PageResult<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
}

export interface CurrentUser {
  id: number
  username: string
  displayName: string
  status: UserStatus
  roles: RoleCode[]
  studentNo?: string | null
  className?: string | null
  classNames?: string[]
}

export interface LoginRequest {
  username: string
  password: string
  portal: PortalRole
}

export interface LoginResult {
  user: CurrentUser
  authMode: AuthMode
  accessToken: string | null
  expiresAt: string
}

export interface ClassSummary {
  id: number
  name: string
  grade: string
  subject: string
  studentCount: number
  activeHomeworkCount?: number
}

export interface StudentSummary {
  id: number
  studentNo: string
  name: string
  status?: UserStatus
}

export type QuestionType = 'SINGLE_CHOICE' | 'TRUE_FALSE'
export type HomeworkStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
export type HandoutStatus = 'UNPUBLISHED' | 'PUBLISHED' | 'WITHDRAWN'
export type HandoutItemStatus =
  | 'DRAFT'
  | 'PENDING_REVIEW'
  | 'READY'
  | 'EXCLUDED'
  | 'INSUFFICIENT_EVIDENCE'
  | 'AI_FAILED_MANUAL'
export type HandoutAction = 'CONFIRM' | 'EXCLUDE' | 'REGENERATE'
export type AiDraftStatus = 'GENERATED' | 'INSUFFICIENT_EVIDENCE' | 'FAILED'
export type EvidenceType =
  | 'QUESTION_STEM'
  | 'STANDARD_ANSWER'
  | 'TEACHER_ANALYSIS'
  | 'KNOWLEDGE_POINT'
  | 'STATISTICS'
  | 'TEMPLATE'

export interface QuestionOption {
  key: string
  text: string
}

export interface Question {
  id: number
  homeworkId: number
  seqNo: number
  questionType: QuestionType
  stem: string
  options: QuestionOption[] | null
  standardAnswer: string | null
  teacherAnalysis: string | null
  score: number
  knowledgePointIds: number[]
  revisionNo: number
}

export interface HomeworkSummary {
  id: number
  classId: number
  className: string
  title: string
  status: HomeworkStatus
  dueAt: string | null
  publishedAt: string | null
  questionCount: number
  studentCount: number
  currentGradingBatchId: number | null
  currentGradingBatchVersion: number | null
  handoutId: number | null
  handoutStatus: HandoutStatus | null
  createdAt: string
  updatedAt: string
  version: number
  description?: string | null
}

export interface HomeworkDetail extends HomeworkSummary {
  description: string | null
  questions: Question[]
}

export interface HomeworkCreateRequest {
  classId: number
  title: string
  description?: string | null
  dueAt?: string | null
}

export interface HomeworkUpdateRequest {
  title?: string
  description?: string | null
  dueAt?: string | null
  expectedVersion: number
}

export type GradingSource = 'MANUAL' | 'EXCEL'

export interface GradingBatch {
  id: number
  homeworkId: number
  versionNo: number
  source: GradingSource
  importedBy?: number
  totalRows: number
  validRows: number
  remark: string | null
  createdAt: string
}

export interface GradingRecord {
  id: number
  batchId: number
  homeworkId: number
  studentId: number
  studentNo: string
  studentName: string
  questionId: number
  seqNo: number
  studentAnswer: string | null
  isCorrect: boolean
  score: number
  teacherNote?: string | null
  errorTag?: string | null
  createdAt: string
}

export interface GradingImportResult {
  batch: GradingBatch
  currentBatchId: number
  statisticsCalculated: boolean
}

export interface GradingRevisionResult {
  batch: GradingBatch
  record: GradingRecord
  statisticsCalculated: boolean
}

export interface ImportRow {
  studentNo: string
  questionSeq: number
  studentAnswer: string
  isCorrect: boolean
  score?: number
}

export interface ImportRowError {
  rowNumber: number
  field?: string | null
  message: string
  rejectedValue?: string | null
}

export interface ImportPreview {
  fileName: string
  rows: ImportRow[]
  rowErrors: ImportRowError[]
}

export interface AiQuestionDraft {
  id: number
  homeworkId: number
  topic: string
  difficulty: 'easy' | 'medium' | 'hard' | string
  questionType: QuestionType
  stem: string
  options: QuestionOption[] | null
  standardAnswer: string | null
  teacherAnalysis: string | null
  score: number
  knowledgePointIds: number[]
  status: 'READY' | 'IMPORTED'
  sourceModel: string | null
  createdAt: string
  importedQuestionId: number | null
}

export interface AiQuestionGenerationResult {
  drafts: AiQuestionDraft[]
  warnings: string[]
  requestedCount: number
  generatedCount: number
}

export interface AiQuestionImportResult {
  importedCount: number
  questions: Question[]
}

export interface StatOverview {
  studentCount: number
  questionCount: number
  totalWrong: number
  totalUnanswered: number
  averageScore: number | null
}

export interface QuestionStatistic {
  questionId: number
  seqNo: number
  questionType: QuestionType
  stemPreview: string | null
  totalCount: number
  correctCount: number
  wrongCount: number
  unansweredCount: number
  errorRate: number
  avgScore: number | null
  optionDistribution: Record<string, number> | null
  knowledgePointIds: number[]
}

export interface KnowledgePointStatistic {
  knowledgePointId: number
  name: string
  parentId: number | null
  totalCount: number
  correctCount: number
  wrongCount: number
  unansweredCount: number
  errorRate: number
  avgScore: number | null
}

export interface HomeworkStatistics {
  homeworkId: number
  batchId: number
  batchVersionNo: number
  calculatedAt: string
  overview: StatOverview
  questions: QuestionStatistic[]
  knowledgePoints: KnowledgePointStatistic[]
}

export interface AiEvidence {
  evidenceType: EvidenceType
  refId?: number | null
  snapshotText: string
}

export interface AiDraft {
  id: number
  homeworkId: number
  questionId: number | null
  draftType: 'QUESTION_COMMENTARY'
  content: string | null
  status: AiDraftStatus
  model: string | null
  promptVersion: string | null
  generationNo: number
  requestId: string | null
  elapsedMs: number | null
  errorCode: string | null
  createdAt: string
  evidence: AiEvidence[]
}

export interface HandoutSummary {
  id: number
  homeworkId: number
  homeworkTitle: string
  status: HandoutStatus
  itemCount: number
  readyCount: number
  pendingReviewCount: number
  blockedCount: number
  excludedCount: number
  publishable: boolean
  blockingReasons: string[]
  publishedAt: string | null
  publishedBy: number | null
  withdrawnAt: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export interface HandoutItemTeacherView {
  id: number
  handoutId: number
  questionId: number
  seqNo: number
  questionType: QuestionType
  stem: string
  options: QuestionOption[] | null
  standardAnswer: string | null
  teacherAnalysis: string | null
  score: number
  source: 'AI' | 'MANUAL' | null
  status: HandoutItemStatus
  sortNo: number
  currentVersionId: number | null
  content: string | null
  versionCount: number
  confirmedBy: number | null
  confirmedAt: string | null
  excludeReason?: string | null
  allowedActions: HandoutAction[]
  evidence: AiEvidence[]
  draft: AiDraft | null
  wrongCount?: number
  errorRate?: number
}

export interface HandoutItemVersion {
  id: number
  versionNo: number
  content: string
  changeType: string
  changeNote: string | null
  createdBy: number
  createdAt: string
}

export interface HandoutDetail extends HandoutSummary {
  gradingBatchId?: number | null
  items: HandoutItemTeacherView[]
}

export interface HandoutLog {
  id: number
  action: 'PUBLISH' | 'WITHDRAW' | 'REPUBLISH'
  fromStatus: HandoutStatus | null
  toStatus: HandoutStatus | null
  itemCount: number | null
  operatorId: number
  operatorName: string | null
  remark: string | null
  createdAt: string
}

export interface CorrectionRecord {
  id: number
  homeworkId: number
  questionId: number
  seqNo: number
  studentId: number
  studentNo: string
  studentName: string
  handoutItemId: number
  itemVersionId: number
  selectedAnswer: string | null
  content: string | null
  versionNo: number
  submittedAt: string
}

export interface CorrectionProgressItem {
  studentId: number
  studentNo: string
  studentName: string
  wrongCount: number
  correctedCount: number
  pendingCount: number
  completionRate: number
  lastSubmittedAt: string | null
}

export interface QuestionCorrectionProgress {
  questionId: number
  seqNo: number
  stem: string | null
  wrongCount: number
  correctedCount: number
  pendingCount: number
  completionRate: number
  lastSubmittedAt: string | null
}

export type DoubtStatus = 'PENDING' | 'REPLIED' | 'CLOSED'

export interface DoubtReply {
  id: number
  teacherId: number
  teacherName: string | null
  content: string
  createdAt: string
}

export interface Doubt {
  id: number
  studentId: number
  studentNo: string
  studentName: string
  handoutItemId: number
  questionId: number
  seqNo: number
  content: string
  status: DoubtStatus
  createdAt: string
  updatedAt: string
  replies: DoubtReply[]
  homeworkId?: number
  homeworkTitle?: string
}

export interface StudentHandoutSummary {
  id: number
  homeworkId: number
  homeworkTitle: string
  publishedAt: string
  itemCount: number
  correctedCount: number
  doubtCount: number
  className?: string
}

export interface StudentGradingView {
  studentAnswer: string | null
  isCorrect: boolean
  score: number
  teacherNote: string | null
}

export interface StudentHandoutItem {
  itemId: number
  questionId: number
  seqNo: number
  questionType: QuestionType
  stem: string
  options: QuestionOption[] | null
  standardAnswer: string | null
  score: number
  commentary: string
  commentaryVersionId: number
  myRecord: StudentGradingView
  corrections: CorrectionRecord[]
  doubts: Doubt[]
}

export interface StudentHandoutDetail {
  id: number
  homeworkId: number
  homeworkTitle: string
  publishedAt: string
  items: StudentHandoutItem[]
}

export interface StudentHomeworkSummary {
  id: number
  title: string
  description: string | null
  dueAt: string | null
  timeEstimateMinutes: number
  questionCount: number
  submitted: boolean
  submissionId: number | null
  attemptNo: number | null
  score: number | null
  maxScore: number | null
  scoreRate: number | null
  submittedAt: string | null
}

export interface StudentHomeworkQuestion {
  id: number
  seqNo: number
  questionType: QuestionType
  stem: string
  options: QuestionOption[] | null
  score: number
}

export interface StudentHomeworkDetail {
  id: number
  title: string
  description: string | null
  instructions: string | null
  dueAt: string | null
  timeEstimateMinutes: number
  questions: StudentHomeworkQuestion[]
}

export interface StudentAnswerResult {
  questionId: number
  seqNo: number
  questionType: QuestionType
  stem: string
  selectedAnswer: string
  correctAnswer: string
  teacherAnalysis: string | null
  isCorrect: boolean
  awardedScore: number
  maxScore: number
}

export interface StudentHomeworkResult {
  id?: number
  submissionId?: number
  homeworkId: number
  studentId: number
  attemptNo: number
  score: number
  maxScore: number
  scoreRate: number
  status?: string
  passed?: boolean
  submittedAt: string
  answers: StudentAnswerResult[]
}

export interface StudentScoreSummary {
  totalScore: number
  maxScore: number
  scoreRate: number
  submissionCount: number
}

export interface StudentWrongQuestion {
  homeworkId: number
  homeworkTitle: string
  questionId: number
  seqNo: number
  questionType: QuestionType
  questionStem: string
  selectedAnswer: string | null
  correctAnswer: string | null
  teacherAnalysis: string | null
  maxScore: number
  attemptNo: number
  submittedAt: string
}

export type KnowledgePointStatus = 'ACTIVE' | 'DISABLED'

export interface KnowledgePoint {
  id: number
  parentId: number | null
  code: string
  name: string
  kpLevel: 1 | 2
  status: KnowledgePointStatus
  disabledImpactNote: string | null
  createdAt: string
  updatedAt: string
  children?: KnowledgePoint[]
}

export type TemplateStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface PublicTemplate {
  id: number
  title: string
  category?: string | null
  structure: Record<string, unknown> | null
  content: string | null
  status: TemplateStatus
  createdBy: number
  createdByName: string | null
  reviewedBy: number | null
  reviewNote: string | null
  reviewedAt: string | null
  createdAt: string
  updatedAt?: string | null
}

export interface OperationLog {
  id: number
  actorId: number | null
  actorRole: RoleCode | 'SYSTEM' | null
  actorName?: string | null
  action: string
  targetType: string | null
  targetId: number | null
  summary: string | null
  detail: Record<string, unknown> | null
  createdAt: string
  ip?: string | null
}

export interface AiExplainRequest {
  handoutItemId: number
  question: string
}

export interface AiExplainResult {
  answer: string
  evidence: AiEvidence[]
  aiDraftId: number | null
}
