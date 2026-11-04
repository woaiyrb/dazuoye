import type {
  AiDraft,
  AiEvidence,
  ClassSummary,
  CorrectionProgressItem,
  CorrectionRecord,
  CurrentUser,
  Doubt,
  GradingBatch,
  GradingRecord,
  HandoutLog,
  HomeworkDetail,
  HomeworkStatistics,
  HomeworkSummary,
  ImportRow,
  ImportRowError,
  KnowledgePoint,
  LoginRequest,
  LoginResult,
  PageResult,
  PublicTemplate,
  Question,
  QuestionStatistic,
  StudentHandoutItem,
  StudentHandoutSummary,
  StudentSummary,
} from '@/types/models'
import { ApiError } from '../errors'
import type { RequestOptions } from '../client'
import {
  db,
  nextId,
  nowIso,
  persistDb,
  refreshHandoutState,
  resetDb,
  type MockHandout,
  type MockHandoutItem,
  type MockStudent,
  type MockUser,
} from './db'

const AUTH_KEY = 'mh.session.v1'
const MAX_FAILURES = 5
const LOCK_MS = 60_000
const sessionAttempts = new Map<string, { failures: number; lockedUntil: number }>()

type Payload = Record<string, unknown>

interface StoredSession {
  user: CurrentUser
  authMode: 'SESSION' | 'JWT'
  accessToken: string | null
  expiresAt: string
}

function fail(status: number, code: string, message: string, data?: unknown): never {
  throw new ApiError(code, message, status, data)
}

function wait(ms = 180): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function round2(value: number): number {
  return Math.round(value * 100) / 100
}

function asPayload(options: RequestOptions): Payload {
  return (options.data && typeof options.data === 'object' ? options.data : {}) as Payload
}

function asString(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

function asNumber(value: unknown, fallback = 0): number {
  const n = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(n) ? n : fallback
}

function param(options: RequestOptions, key: string): unknown {
  return (options.params as Record<string, unknown> | undefined)?.[key]
}

function pageOf<T>(items: T[], options: RequestOptions, defaultSize = 20): PageResult<T> {
  const page = Math.max(1, Math.floor(asNumber(param(options, 'page'), 1)))
  const pageSize = Math.min(100, Math.max(1, Math.floor(asNumber(param(options, 'pageSize'), defaultSize))))
  const start = (page - 1) * pageSize
  return { items: items.slice(start, start + pageSize), page, pageSize, total: items.length }
}

function readSession(): StoredSession | null {
  const raw = localStorage.getItem(AUTH_KEY)
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as StoredSession
    if (!parsed?.user) return null
    if (parsed.expiresAt && new Date(parsed.expiresAt).getTime() < Date.now()) return null
    return parsed
  } catch {
    return null
  }
}

function requireUser(): MockUser {
  const session = readSession()
  if (!session) fail(401, 'AUTH_SESSION_EXPIRED', '登录状态已失效，请重新登录')
  const user = db.users.find((item) => item.id === session.user.id)
  if (!user || user.status !== 'ACTIVE') fail(401, 'AUTH_SESSION_EXPIRED', '登录状态已失效，请重新登录')
  return user
}

function requireRole(role: MockUser['roles'][number]): MockUser {
  const user = requireUser()
  if (!user.roles.includes(role)) fail(403, 'FORBIDDEN', '当前账号无权访问该功能')
  return user
}

function requireStudent(user: MockUser): MockStudent {
  const student = db.students.find((item) => item.id === user.studentId)
  if (!student) fail(403, 'FORBIDDEN', '未找到当前学生档案')
  return student
}

function teacherClassIds(user: MockUser): number[] {
  return db.teacherClasses
    .filter((item) => item.teacherUserId === user.id)
    .map((item) => item.classId)
}

function requireTeacherHomework(user: MockUser, homeworkId: number): HomeworkDetail {
  const homework = db.homeworks.find((item) => item.id === homeworkId)
  if (!homework) fail(404, 'NOT_FOUND', '作业不存在或已被删除')
  if (!teacherClassIds(user).includes(homework.classId)) {
    fail(403, 'FORBIDDEN', '只能访问本人负责班级的作业')
  }
  return homework
}

function requireTeacherClass(user: MockUser, classId: number): ClassSummary {
  const classInfo = db.classes.find((item) => item.id === classId)
  if (!classInfo) fail(404, 'NOT_FOUND', '班级不存在')
  if (!teacherClassIds(user).includes(classId)) fail(403, 'FORBIDDEN', '只能访问本人负责的班级')
  return classInfo
}

function handoutOfHomework(homeworkId: number): MockHandout | undefined {
  return db.handouts.find((item) => item.homeworkId === homeworkId)
}

function handoutById(handoutId: number): MockHandout {
  const handout = db.handouts.find((item) => item.id === handoutId)
  if (!handout) fail(404, 'NOT_FOUND', '讲评不存在')
  return handout
}

function itemById(itemId: number): { handout: MockHandout; item: MockHandoutItem } {
  const handout = db.handouts.find((candidate) => candidate.items.some((item) => item.id === itemId))
  if (!handout) fail(404, 'NOT_FOUND', '讲评条目不存在')
  const item = handout.items.find((candidate) => candidate.id === itemId)
  if (!item) fail(404, 'NOT_FOUND', '讲评条目不存在')
  return { handout, item }
}

function syncHomeworkHandout(homework: HomeworkDetail, handout?: MockHandout | null): void {
  homework.handoutId = handout?.id ?? null
  homework.handoutStatus = handout?.status ?? null
  homework.updatedAt = nowIso()
}

function hydrateHomework(homework: HomeworkDetail): HomeworkDetail {
  homework.questionCount = homework.questions.length
  syncHomeworkHandout(homework, handoutOfHomework(homework.id))
  return homework
}

function toHomeworkSummary(homework: HomeworkDetail): HomeworkSummary {
  const hydrated = hydrateHomework(homework)
  return {
    id: hydrated.id,
    classId: hydrated.classId,
    className: hydrated.className,
    title: hydrated.title,
    status: hydrated.status,
    dueAt: hydrated.dueAt,
    publishedAt: hydrated.publishedAt,
    questionCount: hydrated.questionCount,
    studentCount: hydrated.studentCount,
    currentGradingBatchId: hydrated.currentGradingBatchId,
    currentGradingBatchVersion: hydrated.currentGradingBatchVersion,
    handoutId: hydrated.handoutId,
    handoutStatus: hydrated.handoutStatus,
    createdAt: hydrated.createdAt,
    updatedAt: hydrated.updatedAt,
    version: hydrated.version,
    description: hydrated.description,
  }
}

function addOperationLog(
  actor: MockUser,
  action: string,
  targetType: string,
  targetId: number | null,
  summary: string,
  detail: Record<string, unknown> | null = null,
): void {
  db.operationLogs.unshift({
    id: nextId(db, 'log'),
    actorId: actor.id,
    actorRole: actor.roles[0],
    actorName: actor.displayName,
    action,
    targetType,
    targetId,
    summary,
    detail,
    createdAt: nowIso(),
    ip: '127.0.0.1',
  })
}

function addHandoutLog(
  handout: MockHandout,
  operator: MockUser,
  action: HandoutLog['action'],
  fromStatus: HandoutLog['fromStatus'],
  toStatus: HandoutLog['toStatus'],
  remark: string,
): void {
  handout.logs.unshift({
    id: nextId(db, 'log'),
    action,
    fromStatus,
    toStatus,
    itemCount: handout.items.filter((item) => item.status === 'READY').length,
    operatorId: operator.id,
    operatorName: operator.displayName,
    remark,
    createdAt: nowIso(),
  })
}

function currentBatchId(homework: HomeworkDetail): number | null {
  return homework.currentGradingBatchId
}

function recordsOfCurrentBatch(homework: HomeworkDetail): GradingRecord[] {
  const batchId = currentBatchId(homework)
  if (!batchId) return []
  return db.records.filter((record) => record.batchId === batchId)
}

function visibleItemsForStudent(handout: MockHandout, studentId: number): MockHandoutItem[] {
  const homework = db.homeworks.find((item) => item.id === handout.homeworkId)
  if (!homework) return []
  const records = recordsOfCurrentBatch(homework).filter(
    (record) => record.studentId === studentId && !record.isCorrect,
  )
  return handout.items.filter(
    (item) =>
      item.status === 'READY' &&
      records.some((record) => record.questionId === item.questionId),
  )
}

function studentHandouts(studentId: number): MockHandout[] {
  const student = db.students.find((item) => item.id === studentId)
  if (!student) return []
  return db.handouts.filter((handout) => {
    if (handout.status !== 'PUBLISHED') return false
    const homework = db.homeworks.find((item) => item.id === handout.homeworkId)
    return homework?.classId === student.classId && visibleItemsForStudent(handout, studentId).length > 0
  })
}

function computeStatistics(homework: HomeworkDetail): HomeworkStatistics {
  const batchId = currentBatchId(homework)
  if (!batchId) fail(409, 'GRADING_NOT_READY', '该作业尚未导入批改数据')
  const batch = db.batches.find((item) => item.id === batchId)
  if (!batch) fail(409, 'GRADING_NOT_READY', '当前批改批次不存在')
  const students = db.students.filter(
    (student) => student.classId === homework.classId && student.status !== 'DISABLED',
  )
  const activeStudentIds = new Set(students.map((student) => student.id))
  const records = db.records.filter(
    (record) => record.batchId === batchId && activeStudentIds.has(record.studentId),
  )
  const questionStats: QuestionStatistic[] = [...homework.questions]
    .sort((a, b) => a.seqNo - b.seqNo)
    .map((question) => {
      const qRecords = records.filter((record) => record.questionId === question.id)
      const correctCount = qRecords.filter((record) => record.isCorrect).length
      const unansweredCount = qRecords.filter((record) => record.studentAnswer === null).length
      const wrongCount = qRecords.filter(
        (record) => !record.isCorrect && record.studentAnswer !== null,
      ).length
      const distribution: Record<string, number> = {}
      for (const record of qRecords) {
        const key = record.studentAnswer ?? '未作答'
        distribution[key] = (distribution[key] ?? 0) + 1
      }
      const avgScore = qRecords.length
        ? round2(qRecords.reduce((sum, record) => sum + record.score, 0) / qRecords.length)
        : null
      return {
        questionId: question.id,
        seqNo: question.seqNo,
        questionType: question.questionType,
        stemPreview: question.stem.length > 38 ? `${question.stem.slice(0, 38)}…` : question.stem,
        totalCount: qRecords.length,
        correctCount,
        wrongCount,
        unansweredCount,
        errorRate: qRecords.length ? round2((wrongCount / qRecords.length) * 100) : 0,
        avgScore,
        optionDistribution: distribution,
        knowledgePointIds: question.knowledgePointIds,
      }
    })

  const skillPoints = db.knowledgePoints.filter((kp) => kp.kpLevel === 2)
  const knowledgeStats = skillPoints
    .map((kp) => {
      const questionIds = homework.questions
        .filter((question) => question.knowledgePointIds.includes(kp.id))
        .map((question) => question.id)
      if (!questionIds.length) return null
      const relevant = records.filter((record) => questionIds.includes(record.questionId))
      const ids = new Set(relevant.map((record) => record.studentId))
      let correctCount = 0
      let wrongCount = 0
      let unansweredCount = 0
      for (const student of students) {
        if (!ids.has(student.id)) continue
        const own = relevant.filter((record) => record.studentId === student.id)
        if (own.some((record) => record.studentAnswer === null)) unansweredCount += 1
        if (own.some((record) => !record.isCorrect && record.studentAnswer !== null)) wrongCount += 1
        if (own.every((record) => record.isCorrect)) correctCount += 1
      }
      const avgScore = relevant.length
        ? round2(relevant.reduce((sum, record) => sum + record.score, 0) / relevant.length)
        : null
      return {
        knowledgePointId: kp.id,
        name: kp.name,
        parentId: kp.parentId,
        totalCount: ids.size,
        correctCount,
        wrongCount,
        unansweredCount,
        errorRate: ids.size ? round2((wrongCount / ids.size) * 100) : 0,
        avgScore,
      }
    })
    .filter((item): item is NonNullable<typeof item> => item !== null)
    .sort((a, b) => b.errorRate - a.errorRate)

  const totalWrong = records.filter((record) => !record.isCorrect).length
  const totalUnanswered = records.filter((record) => record.studentAnswer === null).length
  const averageScore = records.length
    ? round2(records.reduce((sum, record) => sum + record.score, 0) / records.length)
    : null

  return {
    homeworkId: homework.id,
    batchId,
    batchVersionNo: batch.versionNo,
    calculatedAt: nowIso(),
    overview: {
      studentCount: students.length,
      questionCount: homework.questions.length,
      totalWrong,
      totalUnanswered,
      averageScore,
    },
    questions: questionStats,
    knowledgePoints: knowledgeStats,
  }
}

function decorateHandout(homework: HomeworkDetail, handout: MockHandout): MockHandout {
  let statistics: HomeworkStatistics | null = null
  try {
    statistics = computeStatistics(homework)
  } catch {
    statistics = null
  }
  if (statistics) {
    for (const item of handout.items) {
      const stat = statistics.questions.find((question) => question.questionId === item.questionId)
      item.wrongCount = stat?.wrongCount ?? 0
      item.errorRate = stat?.errorRate ?? 0
    }
  }
  return handout
}

function makeEvidence(question: Question, wrongCount: number, total: number): AiEvidence[] {
  const evidence: AiEvidence[] = [
    {
      evidenceType: 'QUESTION_STEM',
      refId: question.id,
      snapshotText: `${question.stem}${question.options ? ` ${question.options.map((option) => `${option.key}. ${option.text}`).join(' ')}` : ''}`,
    },
  ]
  if (question.standardAnswer) {
    evidence.push({
      evidenceType: 'STANDARD_ANSWER',
      refId: question.id,
      snapshotText: question.standardAnswer,
    })
  }
  if (question.teacherAnalysis) {
    evidence.push({
      evidenceType: 'TEACHER_ANALYSIS',
      refId: question.id,
      snapshotText: question.teacherAnalysis,
    })
  }
  for (const kpId of question.knowledgePointIds) {
    const kp = db.knowledgePoints.find((item) => item.id === kpId)
    if (kp) {
      evidence.push({ evidenceType: 'KNOWLEDGE_POINT', refId: kp.id, snapshotText: kp.name })
    }
  }
  if (total) {
    evidence.push({
      evidenceType: 'STATISTICS',
      refId: null,
      snapshotText: `第${question.seqNo}题：${total} 人中 ${wrongCount} 人答错，错误率 ${round2((wrongCount / total) * 100)}%。`,
    })
  }
  return evidence
}

function makeAiCommentary(question: Question, wrongCount: number, total: number): string {
  const answerText = question.standardAnswer === 'TRUE'
    ? '正确'
    : question.standardAnswer === 'FALSE'
      ? '错误'
      : question.standardAnswer
  const base = question.teacherAnalysis
    ? `${question.teacherAnalysis.replace(/[。；;]+$/, '')}。`
    : ''
  const answer = answerText ? `本题正确答案为 ${answerText}。` : ''
  const stat = total
    ? `本题错误率 ${round2((wrongCount / total) * 100)}%，可结合典型错误在课堂集中讲解。`
    : ''
  return `${base}${answer}${stat}`.trim()
}

function buildTeacherDraft(
  question: Question,
  content: string,
  evidence: AiEvidence[],
  status: AiDraft['status'],
  errorCode: string | null = null,
): AiDraft {
  return {
    id: nextId(db, 'draft'),
    homeworkId: question.homeworkId,
    questionId: question.id,
    draftType: 'QUESTION_COMMENTARY',
    content,
    status,
    model: 'mock-qwen-plus',
    promptVersion: 'prompt-v1',
    generationNo: 1,
    requestId: `req-mock-${Date.now()}`,
    elapsedMs: 1200,
    errorCode,
    createdAt: nowIso(),
    evidence,
  }
}

function canEditHandout(handout: MockHandout): void {
  if (handout.status === 'PUBLISHED') {
    fail(409, 'HANDOUT_PUBLISHED_LOCKED', '讲评已发布，请先撤回后再编辑')
  }
}

function requirePublishable(handout: MockHandout): void {
  refreshHandoutState(handout)
  if (!handout.publishable) {
    fail(409, 'HANDOUT_NOT_READY', '讲评尚未达到发布条件', {
      blockingReasons: handout.blockingReasons,
    })
  }
}

function addBatchRecords(
  homework: HomeworkDetail,
  rows: Array<{
    student: MockStudent
    question: Question
    studentAnswer: string | null
    isCorrect: boolean
  }>,
  source: 'EXCEL' | 'MANUAL',
  remark: string,
): GradingBatch {
  const existingVersions = db.batches
    .filter((batch) => batch.homeworkId === homework.id)
    .map((batch) => batch.versionNo)
  const versionNo = (existingVersions.length ? Math.max(...existingVersions) : 0) + 1
  const batch: GradingBatch = {
    id: nextId(db, 'batch'),
    homeworkId: homework.id,
    versionNo,
    source,
    importedBy: readSession()?.user.id ?? 2,
    totalRows: rows.length,
    validRows: rows.length,
    remark,
    createdAt: nowIso(),
  }
  db.batches.push(batch)
  for (const row of rows) {
    db.records.push({
      id: nextId(db, 'record'),
      batchId: batch.id,
      homeworkId: homework.id,
      studentId: row.student.id,
      studentNo: row.student.studentNo,
      studentName: row.student.name,
      questionId: row.question.id,
      seqNo: row.question.seqNo,
      studentAnswer: row.studentAnswer,
      isCorrect: row.isCorrect,
      score: row.isCorrect ? row.question.score : 0,
      teacherNote: null,
      errorTag: row.isCorrect ? null : '待归类',
      createdAt: nowIso(),
    })
  }
  homework.currentGradingBatchId = batch.id
  homework.currentGradingBatchVersion = versionNo
  homework.updatedAt = nowIso()
  return batch
}

async function handleMockRequest<T>(
  method: string,
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  await wait()
  const url = new URL(path, 'http://mock.local')
  const segments = url.pathname.split('/').filter(Boolean)
  const p = asPayload(options)
  const upperMethod = method.toUpperCase()

  // 认证 ---------------------------------------------------------------
  if (segments[0] === 'auth' && segments[1] === 'session') {
    if (upperMethod === 'POST') {
      const payload = p as unknown as LoginRequest
      const username = asString(payload.username)
      const password = asString(payload.password)
      const portal = asString(payload.portal)
      if (!username || !password || !portal) {
        fail(400, 'VALIDATION_FAILED', '请输入账号、密码并选择登录入口')
      }
      const attempt = sessionAttempts.get(username) ?? { failures: 0, lockedUntil: 0 }
      if (attempt.lockedUntil > Date.now()) {
        const retryAfterSeconds = Math.ceil((attempt.lockedUntil - Date.now()) / 1000)
        fail(423, 'AUTH_ACCOUNT_LOCKED', `账号已临时锁定，请 ${retryAfterSeconds} 秒后再试`, {
          retryAfterSeconds,
        })
      }
      const user = db.users.find((item) => item.username === username)
      const roleOk = Boolean(user && portal && user.roles.includes(portal as MockUser['roles'][number]))
      if (!user || user.password !== password || !roleOk || user.status !== 'ACTIVE') {
        attempt.failures += 1
        if (attempt.failures >= MAX_FAILURES) {
          attempt.lockedUntil = Date.now() + LOCK_MS
          sessionAttempts.set(username, attempt)
          fail(423, 'AUTH_ACCOUNT_LOCKED', '连续失败次数过多，账号已临时锁定 60 秒', {
            retryAfterSeconds: 60,
          })
        }
        sessionAttempts.set(username, attempt)
        fail(401, 'AUTH_INVALID_CREDENTIALS', '账号或密码错误', {
          attemptsRemaining: Math.max(0, MAX_FAILURES - attempt.failures),
        })
      }
      sessionAttempts.delete(username)
      const result: LoginResult = {
        user: {
          id: user.id,
          username: user.username,
          displayName: user.displayName,
          status: user.status,
          roles: user.roles,
          studentNo: user.studentNo,
          className: user.className,
          classNames: user.classNames,
        },
        authMode: 'SESSION',
        accessToken: null,
        expiresAt: new Date(Date.now() + 8 * 60 * 60 * 1000).toISOString(),
      }
      return result as T
    }
    if (upperMethod === 'DELETE') {
      return undefined as T
    }
  }

  if (segments[0] === 'me' && upperMethod === 'GET') {
    const user = requireUser()
    return {
      id: user.id,
      username: user.username,
      displayName: user.displayName,
      status: user.status,
      roles: user.roles,
      studentNo: user.studentNo,
      className: user.className,
      classNames: user.classNames,
    } as T
  }

  // 开发辅助（仅 Mock 模式） -------------------------------------------
  if (segments[0] === 'mock' && upperMethod === 'POST') {
    if (segments[1] === 'reset') {
      resetDb()
      return undefined as T
    }
    if (segments[1] === 'ai-status') {
      db.aiOnline = Boolean(p.online)
      return undefined as T
    }
  }

  // 教师端 -------------------------------------------------------------
  if (segments[0] === 'teacher') {
    const user = requireRole('TEACHER')

    if (segments[1] === 'classes' && segments.length === 2 && upperMethod === 'GET') {
      const classIds = teacherClassIds(user)
      const result = db.classes
        .filter((item) => classIds.includes(item.id))
        .map((item) => ({
          ...item,
          studentCount: db.students.filter(
            (student) => student.classId === item.id && student.status !== 'DISABLED',
          ).length,
          activeHomeworkCount: db.homeworks.filter(
            (homework) => homework.classId === item.id && homework.status === 'PUBLISHED',
          ).length,
        }))
      return result as T
    }

    if (segments[1] === 'classes' && segments.length === 2 && upperMethod === 'POST') {
      const name = asString(p.name)
      if (!name) fail(400, 'VALIDATION_FAILED', '请填写班级名称')
      const classInfo: ClassSummary = {
        id: nextId(db, 'class'),
        name,
        grade: asString(p.grade) || '初一',
        subject: asString(p.subject) || '数学',
        studentCount: 0,
        activeHomeworkCount: 0,
      }
      db.classes.push(classInfo)
      db.teacherClasses.push({ teacherUserId: user.id, classId: classInfo.id })
      return classInfo as T
    }

    if (segments[1] === 'classes' && segments[2] && segments.length === 3 && upperMethod === 'PUT') {
      const classInfo = requireTeacherClass(user, asNumber(segments[2]))
      if (typeof p.name === 'string') {
        const name = p.name.trim()
        if (!name) fail(400, 'VALIDATION_FAILED', '班级名称不能为空')
        classInfo.name = name
      }
      if (typeof p.grade === 'string') classInfo.grade = p.grade.trim()
      if (typeof p.subject === 'string') classInfo.subject = p.subject.trim()
      return {
        ...classInfo,
        studentCount: db.students.filter(
          (student) => student.classId === classInfo.id && student.status !== 'DISABLED',
        ).length,
        activeHomeworkCount: db.homeworks.filter(
          (homework) => homework.classId === classInfo.id && homework.status === 'PUBLISHED',
        ).length,
      } as T
    }

    if (segments[1] === 'classes' && segments[2] && segments[3] === 'students' && segments.length === 5 && segments[4] === 'import' && upperMethod === 'POST') {
      const classId = asNumber(segments[2])
      requireTeacherClass(user, classId)
      const rows = Array.isArray(p.rows) ? p.rows : []
      if (!rows.length) fail(400, 'VALIDATION_FAILED', '没有可导入的学生')
      const seen = new Set<string>()
      for (const item of rows) {
        const row = item as Record<string, unknown>
        const studentNo = asString(row.studentNo)
        const name = asString(row.name)
        if (!studentNo || !name) fail(400, 'VALIDATION_FAILED', '每行必须包含学号和姓名')
        if (seen.has(studentNo) || db.students.some((student) => student.studentNo === studentNo)) {
          fail(400, 'VALIDATION_FAILED', `学号已存在：${studentNo}`)
        }
        seen.add(studentNo)
      }
      const created = rows.map((item) => {
        const row = item as Record<string, unknown>
        const student: MockStudent = {
          id: nextId(db, 'student'),
          studentNo: asString(row.studentNo),
          name: asString(row.name),
          status: 'ACTIVE',
          classId,
        }
        db.students.push(student)
        return student
      })
      return { created: created.length } as T
    }

    if (segments[1] === 'classes' && segments[2] && segments[3] === 'students' && segments.length === 4 && upperMethod === 'POST') {
      const classId = asNumber(segments[2])
      requireTeacherClass(user, classId)
      const studentNo = asString(p.studentNo)
      const name = asString(p.name)
      if (!studentNo || !name) fail(400, 'VALIDATION_FAILED', '请填写学号与姓名')
      if (db.students.some((student) => student.studentNo === studentNo)) {
        fail(400, 'VALIDATION_FAILED', `学号已存在：${studentNo}`)
      }
      const student: MockStudent = {
        id: nextId(db, 'student'),
        studentNo,
        name,
        status: 'ACTIVE',
        classId,
      }
      db.students.push(student)
      return { id: student.id, studentNo: student.studentNo, name: student.name, status: student.status } as T
    }

    if (segments[1] === 'classes' && segments[2] && segments[3] === 'students' && segments.length === 5 && upperMethod === 'PUT') {
      const classId = asNumber(segments[2])
      requireTeacherClass(user, classId)
      const student = db.students.find((item) => item.id === asNumber(segments[4]) && item.classId === classId)
      if (!student) fail(404, 'NOT_FOUND', '学生不在该班级')
      if (typeof p.name === 'string') {
        const name = p.name.trim()
        if (!name) fail(400, 'VALIDATION_FAILED', '学生姓名不能为空')
        student.name = name
      }
      if (p.status === 'ACTIVE' || p.status === 'DISABLED') student.status = p.status
      return { id: student.id, studentNo: student.studentNo, name: student.name, status: student.status } as T
    }

    if (segments[1] === 'classes' && segments[3] === 'students' && upperMethod === 'GET') {
      const classId = asNumber(segments[2])
      requireTeacherClass(user, classId)
      const keyword = asString(param(options, 'keyword')).toLowerCase()
      const includeDisabled = String(param(options, 'includeDisabled')) === 'true'
      const students = db.students
        .filter(
          (student) =>
            student.classId === classId && (includeDisabled || student.status !== 'DISABLED'),
        )
        .filter(
          (student) =>
            !keyword ||
            student.name.toLowerCase().includes(keyword) ||
            student.studentNo.toLowerCase().includes(keyword),
        )
        .map<StudentSummary>((student) => ({
          id: student.id,
          studentNo: student.studentNo,
          name: student.name,
          status: student.status,
        }))
      return pageOf(students, options, 50) as T
    }

    if (segments[1] === 'templates' && segments[2] === 'approved' && upperMethod === 'GET') {
      return db.templates
        .filter((template) => template.status === 'APPROVED')
        .sort((a, b) => (b.reviewedAt ?? '').localeCompare(a.reviewedAt ?? ''))
        .map((template) => ({ ...template })) as T
    }

    if (segments[1] === 'templates' && segments[2] === 'mine' && upperMethod === 'GET') {
      return db.templates
        .filter((template) => template.createdBy === user.id)
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
        .map((template) => ({ ...template })) as T
    }

    if (segments[1] === 'templates' && segments.length === 2 && upperMethod === 'POST') {
      const title = asString(p.title)
      const content = asString(p.content)
      const sections = Array.isArray(p.sections)
        ? p.sections.map((item) => asString(item)).filter(Boolean)
        : []
      if (!title || !content || !sections.length) {
        fail(400, 'VALIDATION_FAILED', '请填写模板标题、说明和至少一个结构槽位')
      }
      const template: PublicTemplate = {
        id: nextId(db, 'template'),
        title,
        category: null,
        structure: { sections },
        content,
        status: 'PENDING',
        createdBy: user.id,
        createdByName: user.displayName,
        reviewedBy: null,
        reviewNote: null,
        reviewedAt: null,
        createdAt: nowIso(),
        updatedAt: nowIso(),
      }
      db.templates.push(template)
      addOperationLog(user, 'SUBMIT_TEMPLATE', 'TEMPLATE', template.id, `提交公共模板「${title}」`)
      return template as T
    }

    if (segments[1] === 'knowledge-points' && upperMethod === 'GET') {
      const list = db.knowledgePoints
        .filter((kp) => kp.status === 'ACTIVE')
        .map((kp) => ({ ...kp }))
      return list as T
    }

    if (segments[1] === 'homeworks' && segments.length === 2) {
      if (upperMethod === 'GET') {
        const classIds = teacherClassIds(user)
        const status = asString(param(options, 'status'))
        const keyword = asString(param(options, 'keyword')).toLowerCase()
        const items = db.homeworks
          .filter((homework) => classIds.includes(homework.classId))
          .filter((homework) => !status || homework.status === status)
          .filter(
            (homework) =>
              !keyword ||
              homework.title.toLowerCase().includes(keyword) ||
              homework.className.toLowerCase().includes(keyword),
          )
          .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
          .map(toHomeworkSummary)
        return pageOf(items, options) as T
      }
      if (upperMethod === 'POST') {
        const classId = asNumber(p.classId)
        requireTeacherClass(user, classId)
        const title = asString(p.title)
        if (!title) fail(400, 'VALIDATION_FAILED', '请填写作业名称')
        const classInfo = db.classes.find((item) => item.id === classId)
        const homework: HomeworkDetail = {
          id: nextId(db, 'homework'),
          classId,
          className: classInfo?.name ?? '',
          title,
          description: asString(p.description) || null,
          status: 'DRAFT',
          dueAt: typeof p.dueAt === 'string' ? p.dueAt : null,
          publishedAt: null,
          questionCount: 0,
          studentCount: db.students.filter(
            (student) => student.classId === classId && student.status !== 'DISABLED',
          ).length,
          currentGradingBatchId: null,
          currentGradingBatchVersion: null,
          handoutId: null,
          handoutStatus: null,
          createdAt: nowIso(),
          updatedAt: nowIso(),
          version: 0,
          questions: [],
        }
        db.homeworks.push(homework)
        addOperationLog(user, 'CREATE_HOMEWORK', 'HOMEWORK', homework.id, `创建作业「${title}」`)
        return homework as T
      }
    }

    if (segments[1] === 'homeworks' && segments[2]) {
      const homeworkId = asNumber(segments[2])
      const homework = requireTeacherHomework(user, homeworkId)
      const action = segments[3]

      if (!action && upperMethod === 'GET') return hydrateHomework(homework) as T

      if (!action && upperMethod === 'PUT') {
        if (homework.status === 'ARCHIVED') fail(409, 'CONFLICT_STATE', '已归档作业不可编辑')
        if (asNumber(p.expectedVersion, -1) !== homework.version) {
          fail(409, 'VERSION_CONFLICT', '作业已被更新，请刷新后重试')
        }
        if (typeof p.title === 'string') {
          if (!p.title.trim()) fail(400, 'VALIDATION_FAILED', '作业名称不能为空')
          homework.title = p.title.trim()
        }
        if (p.description !== undefined) homework.description = asString(p.description) || null
        if (p.dueAt !== undefined) homework.dueAt = typeof p.dueAt === 'string' ? p.dueAt : null
        homework.version += 1
        homework.updatedAt = nowIso()
        addOperationLog(user, 'UPDATE_HOMEWORK', 'HOMEWORK', homework.id, '更新作业信息')
        return hydrateHomework(homework) as T
      }

      if (action === 'publish' && upperMethod === 'POST') {
        if (asNumber(p.expectedVersion, -1) !== homework.version) {
          fail(409, 'VERSION_CONFLICT', '作业已被更新，请刷新后重试')
        }
        if (homework.status !== 'DRAFT') fail(409, 'CONFLICT_STATE', '仅草稿作业可以发布')
        if (!homework.questions.length) fail(409, 'HOMEWORK_NOT_READY', '至少添加 1 道题后才能发布')
        homework.status = 'PUBLISHED'
        homework.publishedAt = nowIso()
        homework.version += 1
        homework.updatedAt = nowIso()
        addOperationLog(user, 'PUBLISH_HOMEWORK', 'HOMEWORK', homework.id, '发布作业', {
          questionCount: homework.questions.length,
        })
        return hydrateHomework(homework) as T
      }

      if (action === 'archive' && upperMethod === 'POST') {
        if (asNumber(p.expectedVersion, -1) !== homework.version) {
          fail(409, 'VERSION_CONFLICT', '作业已被更新，请刷新后重试')
        }
        if (homework.status !== 'PUBLISHED') fail(409, 'CONFLICT_STATE', '仅已发布作业可以归档')
        homework.status = 'ARCHIVED'
        homework.version += 1
        homework.updatedAt = nowIso()
        addOperationLog(user, 'ARCHIVE_HOMEWORK', 'HOMEWORK', homework.id, '归档作业')
        return hydrateHomework(homework) as T
      }

      if (action === 'questions' && upperMethod === 'POST') {
        if (homework.status === 'ARCHIVED') fail(409, 'CONFLICT_STATE', '已归档作业不可编辑')
        const seqNo = Math.floor(asNumber(p.seqNo))
        const questionType = asString(p.questionType)
        const stem = asString(p.stem)
        const score = asNumber(p.score)
        const kpIds = Array.isArray(p.knowledgePointIds)
          ? p.knowledgePointIds.map((id) => asNumber(id)).filter(Boolean)
          : []
        if (!seqNo || !stem || !score || !kpIds.length) {
          fail(400, 'VALIDATION_FAILED', '请完整填写题号、题干、分值与知识点')
        }
        if (!['SINGLE_CHOICE', 'TRUE_FALSE'].includes(questionType)) {
          fail(400, 'VALIDATION_FAILED', '第一版仅支持单选题与判断题')
        }
        if (homework.questions.some((question) => question.seqNo === seqNo)) {
          fail(409, 'CONFLICT_STATE', `第 ${seqNo} 题已存在`)
        }
        const options = Array.isArray(p.options)
          ? (p.options as Array<{ key: string; text: string }>)
          : null
        if (questionType === 'SINGLE_CHOICE' && (!options || options.length < 2)) {
          fail(400, 'VALIDATION_FAILED', '单选题至少需要 2 个选项')
        }
        const question: Question = {
          id: nextId(db, 'question'),
          homeworkId: homework.id,
          seqNo,
          questionType: questionType as Question['questionType'],
          stem,
          options: questionType === 'TRUE_FALSE' ? null : options,
          standardAnswer: asString(p.standardAnswer) || null,
          teacherAnalysis: asString(p.teacherAnalysis) || null,
          score,
          knowledgePointIds: kpIds,
          revisionNo: 1,
        }
        db.questions.push(question)
        homework.questions.push(question)
        homework.version += 1
        homework.updatedAt = nowIso()
        addOperationLog(user, 'CREATE_QUESTION', 'QUESTION', question.id, `新增第 ${question.seqNo} 题`)
        return question as T
      }

      if (action === 'grading-batches' && upperMethod === 'GET') {
        const batches = db.batches
          .filter((batch) => batch.homeworkId === homework.id)
          .sort((a, b) => b.versionNo - a.versionNo)
        return batches as T
      }

      if (action === 'grading' && segments[4] === 'import' && upperMethod === 'POST') {
        if (homework.status !== 'PUBLISHED') {
          fail(409, 'CONFLICT_STATE', '作业发布后才能导入批改数据')
        }
        const students = db.students.filter(
          (student) => student.classId === homework.classId && student.status !== 'DISABLED',
        )
        const questions = homework.questions
        const rowErrors: ImportRowError[] = []
        const inputRows = Array.isArray(p.rows) ? p.rows : []
        const rows = inputRows
          .map((raw, index) => {
            const row = raw as Partial<ImportRow>
            const student = students.find(
              (candidate) => candidate.studentNo === asString(row.studentNo),
            )
            const question = questions.find(
              (candidate) => candidate.seqNo === asNumber(row.questionSeq),
            )
            if (!student) {
              rowErrors.push({
                rowNumber: index + 1,
                field: 'studentNo',
                message: `学号 ${asString(row.studentNo) || '（空）'} 不在本班名单中`,
                rejectedValue: asString(row.studentNo),
              })
            }
            if (!question) {
              rowErrors.push({
                rowNumber: index + 1,
                field: 'questionSeq',
                message: `题号 ${asNumber(row.questionSeq) || '（空）'} 不存在`,
                rejectedValue: String(row.questionSeq ?? ''),
              })
            }
            if (typeof row.isCorrect !== 'boolean') {
              rowErrors.push({
                rowNumber: index + 1,
                field: 'isCorrect',
                message: '是否正确必须为 true / false',
              })
            }
            if (!student || !question || typeof row.isCorrect !== 'boolean') return null
            return {
              student,
              question,
              studentAnswer: asString(row.studentAnswer) || null,
              isCorrect: row.isCorrect,
            }
          })
          .filter((row): row is NonNullable<typeof row> => row !== null)

        const keys = new Set<string>()
        for (const row of rows) {
          const key = `${row.student.studentNo}-${row.question.seqNo}`
          if (keys.has(key)) {
            rowErrors.push({
              rowNumber: 0,
              field: 'studentNo+questionSeq',
              message: `重复行：${row.student.studentNo} 第 ${row.question.seqNo} 题`,
            })
          }
          keys.add(key)
        }
        for (const student of students) {
          for (const question of questions) {
            const key = `${student.studentNo}-${question.seqNo}`
            if (!keys.has(key)) {
              rowErrors.push({
                rowNumber: 0,
                field: 'coverage',
                message: `缺少记录：${student.studentNo} 第 ${question.seqNo} 题`,
              })
            }
          }
        }
        if (!rows.length) {
          rowErrors.push({ rowNumber: 0, message: '未读取到可导入的批改行', field: 'file' })
        }
        if (rowErrors.length) {
          fail(422, 'GRADING_IMPORT_INVALID_ROWS', '导入文件存在错误，整批未写入', {
            rowErrors: rowErrors.slice(0, 30),
          })
        }
        const remark = asString(p.remark) || `导入 ${asString(p.fileName) || '批改文件'}`
        const batch = addBatchRecords(homework, rows, 'EXCEL', remark)
        addOperationLog(user, 'IMPORT_GRADING', 'HOMEWORK', homework.id, `导入批改批次 V${batch.versionNo}`, {
          batchId: batch.id,
          rows: rows.length,
        })
        return {
          batch,
          currentBatchId: batch.id,
          statisticsCalculated: true,
        } as T
      }

      if (action === 'grading' && segments[4] === 'manual' && upperMethod === 'POST') {
        if (homework.status !== 'PUBLISHED') {
          fail(409, 'CONFLICT_STATE', '作业发布后才能录入批改数据')
        }
        const students = db.students.filter(
          (student) => student.classId === homework.classId && student.status !== 'DISABLED',
        )
        const questions = homework.questions
        const inputRows = Array.isArray(p.rows) ? p.rows : []
        const rows = inputRows
          .map((raw) => {
            const row = raw as Record<string, unknown>
            const student = students.find((item) => item.studentNo === asString(row.studentNo))
            const question = questions.find((item) => item.seqNo === asNumber(row.seqNo))
            if (!student || !question || typeof row.isCorrect !== 'boolean') return null
            return {
              student,
              question,
              studentAnswer: asString(row.studentAnswer) || null,
              isCorrect: row.isCorrect,
            }
          })
          .filter((row): row is NonNullable<typeof row> => row !== null)
        const keys = new Set(rows.map((row) => `${row.student.studentNo}-${row.question.seqNo}`))
        const missing = students.some((student) =>
          questions.some((question) => !keys.has(`${student.studentNo}-${question.seqNo}`)),
        )
        if (!rows.length || missing) {
          fail(422, 'GRADING_IMPORT_INVALID_ROWS', '手工录入必须覆盖本班全部学生与题目', {
            rowErrors: [{ rowNumber: 0, field: 'coverage', message: '记录不完整，请刷新后重试' }],
          })
        }
        const batch = addBatchRecords(homework, rows, 'MANUAL', asString(p.remark) || '教师手工录入批改')
        addOperationLog(user, 'IMPORT_GRADING', 'HOMEWORK', homework.id, `手工录入批改批次 V${batch.versionNo}`, {
          batchId: batch.id,
          rows: rows.length,
          source: 'MANUAL',
        })
        return { batch, currentBatchId: batch.id, statisticsCalculated: true } as T
      }

      if (action === 'grading-records' && upperMethod === 'GET') {
        const batchId = asNumber(param(options, 'batchId'), homework.currentGradingBatchId ?? 0)
        const keyword = asString(param(options, 'keyword')).toLowerCase()
        const seqNo = asNumber(param(options, 'seqNo'), 0)
        const correctParam = param(options, 'isCorrect')
        const records = db.records
          .filter((record) => record.homeworkId === homework.id && record.batchId === batchId)
          .filter((record) => !keyword || record.studentName.toLowerCase().includes(keyword) || record.studentNo.toLowerCase().includes(keyword))
          .filter((record) => !seqNo || record.seqNo === seqNo)
          .filter((record) => {
            if (correctParam === undefined || correctParam === null || correctParam === '') return true
            return record.isCorrect === (String(correctParam) === 'true')
          })
          .sort((a, b) => a.studentId - b.studentId || a.seqNo - b.seqNo)
        return pageOf(records, options, 20) as T
      }

      if (action === 'statistics' && upperMethod === 'GET') {
        return computeStatistics(homework) as T
      }

      if (action === 'handout' && upperMethod === 'GET') {
        const handout = handoutOfHomework(homework.id)
        if (!handout) fail(404, 'NOT_FOUND', '尚未生成该作业的讲评')
        return decorateHandout(homework, handout) as T
      }

      if (action === 'handouts' && segments[4] === 'generate' && upperMethod === 'POST') {
        if (homework.status === 'ARCHIVED') fail(409, 'CONFLICT_STATE', '已归档作业不可生成讲评')
        if (!homework.currentGradingBatchId) {
          fail(409, 'GRADING_NOT_READY', '请先导入批改数据，再生成讲评草稿')
        }
        const existing = handoutOfHomework(homework.id)
        if (existing) fail(409, 'CONFLICT_STATE', '该作业已生成讲评，可在审核台继续处理')
        const stats = computeStatistics(homework)
        const selectedTemplate = asNumber(p.templateId)
          ? db.templates.find(
              (template) => template.id === asNumber(p.templateId) && template.status === 'APPROVED',
            )
          : null
        if (asNumber(p.templateId) && !selectedTemplate) {
          fail(400, 'VALIDATION_FAILED', '只能引用已通过的公共模板')
        }
        const selectedIds = Array.isArray(p.questionIds)
          ? (p.questionIds as number[])
          : homework.questions.map((question) => question.id)
        const items: MockHandoutItem[] = homework.questions
          .filter((question) => selectedIds.includes(question.id))
          .sort((a, b) => a.seqNo - b.seqNo)
          .map((question, index) => {
            const stat = stats.questions.find((entry) => entry.questionId === question.id)
            const hasEvidence = Boolean(question.standardAnswer || question.teacherAnalysis)
            const aiAvailable = db.aiOnline
            const evidence = makeEvidence(question, stat?.wrongCount ?? 0, stat?.totalCount ?? 0)
            if (selectedTemplate) {
              evidence.push({
                evidenceType: 'TEMPLATE',
                refId: selectedTemplate.id,
                snapshotText: `${selectedTemplate.title}\n${selectedTemplate.content ?? ''}`,
              })
            }
            const content = hasEvidence && aiAvailable
              ? makeAiCommentary(question, stat?.wrongCount ?? 0, stat?.totalCount ?? 0)
              : null
            const draftStatus: AiDraft['status'] = !hasEvidence
              ? 'INSUFFICIENT_EVIDENCE'
              : aiAvailable
                ? 'GENERATED'
                : 'FAILED'
            const draft = buildTeacherDraft(
              question,
              content ?? '',
              evidence,
              draftStatus,
              aiAvailable ? null : 'AUTH_PROVIDER_UNAVAILABLE',
            )
            return {
              id: nextId(db, 'item'),
              handoutId: 0,
              questionId: question.id,
              seqNo: question.seqNo,
              questionType: question.questionType,
              stem: question.stem,
              options: question.options,
              standardAnswer: question.standardAnswer,
              teacherAnalysis: question.teacherAnalysis,
              score: question.score,
              source: hasEvidence ? 'AI' : null,
              status: !hasEvidence ? 'INSUFFICIENT_EVIDENCE' : aiAvailable ? 'PENDING_REVIEW' : 'AI_FAILED_MANUAL',
              sortNo: index + 1,
              currentVersionId: null,
              content,
              versionCount: 0,
              confirmedBy: null,
              confirmedAt: null,
              allowedActions: ['CONFIRM', 'EXCLUDE', 'REGENERATE'],
              evidence,
              draft,
              versions: [],
              excludeReason: null,
              wrongCount: stat?.wrongCount ?? 0,
              errorRate: stat?.errorRate ?? 0,
            }
          })
        const handout: MockHandout = {
          id: nextId(db, 'handout'),
          homeworkId: homework.id,
          homeworkTitle: homework.title,
          status: 'UNPUBLISHED',
          itemCount: items.length,
          readyCount: 0,
          pendingReviewCount: 0,
          blockedCount: 0,
          excludedCount: 0,
          publishable: false,
          blockingReasons: [],
          publishedAt: null,
          publishedBy: null,
          withdrawnAt: null,
          version: 0,
          createdAt: nowIso(),
          updatedAt: nowIso(),
          items,
          logs: [],
        }
        for (const item of handout.items) item.handoutId = handout.id
        refreshHandoutState(handout)
        db.handouts.push(handout)
        syncHomeworkHandout(homework, handout)
        addOperationLog(user, 'GENERATE_AI', 'HOMEWORK', homework.id, `生成 ${items.length} 条讲评草稿`, {
          online: db.aiOnline,
        })
        return decorateHandout(homework, handout) as T
      }

      if (action === 'corrections' && upperMethod === 'GET') {
        const keyword = asString(param(options, 'keyword')).toLowerCase()
        const corrections = db.corrections
          .filter((correction) => correction.homeworkId === homework.id)
          .filter(
            (correction) =>
              !keyword ||
              correction.studentName.toLowerCase().includes(keyword) ||
              correction.studentNo.toLowerCase().includes(keyword),
          )
          .sort((a, b) => b.submittedAt.localeCompare(a.submittedAt))
        return pageOf(corrections, options) as T
      }

      if (action === 'correction-progress' && segments[4] === 'by-question' && upperMethod === 'GET') {
        const handout = handoutOfHomework(homework.id)
        if (!handout || handout.status !== 'PUBLISHED') return pageOf([], options) as T
        const records = recordsOfCurrentBatch(homework)
        const items = handout.items
          .filter((item) => item.status === 'READY')
          .map((item) => {
            const wrongStudents = new Set(
              records
                .filter((record) => !record.isCorrect && record.questionId === item.questionId)
                .map((record) => record.studentId),
            )
            if (!wrongStudents.size) return null
            const correctedStudents = new Set(
              db.corrections
                .filter(
                  (correction) =>
                    correction.homeworkId === homework.id &&
                    correction.handoutItemId === item.id,
                )
                .map((correction) => correction.studentId),
            )
            const correctedCount = [...wrongStudents].filter((id) => correctedStudents.has(id)).length
            const lastSubmittedAt = db.corrections
              .filter(
                (correction) =>
                  correction.homeworkId === homework.id &&
                  correction.handoutItemId === item.id,
              )
              .map((correction) => correction.submittedAt)
              .sort()
              .reverse()[0] ?? null
            return {
              questionId: item.questionId,
              seqNo: item.seqNo,
              stem: item.stem,
              wrongCount: wrongStudents.size,
              correctedCount,
              pendingCount: Math.max(0, wrongStudents.size - correctedCount),
              completionRate: round2((correctedCount / wrongStudents.size) * 100),
              lastSubmittedAt,
            }
          })
          .filter((item): item is NonNullable<typeof item> => item !== null)
          .sort((a, b) => b.pendingCount - a.pendingCount || a.seqNo - b.seqNo)
        return pageOf(items, options, 50) as T
      }

      if (action === 'correction-progress' && upperMethod === 'GET') {
        const handout = handoutOfHomework(homework.id)
        const keyword = asString(param(options, 'keyword')).toLowerCase()
        const items = !handout || handout.status === 'UNPUBLISHED'
          ? []
          : db.students
              .filter((student) => student.classId === homework.classId)
              .map<CorrectionProgressItem>((student) => {
                const wrongItems = visibleItemsForStudent(handout, student.id)
                const ownCorrections = db.corrections.filter(
                  (correction) =>
                    correction.homeworkId === homework.id && correction.studentId === student.id,
                )
                const correctedItemIds = new Set(ownCorrections.map((correction) => correction.handoutItemId))
                const correctedCount = wrongItems.filter((item) => correctedItemIds.has(item.id)).length
                const pendingCount = Math.max(0, wrongItems.length - correctedCount)
                return {
                  studentId: student.id,
                  studentNo: student.studentNo,
                  studentName: student.name,
                  wrongCount: wrongItems.length,
                  correctedCount,
                  pendingCount,
                  completionRate: wrongItems.length
                    ? round2((correctedCount / wrongItems.length) * 100)
                    : 100,
                  lastSubmittedAt: ownCorrections.length
                    ? ownCorrections
                        .map((correction) => correction.submittedAt)
                        .sort()
                        .reverse()[0]
                    : null,
                }
              })
              .filter((item) => item.wrongCount > 0)
              .filter(
                (item) =>
                  !keyword ||
                  item.studentName.toLowerCase().includes(keyword) ||
                  item.studentNo.toLowerCase().includes(keyword),
              )
              .sort((a, b) => b.pendingCount - a.pendingCount || a.studentNo.localeCompare(b.studentNo))
        return pageOf(items, options, 50) as T
      }
    }

    if (segments[1] === 'questions' && segments[2]) {
      const questionId = asNumber(segments[2])
      const question = db.questions.find((item) => item.id === questionId)
      if (!question) fail(404, 'NOT_FOUND', '题目不存在')
      const homework = requireTeacherHomework(user, question.homeworkId)
      if (upperMethod === 'PUT') {
        if (homework.status === 'ARCHIVED') fail(409, 'CONFLICT_STATE', '已归档作业不可编辑')
        if (asNumber(p.expectedRevisionNo, -1) !== question.revisionNo) {
          fail(409, 'VERSION_CONFLICT', '题目已被更新，请刷新后重试')
        }
        const stem = asString(p.stem)
        const score = asNumber(p.score)
        const kpIds = Array.isArray(p.knowledgePointIds)
          ? p.knowledgePointIds.map((id) => asNumber(id)).filter(Boolean)
          : []
        if (!stem || !score || !kpIds.length) {
          fail(400, 'VALIDATION_FAILED', '请完整填写题干、分值与知识点')
        }
        question.questionType = asString(p.questionType) === 'TRUE_FALSE' ? 'TRUE_FALSE' : 'SINGLE_CHOICE'
        question.stem = stem
        question.options = question.questionType === 'TRUE_FALSE'
          ? null
          : Array.isArray(p.options)
            ? (p.options as Array<{ key: string; text: string }>)
            : question.options
        question.standardAnswer = asString(p.standardAnswer) || null
        question.teacherAnalysis = asString(p.teacherAnalysis) || null
        question.score = score
        question.knowledgePointIds = kpIds
        question.revisionNo += 1
        homework.version += 1
        homework.updatedAt = nowIso()
        addOperationLog(user, 'UPDATE_QUESTION', 'QUESTION', question.id, `修订第 ${question.seqNo} 题（R${question.revisionNo}）`)
        return question as T
      }
      if (upperMethod === 'DELETE') {
        if (homework.status !== 'DRAFT' || homework.currentGradingBatchId) {
          fail(409, 'CONFLICT_STATE', '已有批改事实的作业不能删除题目')
        }
        homework.questions = homework.questions.filter((item) => item.id !== question.id)
        homework.questions
          .sort((a, b) => a.seqNo - b.seqNo)
          .forEach((item, index) => {
            item.seqNo = index + 1
          })
        const index = db.questions.findIndex((item) => item.id === question.id)
        if (index >= 0) db.questions.splice(index, 1)
        homework.version += 1
        homework.updatedAt = nowIso()
        addOperationLog(user, 'DELETE_QUESTION', 'QUESTION', question.id, `删除第 ${question.seqNo} 题`)
        return undefined as T
      }
    }

    if (segments[1] === 'grading-records' && segments[2] && upperMethod === 'PUT') {
      const recordId = asNumber(segments[2])
      const record = db.records.find((item) => item.id === recordId)
      if (!record) fail(404, 'NOT_FOUND', '批改记录不存在')
      const homework = requireTeacherHomework(user, record.homeworkId)
      if (record.batchId !== homework.currentGradingBatchId) {
        fail(409, 'VERSION_CONFLICT', '该记录已不是当前批次，请刷新后再编辑')
      }
      if (asNumber(p.expectedBatchId, -1) !== record.batchId) {
        fail(409, 'VERSION_CONFLICT', '批改批次已变化，请刷新后重试')
      }
      const previousRecord: GradingRecord = { ...record }
      const newBatchRows = recordsOfCurrentBatch(homework).map((item) => {
        const student = db.students.find((candidate) => candidate.id === item.studentId)
        const question = db.questions.find((candidate) => candidate.id === item.questionId)
        if (!student || !question) return null
        return {
          student,
          question,
          studentAnswer: item.id === recordId
            ? asString(p.studentAnswer) || null
            : item.studentAnswer,
          isCorrect: item.id === recordId
            ? Boolean(p.isCorrect)
            : item.isCorrect,
        }
      }).filter((row): row is NonNullable<typeof row> => row !== null)
      const batch = addBatchRecords(homework, newBatchRows, 'MANUAL', asString(p.remark) || '人工修订单条批改记录')
      const updated = db.records.find(
        (item) => item.batchId === batch.id && item.studentId === previousRecord.studentId && item.questionId === previousRecord.questionId,
      )
      if (!updated) fail(500, 'INTERNAL_ERROR', '修订结果写入失败')
      if (asString(p.teacherNote)) updated.teacherNote = asString(p.teacherNote)
      addOperationLog(user, 'REVISE_GRADING', 'GRADING_RECORD', updated.id, `人工修订 ${updated.studentName} 第 ${updated.seqNo} 题`, {
        fromBatchId: previousRecord.batchId,
        toBatchId: batch.id,
      })
      return { batch, record: updated, statisticsCalculated: true } as T
    }

    if (segments[1] === 'handout-items' && segments[2] && segments[3] === 'versions' && upperMethod === 'GET') {
      const { handout, item } = itemById(asNumber(segments[2]))
      requireTeacherHomework(user, handout.homeworkId)
      return [...item.versions].sort((a, b) => b.versionNo - a.versionNo) as T
    }

    if (segments[1] === 'handout-items' && segments[2] && upperMethod === 'POST') {
      const itemId = asNumber(segments[2])
      const { handout, item } = itemById(itemId)
      const homework = requireTeacherHomework(user, handout.homeworkId)
      const action = segments[3]
      canEditHandout(handout)
      if (action === 'save') {
        const nextContent = asString(p.content)
        if (!nextContent) fail(400, 'VALIDATION_FAILED', '请填写讲评正文后再保存')
        if (nextContent.length > 2000) fail(400, 'VALIDATION_FAILED', '讲评正文最多 2000 字')
        const versionChanged = nextContent !== item.content || !item.currentVersionId
        if (versionChanged) {
          const version = {
            id: nextId(db, 'version'),
            versionNo: item.versions.length + 1,
            content: nextContent,
            changeNote: asString(p.changeNote) || '保存讲评草稿',
            createdAt: nowIso(),
            createdBy: user.id,
          }
          item.versions.push(version)
          item.currentVersionId = version.id
          item.versionCount = item.versions.length
        }
        item.content = nextContent
        item.status = 'PENDING_REVIEW'
        item.confirmedBy = null
        item.confirmedAt = null
        item.source = item.source ?? 'MANUAL'
        item.allowedActions = ['CONFIRM', 'EXCLUDE', 'REGENERATE']
        refreshHandoutState(handout)
        addOperationLog(user, 'SAVE_ITEM_DRAFT', 'HANDOUT_ITEM', item.id, `保存第 ${item.seqNo} 题讲评草稿`)
        return item as T
      }
      if (action === 'confirm') {
        const nextContent = typeof p.content === 'string' ? p.content.trim() : (item.content ?? '').trim()
        if (!nextContent) fail(400, 'VALIDATION_FAILED', '请填写讲评正文后再确认')
        if (nextContent.length > 2000) fail(400, 'VALIDATION_FAILED', '讲评正文最多 2000 字')
        const versionChanged = nextContent !== item.content || !item.currentVersionId
        if (versionChanged) {
          const version = {
            id: nextId(db, 'version'),
            versionNo: item.versions.length + 1,
            content: nextContent,
            changeNote: asString(p.changeNote) || null,
            createdAt: nowIso(),
            createdBy: user.id,
          }
          item.versions.push(version)
          item.currentVersionId = version.id
          item.versionCount = item.versions.length
        }
        item.content = nextContent
        item.status = 'READY'
        item.source = item.source ?? 'MANUAL'
        item.confirmedBy = user.id
        item.confirmedAt = nowIso()
        item.allowedActions = ['CONFIRM', 'EXCLUDE', 'REGENERATE']
        refreshHandoutState(handout)
        addOperationLog(user, 'CONFIRM_ITEM', 'HANDOUT_ITEM', item.id, `确认第 ${item.seqNo} 题讲评`)
        return item as T
      }
      if (action === 'exclude') {
        const reason = asString(p.reason)
        if (!reason) fail(400, 'VALIDATION_FAILED', '请填写剔除原因')
        item.status = 'EXCLUDED'
        item.excludeReason = reason
        item.allowedActions = ['REGENERATE']
        item.confirmedBy = null
        item.confirmedAt = null
        refreshHandoutState(handout)
        addOperationLog(user, 'EXCLUDE_ITEM', 'HANDOUT_ITEM', item.id, `剔除第 ${item.seqNo} 题讲评：${reason}`)
        return item as T
      }
      if (action === 'regenerate') {
        const homeworkStats = computeStatistics(homework)
        const stat = homeworkStats.questions.find((question) => question.questionId === item.questionId)
        const question = homework.questions.find((candidate) => candidate.id === item.questionId)
        if (!question) fail(404, 'NOT_FOUND', '题目不存在')
        const evidence = makeEvidence(question, stat?.wrongCount ?? 0, stat?.totalCount ?? 0)
        item.evidence = evidence
        if (!db.aiOnline) {
          item.status = 'AI_FAILED_MANUAL'
          item.draft = buildTeacherDraft(question, item.content ?? '', evidence, 'FAILED', 'AUTH_PROVIDER_UNAVAILABLE')
        } else if (!question.standardAnswer && !question.teacherAnalysis) {
          item.status = 'INSUFFICIENT_EVIDENCE'
          item.draft = buildTeacherDraft(question, '', evidence, 'INSUFFICIENT_EVIDENCE')
        } else {
          const content = makeAiCommentary(question, stat?.wrongCount ?? 0, stat?.totalCount ?? 0)
          const version = {
            id: nextId(db, 'version'),
            versionNo: item.versions.length + 1,
            content,
            changeNote: 'AI 重新生成',
            createdAt: nowIso(),
            createdBy: user.id,
          }
          item.versions.push(version)
          item.currentVersionId = version.id
          item.versionCount = item.versions.length
          item.content = content
          item.status = 'PENDING_REVIEW'
          item.source = 'AI'
          item.confirmedBy = null
          item.confirmedAt = null
          item.draft = buildTeacherDraft(question, content, evidence, 'GENERATED')
        }
        refreshHandoutState(handout)
        addOperationLog(user, 'REGENERATE_ITEM', 'HANDOUT_ITEM', item.id, `重新生成第 ${item.seqNo} 题讲评`)
        return item as T
      }
    }

    if (segments[1] === 'handouts' && segments[2]) {
      const handoutId = asNumber(segments[2])
      const handout = handoutById(handoutId)
      const homework = requireTeacherHomework(user, handout.homeworkId)
      const action = segments[3]
      if (action === 'publish' && upperMethod === 'POST') {
        if (handout.status === 'PUBLISHED') fail(409, 'CONFLICT_STATE', '讲评已处于发布状态')
        if (asNumber(p.expectedVersion, -1) !== handout.version) {
          fail(409, 'VERSION_CONFLICT', '讲评已被更新，请刷新后重试')
        }
        requirePublishable(handout)
        const fromStatus = handout.status
        handout.status = 'PUBLISHED'
        handout.publishedAt = nowIso()
        handout.publishedBy = user.id
        handout.withdrawnAt = null
        handout.version += 1
        handout.updatedAt = nowIso()
        addHandoutLog(handout, user, fromStatus === 'WITHDRAWN' ? 'REPUBLISH' : 'PUBLISH', fromStatus, 'PUBLISHED', asString(p.remark) || '发布讲评')
        syncHomeworkHandout(homework, handout)
        addOperationLog(user, 'PUBLISH', 'HANDOUT', handout.id, `发布讲评，${handout.readyCount} 条可见`, {
          itemCount: handout.readyCount,
        })
        return decorateHandout(homework, handout) as T
      }
      if (action === 'withdraw' && upperMethod === 'POST') {
        if (handout.status !== 'PUBLISHED') fail(409, 'CONFLICT_STATE', '仅已发布讲评可以撤回')
        if (asNumber(p.expectedVersion, -1) !== handout.version) {
          fail(409, 'VERSION_CONFLICT', '讲评已被更新，请刷新后重试')
        }
        handout.status = 'WITHDRAWN'
        handout.withdrawnAt = nowIso()
        handout.version += 1
        handout.updatedAt = nowIso()
        for (const item of handout.items) {
          if (item.status === 'READY') {
            item.status = 'PENDING_REVIEW'
            item.confirmedBy = null
            item.confirmedAt = null
          }
        }
        refreshHandoutState(handout)
        addHandoutLog(handout, user, 'WITHDRAW', 'PUBLISHED', 'WITHDRAWN', asString(p.remark) || '撤回讲评')
        syncHomeworkHandout(homework, handout)
        addOperationLog(user, 'WITHDRAW', 'HANDOUT', handout.id, '撤回讲评，历史订正与疑问保留')
        return decorateHandout(homework, handout) as T
      }
      if (action === 'logs' && upperMethod === 'GET') {
        return pageOf(handout.logs, options, 20) as T
      }
    }

    if (segments[1] === 'doubts') {
      if (segments.length === 2 && upperMethod === 'GET') {
        const classIds = teacherClassIds(user)
        const classStudentIds = new Set(
          db.students.filter((student) => classIds.includes(student.classId)).map((student) => student.id),
        )
        const status = asString(param(options, 'status'))
        const keyword = asString(param(options, 'keyword')).toLowerCase()
        const doubts = db.doubts
          .filter((doubt) => classStudentIds.has(doubt.studentId))
          .filter((doubt) => !status || doubt.status === status)
          .filter(
            (doubt) =>
              !keyword ||
              doubt.content.toLowerCase().includes(keyword) ||
              doubt.studentName.toLowerCase().includes(keyword),
          )
          .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
        return pageOf(doubts, options) as T
      }
      if (segments[3] === 'replies' && upperMethod === 'POST') {
        const doubtId = asNumber(segments[2])
        const doubt = db.doubts.find((item) => item.id === doubtId)
        if (!doubt) fail(404, 'NOT_FOUND', '疑问不存在')
        const classIds = teacherClassIds(user)
        const student = db.students.find((item) => item.id === doubt.studentId)
        if (!student || !classIds.includes(student.classId)) fail(403, 'FORBIDDEN', '只能回复本班学生的疑问')
        const content = asString(p.content)
        if (!content) fail(400, 'VALIDATION_FAILED', '请填写回复内容')
        if (content.length > 1000) fail(400, 'VALIDATION_FAILED', '回复最多 1000 字')
        doubt.replies.push({
          id: nextId(db, 'reply'),
          teacherId: user.id,
          teacherName: user.displayName,
          content,
          createdAt: nowIso(),
        })
        doubt.status = 'REPLIED'
        doubt.updatedAt = nowIso()
        addOperationLog(user, 'REPLY_DOUBT', 'DOUBT', doubt.id, `回复 ${doubt.studentName} 的第 ${doubt.seqNo} 题疑问`)
        return doubt as T
      }
    }
  }

  // 学生端 -------------------------------------------------------------
  if (segments[0] === 'student') {
    const user = requireRole('STUDENT')
    const student = requireStudent(user)

    if (segments[1] === 'handouts' && segments[2] === 'mine' && upperMethod === 'GET') {
      const result: StudentHandoutSummary[] = studentHandouts(student.id)
        .sort((a, b) => (b.publishedAt ?? '').localeCompare(a.publishedAt ?? ''))
        .map((handout) => {
          const items = visibleItemsForStudent(handout, student.id)
          const correctedCount = items.filter((item) =>
            db.corrections.some(
              (correction) => correction.studentId === student.id && correction.handoutItemId === item.id,
            ),
          ).length
          const doubtCount = db.doubts.filter(
            (doubt) => doubt.studentId === student.id && items.some((item) => item.id === doubt.handoutItemId),
          ).length
          const homework = db.homeworks.find((item) => item.id === handout.homeworkId)
          return {
            id: handout.id,
            homeworkId: handout.homeworkId,
            homeworkTitle: handout.homeworkTitle,
            publishedAt: handout.publishedAt ?? handout.updatedAt,
            itemCount: items.length,
            correctedCount,
            doubtCount,
            className: homework?.className,
          }
        })
      return result as T
    }

    if (segments[1] === 'handouts' && segments[2] && upperMethod === 'GET') {
      const handoutId = asNumber(segments[2])
      const handout = handoutById(handoutId)
      const homework = db.homeworks.find((item) => item.id === handout.homeworkId)
      if (!homework || homework.classId !== student.classId) fail(403, 'FORBIDDEN', '无权查看该讲评')
      if (handout.status !== 'PUBLISHED') fail(404, 'NOT_FOUND', '讲评未发布或已撤回')
      const items: StudentHandoutItem[] = visibleItemsForStudent(handout, student.id)
        .sort((a, b) => a.sortNo - b.sortNo)
        .map((item) => {
          const record = recordsOfCurrentBatch(homework).find(
            (entry) => entry.studentId === student.id && entry.questionId === item.questionId,
          )
          const corrections = db.corrections
            .filter(
              (correction) =>
                correction.studentId === student.id && correction.handoutItemId === item.id,
            )
            .sort((a, b) => b.versionNo - a.versionNo)
          const doubts = db.doubts
            .filter((doubt) => doubt.studentId === student.id && doubt.handoutItemId === item.id)
            .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
          return {
            itemId: item.id,
            questionId: item.questionId,
            seqNo: item.seqNo,
            questionType: item.questionType,
            stem: item.stem,
            options: item.options,
            standardAnswer: item.standardAnswer,
            score: item.score,
            commentary: item.content ?? '',
            commentaryVersionId: item.currentVersionId ?? 0,
            myRecord: {
              studentAnswer: record?.studentAnswer ?? null,
              isCorrect: record?.isCorrect ?? false,
              score: record?.score ?? 0,
              teacherNote: record?.teacherNote ?? null,
            },
            corrections,
            doubts,
          }
        })
      return {
        id: handout.id,
        homeworkId: handout.homeworkId,
        homeworkTitle: handout.homeworkTitle,
        publishedAt: handout.publishedAt ?? handout.updatedAt,
        items,
      } as T
    }

    if (segments[1] === 'corrections') {
      if (upperMethod === 'GET') {
        const corrections = db.corrections
          .filter((correction) => correction.studentId === student.id)
          .sort((a, b) => b.submittedAt.localeCompare(a.submittedAt))
        return pageOf(corrections, options, 50) as T
      }
      if (upperMethod === 'POST') {
        const itemId = asNumber(p.handoutItemId)
        const { handout, item } = itemById(itemId)
        if (handout.status !== 'PUBLISHED') fail(409, 'CONFLICT_STATE', '该讲评当前不可订正')
        const homework = db.homeworks.find((entry) => entry.id === handout.homeworkId)
        if (!homework || homework.classId !== student.classId) fail(403, 'FORBIDDEN', '无权订正该讲评')
        const visible = visibleItemsForStudent(handout, student.id).some((entry) => entry.id === item.id)
        if (!visible) fail(403, 'FORBIDDEN', '只能订正本人可见的错题')
        const selectedAnswer = asString(p.selectedAnswer) || null
        const content = asString(p.content) || null
        if (!selectedAnswer && !content) fail(400, 'VALIDATION_FAILED', '请选择新答案或填写订正说明')
        if (content && content.length > 2000) fail(400, 'VALIDATION_FAILED', '订正说明最多 2000 字')
        const question = homework.questions.find((candidate) => candidate.id === item.questionId)
        if (!question) fail(404, 'NOT_FOUND', '题目不存在')
        const previous = db.corrections.filter(
          (correction) => correction.studentId === student.id && correction.handoutItemId === item.id,
        )
        const correction: CorrectionRecord = {
          id: nextId(db, 'correction'),
          homeworkId: homework.id,
          questionId: question.id,
          seqNo: question.seqNo,
          studentId: student.id,
          studentNo: student.studentNo,
          studentName: student.name,
          handoutItemId: item.id,
          itemVersionId: item.currentVersionId ?? 0,
          selectedAnswer,
          content,
          versionNo: previous.length + 1,
          submittedAt: nowIso(),
        }
        db.corrections.push(correction)
        addOperationLog(user, 'SUBMIT_CORRECTION', 'CORRECTION', correction.id, `提交第 ${correction.seqNo} 题订正`, {
          versionNo: correction.versionNo,
        })
        return correction as T
      }
    }

    if (segments[1] === 'doubts') {
      if (upperMethod === 'GET') {
        const doubts = db.doubts
          .filter((doubt) => doubt.studentId === student.id)
          .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
        return pageOf(doubts, options, 50) as T
      }
      if (upperMethod === 'POST') {
        const itemId = asNumber(p.handoutItemId)
        const { handout, item } = itemById(itemId)
        if (handout.status !== 'PUBLISHED') fail(409, 'CONFLICT_STATE', '该讲评当前不可提问')
        const homework = db.homeworks.find((entry) => entry.id === handout.homeworkId)
        if (!homework || homework.classId !== student.classId) fail(403, 'FORBIDDEN', '无权向该讲评提问')
        if (!visibleItemsForStudent(handout, student.id).some((entry) => entry.id === item.id)) {
          fail(403, 'FORBIDDEN', '只能针对本人可见的错题提问')
        }
        const content = asString(p.content)
        if (!content) fail(400, 'VALIDATION_FAILED', '请填写疑问内容')
        if (content.length > 1000) fail(400, 'VALIDATION_FAILED', '疑问最多 1000 字')
        const doubt: Doubt = {
          id: nextId(db, 'doubt'),
          studentId: student.id,
          studentNo: student.studentNo,
          studentName: student.name,
          handoutItemId: item.id,
          questionId: item.questionId,
          seqNo: item.seqNo,
          content,
          status: 'PENDING',
          createdAt: nowIso(),
          updatedAt: nowIso(),
          replies: [],
          homeworkId: homework.id,
          homeworkTitle: homework.title,
        }
        db.doubts.push(doubt)
        addOperationLog(user, 'CREATE_DOUBT', 'DOUBT', doubt.id, `对第 ${doubt.seqNo} 题提交疑问`)
        return doubt as T
      }
    }
  }

  // 教研端 -------------------------------------------------------------
  if (segments[0] === 'research') {
    const user = requireRole('RESEARCH')

    if (segments[1] === 'knowledge-points') {
      if (segments.length === 2 && upperMethod === 'GET') {
        const includeDisabled = String(param(options, 'includeDisabled')) === 'true'
        const list = db.knowledgePoints
          .filter((kp) => includeDisabled || kp.status === 'ACTIVE')
          .map((kp) => ({ ...kp, children: [] as KnowledgePoint[] }))
        const map = new Map(list.map((kp) => [kp.id, kp]))
        const roots: KnowledgePoint[] = []
        for (const kp of list) {
          if (kp.parentId) {
            map.get(kp.parentId)?.children?.push(kp)
          } else {
            roots.push(kp)
          }
        }
        return roots as T
      }
      if (segments.length === 2 && upperMethod === 'POST') {
        const kpLevel = asNumber(p.kpLevel)
        const name = asString(p.name)
        const code = asString(p.code)
        const parentId = p.parentId === null || p.parentId === undefined ? null : asNumber(p.parentId)
        if (!name || !code || ![1, 2].includes(kpLevel)) {
          fail(400, 'VALIDATION_FAILED', '请完整填写编码、名称与级别')
        }
        if (db.knowledgePoints.some((kp) => kp.code.toLowerCase() === code.toLowerCase())) {
          fail(409, 'CONFLICT_STATE', '知识点编码已存在')
        }
        if (kpLevel === 1 && parentId !== null) fail(400, 'VALIDATION_FAILED', '一级知识点不能设置父节点')
        if (kpLevel === 2) {
          const parent = db.knowledgePoints.find((kp) => kp.id === parentId)
          if (!parent || parent.kpLevel !== 1) fail(400, 'VALIDATION_FAILED', '二级知识点必须挂在章节下')
        }
        const kp: KnowledgePoint = {
          id: nextId(db, 'knowledgePoint'),
          parentId,
          code,
          name,
          kpLevel: kpLevel as 1 | 2,
          status: 'ACTIVE',
          disabledImpactNote: null,
          createdAt: nowIso(),
          updatedAt: nowIso(),
        }
        db.knowledgePoints.push(kp)
        addOperationLog(user, 'CREATE_KNOWLEDGE_POINT', 'KNOWLEDGE_POINT', kp.id, `新增知识点「${name}」`)
        return kp as T
      }
      if (segments[2] && upperMethod === 'PUT') {
        const kp = db.knowledgePoints.find((item) => item.id === asNumber(segments[2]))
        if (!kp) fail(404, 'NOT_FOUND', '知识点不存在')
        const name = asString(p.name)
        if (!name) fail(400, 'VALIDATION_FAILED', '知识点名称不能为空')
        kp.name = name
        kp.updatedAt = nowIso()
        addOperationLog(user, 'UPDATE_KNOWLEDGE_POINT', 'KNOWLEDGE_POINT', kp.id, `重命名知识点为「${name}」`)
        return kp as T
      }
      if (segments[3] === 'disable' && upperMethod === 'POST') {
        const kp = db.knowledgePoints.find((item) => item.id === asNumber(segments[2]))
        if (!kp) fail(404, 'NOT_FOUND', '知识点不存在')
        const impactNote = asString(p.impactNote)
        if (!impactNote) fail(400, 'VALIDATION_FAILED', '停用必须填写历史口径影响说明')
        kp.status = 'DISABLED'
        kp.disabledImpactNote = impactNote
        kp.updatedAt = nowIso()
        addOperationLog(user, 'DISABLE_KNOWLEDGE_POINT', 'KNOWLEDGE_POINT', kp.id, `停用知识点「${kp.name}」`, {
          impactNote,
        })
        return kp as T
      }
    }

    if (segments[1] === 'templates') {
      if (segments.length === 2 && upperMethod === 'GET') {
        const status = asString(param(options, 'status'))
        const templates = db.templates
          .filter((template) => !status || template.status === status)
          .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
        return pageOf(templates, options) as T
      }
      if (segments[3] === 'review' && upperMethod === 'POST') {
        const template = db.templates.find((item) => item.id === asNumber(segments[2]))
        if (!template) fail(404, 'NOT_FOUND', '模板不存在')
        if (template.status !== 'PENDING') fail(409, 'CONFLICT_STATE', '仅待审核模板可以审核')
        const decision = asString(p.decision)
        const reviewNote = asString(p.reviewNote)
        if (!['APPROVED', 'REJECTED'].includes(decision)) fail(400, 'VALIDATION_FAILED', '审核结论无效')
        if (decision === 'REJECTED' && !reviewNote) fail(400, 'VALIDATION_FAILED', '拒绝时必须填写审核意见')
        template.status = decision as PublicTemplate['status']
        template.reviewedBy = user.id
        template.reviewNote = reviewNote || (decision === 'APPROVED' ? '审核通过。' : null)
        template.reviewedAt = nowIso()
        template.updatedAt = nowIso()
        addOperationLog(user, 'REVIEW_TEMPLATE', 'TEMPLATE', template.id, `${decision === 'APPROVED' ? '通过' : '拒绝'}公共模板「${template.title}」`, {
          reviewNote,
        })
        return template as T
      }
    }

    if (segments[1] === 'operation-logs' && upperMethod === 'GET') {
      const action = asString(param(options, 'action'))
      const targetType = asString(param(options, 'targetType'))
      const actorId = asNumber(param(options, 'actorId'), 0)
      const logs = db.operationLogs
        .filter((log) => !action || log.action === action)
        .filter((log) => !targetType || log.targetType === targetType)
        .filter((log) => !actorId || log.actorId === actorId)
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      return pageOf(logs, options) as T
    }
  }

  // AI ----------------------------------------------------------------
  if (segments[0] === 'ai') {
    requireUser()
    if (segments[1] === 'explain' && upperMethod === 'POST') {
      fail(501, 'FEATURE_DISABLED', '首版不开放学生端 AI 自由追问')
    }
    if (segments[1] === 'draft' && upperMethod === 'POST') {
      const user = requireRole('TEACHER')
      const homework = requireTeacherHomework(user, asNumber(p.homeworkId))
      const question = homework.questions.find((item) => item.id === asNumber(p.questionId))
      if (!question) fail(404, 'NOT_FOUND', '题目不存在')
      const stats = computeStatistics(homework)
      const stat = stats.questions.find((item) => item.questionId === question.id)
      const evidence = makeEvidence(question, stat?.wrongCount ?? 0, stat?.totalCount ?? 0)
      if (!db.aiOnline) {
        return buildTeacherDraft(question, '', evidence, 'FAILED', 'AUTH_PROVIDER_UNAVAILABLE') as T
      }
      if (!question.standardAnswer && !question.teacherAnalysis) {
        return buildTeacherDraft(question, '', evidence, 'INSUFFICIENT_EVIDENCE') as T
      }
      const content = makeAiCommentary(question, stat?.wrongCount ?? 0, stat?.totalCount ?? 0)
      return buildTeacherDraft(question, content, evidence, 'GENERATED') as T
    }
  }

  fail(404, 'NOT_FOUND', `Mock 接口未实现：${method} ${url.pathname}`)
}

export async function mockRequest<T>(
  method: string,
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const result = await handleMockRequest<T>(method, path, options)
  const upperMethod = method.toUpperCase()
  if (upperMethod !== 'GET' && upperMethod !== 'HEAD') persistDb()
  return cloneResponse(result)
}

function cloneResponse<T>(value: T): T {
  if (value === undefined || value === null) return value
  return structuredClone(value)
}
