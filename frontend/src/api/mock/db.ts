import type {
  AiDraft,
  AiEvidence,
  ClassSummary,
  CorrectionRecord,
  CurrentUser,
  Doubt,
  GradingBatch,
  GradingRecord,
  HandoutDetail,
  HandoutItemTeacherView,
  HandoutLog,
  HomeworkDetail,
  KnowledgePoint,
  OperationLog,
  PublicTemplate,
  Question,
  StudentSummary,
} from '@/types/models'

export interface MockUser extends CurrentUser {
  password: string
  classIds: number[]
  studentId: number | null
}

export interface MockStudent extends StudentSummary {
  classId: number
}

export interface MockVersion {
  id: number
  versionNo: number
  content: string
  changeNote: string | null
  createdAt: string
  createdBy: number
}

export interface MockHandoutItem extends HandoutItemTeacherView {
  versions: MockVersion[]
  excludeReason: string | null
}

export interface MockHandout extends HandoutDetail {
  items: MockHandoutItem[]
  logs: HandoutLog[]
}

export interface MockDb {
  users: MockUser[]
  classes: ClassSummary[]
  students: MockStudent[]
  teacherClasses: Array<{ teacherUserId: number; classId: number }>
  homeworks: HomeworkDetail[]
  questions: Question[]
  knowledgePoints: KnowledgePoint[]
  batches: GradingBatch[]
  records: GradingRecord[]
  handouts: MockHandout[]
  corrections: CorrectionRecord[]
  doubts: Doubt[]
  templates: PublicTemplate[]
  operationLogs: OperationLog[]
  aiOnline: boolean
  counters: Record<string, number>
}

const baseTime = Date.now()
const isoAgo = (minutes: number) => new Date(baseTime - minutes * 60_000).toISOString()
const isoAhead = (minutes: number) => new Date(baseTime + minutes * 60_000).toISOString()

const QUESTION_TEXTS: Record<number, string> = {
  1: '下列方程中，是一元一次方程的是？',
  2: '方程 2x+3=11 的解是？',
  3: '方程 3(x−1)=9 的解是？',
  4: '方程一定是等式。',
  5: '方程 2(x−3)=x+1 的解是？',
  6: '方程 3x−2x+5=0 的解是？',
  7: '移项时不需要改变符号。',
  8: '小明从家到学校，如果每分钟走 60 米，会迟到 3 分钟；如果每分钟走 80 米，会提前 1 分钟。下列方程正确的是？',
  9: '方程 x−4=3 的解是？',
  10: '方程 2x+1=9 的解是？',
  11: '方程两边同乘同一个不为 0 的数，方程仍成立。',
}

function makeQuestions(): Question[] {
  const q = (
    id: number,
    homeworkId: number,
    seqNo: number,
    questionType: 'SINGLE_CHOICE' | 'TRUE_FALSE',
    stem: string,
    options: Array<{ key: string; text: string }> | null,
    standardAnswer: string | null,
    teacherAnalysis: string | null,
    knowledgePointIds: number[],
  ): Question => ({
    id,
    homeworkId,
    seqNo,
    questionType,
    stem,
    options,
    standardAnswer,
    teacherAnalysis,
    score: 5,
    knowledgePointIds,
    revisionNo: 1,
  })

  return [
    q(1, 1, 1, 'SINGLE_CHOICE', QUESTION_TEXTS[1], [
      { key: 'A', text: '2x+1=5' },
      { key: 'B', text: 'x²=4' },
      { key: 'C', text: '1/x=2' },
      { key: 'D', text: 'x+y=3' },
    ], 'A', '只含一个未知数且未知数次数为 1 的整式方程，才是一元一次方程。', [2]),
    q(2, 1, 2, 'SINGLE_CHOICE', QUESTION_TEXTS[2], [
      { key: 'A', text: 'x=3' },
      { key: 'B', text: 'x=4' },
      { key: 'C', text: 'x=5' },
      { key: 'D', text: 'x=6' },
    ], 'B', '移项得 2x=8，所以 x=4。', [3]),
    q(3, 1, 3, 'SINGLE_CHOICE', QUESTION_TEXTS[3], [
      { key: 'A', text: 'x=2' },
      { key: 'B', text: 'x=3' },
      { key: 'C', text: 'x=4' },
      { key: 'D', text: 'x=5' },
    ], 'C', '两边同除以 3 得 x−1=3，所以 x=4。', [4]),
    q(4, 1, 4, 'TRUE_FALSE', QUESTION_TEXTS[4], null, 'TRUE',
      '方程是含未知数的等式，因此一定是等式；但等式不一定是方程。', [4]),
    q(5, 1, 5, 'SINGLE_CHOICE', QUESTION_TEXTS[5], [
      { key: 'A', text: 'x=7' },
      { key: 'B', text: 'x=5' },
      { key: 'C', text: 'x=−5' },
      { key: 'D', text: 'x=6' },
    ], 'A', '去括号时括号前是负号，括号内每一项都要变号：2x−6=x+1，所以 x=7。', [4]),
    q(6, 1, 6, 'SINGLE_CHOICE', QUESTION_TEXTS[6], [
      { key: 'A', text: 'x=5' },
      { key: 'B', text: 'x=−5' },
      { key: 'C', text: 'x=0' },
      { key: 'D', text: 'x=1' },
    ], 'B', '合并同类项得 x+5=0，移项得 x=−5。', [5]),
    q(7, 1, 7, 'TRUE_FALSE', QUESTION_TEXTS[7], null, 'FALSE',
      '移项的依据是等式性质，移项时必须改变符号。', [3]),
    q(8, 1, 8, 'SINGLE_CHOICE', QUESTION_TEXTS[8], [
      { key: 'A', text: 'x/60 + 3 = x/80 − 1' },
      { key: 'B', text: 'x/60 − 3 = x/80 + 1' },
      { key: 'C', text: 'x/60 + 3 = x/80 + 1' },
      { key: 'D', text: 'x/60 − 3 = x/80 − 1' },
    ], null, null, [6]),
    q(9, 2, 1, 'SINGLE_CHOICE', QUESTION_TEXTS[9], [
      { key: 'A', text: 'x=7' },
      { key: 'B', text: 'x=1' },
      { key: 'C', text: 'x=−1' },
      { key: 'D', text: 'x=11' },
    ], 'A', '移项得 x=3+4=7。', [2]),
    q(10, 2, 2, 'SINGLE_CHOICE', QUESTION_TEXTS[10], [
      { key: 'A', text: 'x=3' },
      { key: 'B', text: 'x=5' },
      { key: 'C', text: 'x=4' },
      { key: 'D', text: 'x=8' },
    ], 'C', '移项得 2x=8，所以 x=4。', [3]),
    q(11, 2, 3, 'TRUE_FALSE', QUESTION_TEXTS[11], null, 'TRUE',
      '这是等式性质 2；必须强调乘的数不为 0。', [2]),
  ]
}

function makeKnowledgePoints(): KnowledgePoint[] {
  const kp = (
    id: number,
    parentId: number | null,
    code: string,
    name: string,
    kpLevel: 1 | 2,
  ): KnowledgePoint => ({
    id,
    parentId,
    code,
    name,
    kpLevel,
    status: 'ACTIVE',
    disabledImpactNote: null,
    createdAt: isoAgo(100_000),
    updatedAt: isoAgo(50_000),
    children: [],
  })
  return [
    kp(1, null, 'KP-CH1', '第一章 · 一元一次方程', 1),
    kp(2, 1, 'KP-CH1-EQ', '等式的性质', 2),
    kp(3, 1, 'KP-CH1-MOVE', '移项', 2),
    kp(4, 1, 'KP-CH1-BRACKET', '去括号（含符号变化）', 2),
    kp(5, 1, 'KP-CH1-COMBINE', '合并同类项', 2),
    kp(6, 1, 'KP-CH1-APPLY', '方程应用 · 设未知数', 2),
  ]
}

function makeStudents(): MockStudent[] {
  return Array.from({ length: 45 }, (_, index) => {
    const n = index + 1
    const id = 100 + n
    return {
      id,
      studentNo: `S2024${String(n).padStart(3, '0')}`,
      name: id === 113 ? '小李' : `虚构学生${String(n).padStart(2, '0')}`,
      status: 'ACTIVE',
      classId: 1,
    }
  })
}

const WRONG_ANSWERS: Record<number, string> = {
  1: 'B', 2: 'C', 3: 'A', 4: 'FALSE', 5: 'B',
  6: 'C', 7: 'TRUE', 8: 'A', 9: 'B', 10: 'A', 11: 'FALSE',
}

function isWrong(homeworkId: number, seqNo: number, studentId: number, batchVersion: number): boolean {
  if (homeworkId === 1) {
    const table: Record<number, (id: number) => boolean> = {
      1: (id) => id <= 103,
      2: (id) => id <= 105,
      3: (id) => id <= 107,
      4: (id) => id <= 110,
      5: (id) => id <= (batchVersion === 1 ? 127 : 128),
      6: (id) => id <= 120,
      7: (id) => (id >= 101 && id <= 112) || id === 115 || id === 116,
      8: (id) => id <= 124,
    }
    return table[seqNo]?.(studentId) ?? false
  }
  if (homeworkId === 2) {
    if (seqNo === 1) return studentId <= 120
    if (seqNo === 2) return studentId >= 113 && studentId <= 122
    if (seqNo === 3) return (studentId >= 101 && studentId <= 110) || studentId === 113
  }
  return false
}

function wrongAnswerFor(question: Question): string | null {
  const { standardAnswer, options } = question
  if (!options?.length) {
    if (standardAnswer === 'TRUE') return 'FALSE'
    if (standardAnswer === 'FALSE') return 'TRUE'
    return null
  }
  const candidate = WRONG_ANSWERS[question.seqNo]
  if (candidate && candidate !== standardAnswer && options.some((option) => option.key === candidate)) {
    return candidate
  }
  return options.find((option) => option.key !== standardAnswer)?.key ?? null
}

function makeRecordsAndBatches(homeworks: HomeworkDetail[], students: MockStudent[], questions: Question[]) {
  const batches: GradingBatch[] = [
    {
      id: 1,
      homeworkId: 1,
      versionNo: 1,
      source: 'EXCEL',
      importedBy: 2,
      totalRows: 360,
      validRows: 360,
      remark: '初次导入',
      createdAt: isoAgo(1440),
    },
    {
      id: 2,
      homeworkId: 1,
      versionNo: 2,
      source: 'EXCEL',
      importedBy: 2,
      totalRows: 360,
      validRows: 360,
      remark: '第 5 题批改修订：补录 1 条错误',
      createdAt: isoAgo(50),
    },
    {
      id: 3,
      homeworkId: 2,
      versionNo: 1,
      source: 'EXCEL',
      importedBy: 2,
      totalRows: 135,
      validRows: 135,
      remark: '初次导入',
      createdAt: isoAgo(4320),
    },
  ]

  const records: GradingRecord[] = []
  let recordId = 10_000
  for (const batch of batches) {
    const hw = homeworks.find((item) => item.id === batch.homeworkId)
    if (!hw) continue
    for (const student of students) {
      for (const question of questions.filter((item) => item.homeworkId === hw.id)) {
        const wrong = isWrong(hw.id, question.seqNo, student.id, batch.versionNo)
        const answer = wrong ? wrongAnswerFor(question) : question.standardAnswer
        records.push({
          id: recordId++,
          batchId: batch.id,
          homeworkId: hw.id,
          studentId: student.id,
          studentNo: student.studentNo,
          studentName: student.name,
          questionId: question.id,
          seqNo: question.seqNo,
          studentAnswer: answer,
          isCorrect: !wrong,
          score: wrong ? 0 : question.score,
          teacherNote: null,
          errorTag: wrong ? '概念混淆' : null,
          createdAt: batch.createdAt,
        })
      }
    }
  }
  return { batches, records }
}

function evidence(type: AiEvidence['evidenceType'], text: string, refId?: number): AiEvidence {
  return { evidenceType: type, refId: refId ?? null, snapshotText: text }
}

function makeDrafts(): AiDraft[] {
  const common = {
    draftType: 'QUESTION_COMMENTARY' as const,
    model: 'mock-qwen-plus',
    promptVersion: 'prompt-v1',
    generationNo: 1,
    requestId: 'req-ai-001',
    elapsedMs: 1100,
    errorCode: null,
    createdAt: isoAgo(40),
  }
  return [
    {
      ...common,
      id: 1,
      homeworkId: 1,
      questionId: 1,
      content: '第1题考查一元一次方程的定义：只含一个未知数且未知数次数为 1 的整式方程。B 是二次，C 是分式，D 含两个未知数，故选 A。',
      status: 'GENERATED',
      evidence: [
        evidence('QUESTION_STEM', '下列方程中，是一元一次方程的是？ A. 2x+1=5 B. x²=4 C. 1/x=2 D. x+y=3', 1),
        evidence('STANDARD_ANSWER', 'A', 1),
      ],
    },
    {
      ...common,
      id: 2,
      homeworkId: 1,
      questionId: 2,
      content: '移项得 2x=11−3=8，所以 x=4。提醒学生：移项要变号。',
      status: 'GENERATED',
      evidence: [
        evidence('QUESTION_STEM', '方程 2x+3=11 的解是？', 2),
        evidence('STANDARD_ANSWER', 'B（x=4）', 2),
      ],
    },
    {
      ...common,
      id: 3,
      homeworkId: 1,
      questionId: 3,
      content: null,
      status: 'FAILED',
      errorCode: 'TIMEOUT',
      elapsedMs: 30_000,
      evidence: [evidence('QUESTION_STEM', '方程 3(x−1)=9 的解是？', 3)],
    },
    {
      ...common,
      id: 4,
      homeworkId: 1,
      questionId: 4,
      content: '方程一定是等式，但等式不一定是方程。题干说法正确。',
      status: 'GENERATED',
      evidence: [
        evidence('QUESTION_STEM', '判断：方程一定是等式。', 4),
        evidence('TEACHER_ANALYSIS', '方程是含未知数的等式，因此一定是等式；但等式不一定是方程。', 4),
      ],
    },
    {
      ...common,
      id: 5,
      homeworkId: 1,
      questionId: 5,
      content: '第5题考查含负号括号的展开。错选 B 的学生多为去括号时只对第一项变号。正确过程：2(x−3)=x+1 → 2x−6=x+1 → x=7，答案 A。本题 28/45 人答错，建议课堂重点板演。',
      status: 'GENERATED',
      elapsedMs: 1560,
      evidence: [
        evidence('QUESTION_STEM', '方程 2(x−3)=x+1 的解是？', 5),
        evidence('STANDARD_ANSWER', 'A（x=7）', 5),
        evidence('TEACHER_ANALYSIS', '去括号时括号前是负号，括号内每一项都要变号。', 5),
        evidence('STATISTICS', '第5题：45 人中 28 人答错，错误率 62.22%。'),
      ],
    },
    {
      ...common,
      id: 6,
      homeworkId: 1,
      questionId: 6,
      content: '合并同类项得 x+5=0，移项得 x=−5，答案 B。提醒合并时只合并同类项。',
      status: 'GENERATED',
      evidence: [
        evidence('QUESTION_STEM', '方程 3x−2x+5=0 的解是？', 6),
        evidence('STANDARD_ANSWER', 'B（x=−5）', 6),
      ],
    },
    {
      ...common,
      id: 7,
      homeworkId: 1,
      questionId: 7,
      content: '移项的依据是等式性质，移项必须变号。题干说法错误。',
      status: 'GENERATED',
      evidence: [
        evidence('QUESTION_STEM', '判断：移项时不需要改变符号。', 7),
        evidence('TEACHER_ANALYSIS', '移项必须变号。', 7),
      ],
    },
    {
      ...common,
      id: 8,
      homeworkId: 1,
      questionId: 8,
      content: null,
      status: 'INSUFFICIENT_EVIDENCE',
      elapsedMs: 540,
      evidence: [evidence('QUESTION_STEM', '小明从家到学校……设距离为 x 米，下列方程正确的是？', 8)],
    },
  ]
}

function makeHandouts(drafts: AiDraft[]): MockHandout[] {
  const draftById = new Map(drafts.map((draft) => [draft.id, draft]))

  const itemBase = {
    handoutId: 1,
    score: 5,
    sortNo: 1,
    currentVersionId: null,
    versionCount: 1,
    confirmedBy: null,
    confirmedAt: null,
    allowedActions: ['CONFIRM', 'EXCLUDE', 'REGENERATE'] as HandoutItemTeacherView['allowedActions'],
    evidence: [] as AiEvidence[],
    draft: null,
    versions: [] as MockVersion[],
    excludeReason: null,
    source: 'AI' as const,
  }

  const handout1Items: MockHandoutItem[] = []

  const contents: Record<number, string> = {
    1: '第 1 题考查一元一次方程的定义：只含一个未知数且未知数次数为 1 的整式方程。B 为二次，C 为分式，D 含两个未知数，答案 A。',
    2: '移项得 2x=8，所以 x=4。提醒：移项要变号。',
    4: '方程一定是等式；等式不一定是方程。题干说法正确。',
    5: '去括号时括号前是负号，括号内每一项都要变号。2(x−3)=x+1 → 2x−6=x+1 → x=7，选 A。本题 28/45 人答错，19 人误选 B，多因只对第一项变号。',
    6: '合并同类项得 x+5=0，移项得 x=−5，选 B。注意合并时只合并同类项。',
    7: '移项的依据是等式性质，移项必须变号，本题说法错误。',
  }

  const statuses: Array<{ q: number; status: HandoutItemTeacherView['status']; draftId: number | null; versionId: number | null }> = [
    { q: 1, status: 'READY', draftId: 1, versionId: 1 },
    { q: 2, status: 'PENDING_REVIEW', draftId: 2, versionId: 2 },
    { q: 3, status: 'AI_FAILED_MANUAL', draftId: 3, versionId: null },
    { q: 4, status: 'EXCLUDED', draftId: 4, versionId: 3 },
    { q: 5, status: 'PENDING_REVIEW', draftId: 5, versionId: 4 },
    { q: 6, status: 'DRAFT', draftId: 6, versionId: 5 },
    { q: 7, status: 'READY', draftId: 7, versionId: 6 },
    { q: 8, status: 'INSUFFICIENT_EVIDENCE', draftId: 8, versionId: null },
  ]

  const questionSnapshot: Record<number, Question> = {
    1: { id: 1, homeworkId: 1, seqNo: 1, questionType: 'SINGLE_CHOICE', stem: QUESTION_TEXTS[1], options: [{ key: 'A', text: '2x+1=5' }, { key: 'B', text: 'x²=4' }, { key: 'C', text: '1/x=2' }, { key: 'D', text: 'x+y=3' }], standardAnswer: 'A', teacherAnalysis: '只含一个未知数且未知数次数为 1 的整式方程。', score: 5, knowledgePointIds: [2], revisionNo: 1 },
    2: { id: 2, homeworkId: 1, seqNo: 2, questionType: 'SINGLE_CHOICE', stem: QUESTION_TEXTS[2], options: [{ key: 'A', text: 'x=3' }, { key: 'B', text: 'x=4' }, { key: 'C', text: 'x=5' }, { key: 'D', text: 'x=6' }], standardAnswer: 'B', teacherAnalysis: '移项得 2x=8。', score: 5, knowledgePointIds: [3], revisionNo: 1 },
    3: { id: 3, homeworkId: 1, seqNo: 3, questionType: 'SINGLE_CHOICE', stem: QUESTION_TEXTS[3], options: [{ key: 'A', text: 'x=2' }, { key: 'B', text: 'x=3' }, { key: 'C', text: 'x=4' }, { key: 'D', text: 'x=5' }], standardAnswer: 'C', teacherAnalysis: '两边同除以 3。', score: 5, knowledgePointIds: [4], revisionNo: 1 },
    4: { id: 4, homeworkId: 1, seqNo: 4, questionType: 'TRUE_FALSE', stem: QUESTION_TEXTS[4], options: null, standardAnswer: 'TRUE', teacherAnalysis: '方程是含未知数的等式。', score: 5, knowledgePointIds: [4], revisionNo: 1 },
    5: { id: 5, homeworkId: 1, seqNo: 5, questionType: 'SINGLE_CHOICE', stem: QUESTION_TEXTS[5], options: [{ key: 'A', text: 'x=7' }, { key: 'B', text: 'x=5' }, { key: 'C', text: 'x=−5' }, { key: 'D', text: 'x=6' }], standardAnswer: 'A', teacherAnalysis: '括号前是负号，括号内每一项都要变号。', score: 5, knowledgePointIds: [4], revisionNo: 1 },
    6: { id: 6, homeworkId: 1, seqNo: 6, questionType: 'SINGLE_CHOICE', stem: QUESTION_TEXTS[6], options: [{ key: 'A', text: 'x=5' }, { key: 'B', text: 'x=−5' }, { key: 'C', text: 'x=0' }, { key: 'D', text: 'x=1' }], standardAnswer: 'B', teacherAnalysis: '合并同类项得 x+5=0。', score: 5, knowledgePointIds: [5], revisionNo: 1 },
    7: { id: 7, homeworkId: 1, seqNo: 7, questionType: 'TRUE_FALSE', stem: QUESTION_TEXTS[7], options: null, standardAnswer: 'FALSE', teacherAnalysis: '移项必须变号。', score: 5, knowledgePointIds: [3], revisionNo: 1 },
    8: { id: 8, homeworkId: 1, seqNo: 8, questionType: 'SINGLE_CHOICE', stem: QUESTION_TEXTS[8], options: [{ key: 'A', text: 'x/60 + 3 = x/80 − 1' }, { key: 'B', text: 'x/60 − 3 = x/80 + 1' }, { key: 'C', text: 'x/60 + 3 = x/80 + 1' }, { key: 'D', text: 'x/60 − 3 = x/80 − 1' }], standardAnswer: null, teacherAnalysis: null, score: 5, knowledgePointIds: [6], revisionNo: 1 },
  }

  for (const entry of statuses) {
    const question = questionSnapshot[entry.q]
    const draft = entry.draftId ? draftById.get(entry.draftId) ?? null : null
    const content = contents[entry.q] ?? null
    const version: MockVersion | null = entry.versionId
      ? {
          id: entry.versionId,
          versionNo: 1,
          content: content ?? '',
          changeNote: null,
          createdAt: isoAgo(35),
          createdBy: 2,
        }
      : null
    handout1Items.push({
      ...itemBase,
      id: entry.q,
      questionId: question.id,
      seqNo: question.seqNo,
      questionType: question.questionType,
      stem: question.stem,
      options: question.options,
      standardAnswer: question.standardAnswer,
      teacherAnalysis: question.teacherAnalysis,
      source: entry.q === 8 ? null : 'AI',
      status: entry.status,
      sortNo: entry.q,
      currentVersionId: version?.id ?? null,
      content,
      versionCount: version ? 1 : 0,
      confirmedBy: entry.status === 'READY' ? 2 : null,
      confirmedAt: entry.status === 'READY' ? isoAgo(25) : null,
      evidence: draft?.evidence ?? [],
      draft,
      versions: version ? [version] : [],
      excludeReason: entry.status === 'EXCLUDED' ? '正确率较高，本次讲评不占用课堂时间' : null,
      allowedActions: entry.status === 'EXCLUDED' ? ['REGENERATE'] : ['CONFIRM', 'EXCLUDE', 'REGENERATE'],
    })
  }

  const handout2Items: MockHandoutItem[] = [9, 10, 11].map((id, index) => {
    const q = id - 8
    const qData = [
      { id: 9, questionId: 9, seqNo: 1, stem: QUESTION_TEXTS[9], options: [{ key: 'A', text: 'x=7' }, { key: 'B', text: 'x=1' }, { key: 'C', text: 'x=−1' }, { key: 'D', text: 'x=11' }], standardAnswer: 'A' },
      { id: 10, questionId: 10, seqNo: 2, stem: QUESTION_TEXTS[10], options: [{ key: 'A', text: 'x=3' }, { key: 'B', text: 'x=5' }, { key: 'C', text: 'x=4' }, { key: 'D', text: 'x=8' }], standardAnswer: 'C' },
      { id: 11, questionId: 11, seqNo: 3, stem: QUESTION_TEXTS[11], options: null, standardAnswer: 'TRUE' },
    ][index]
    const content = [
      '移项得 x=3+4=7，选 A。注意移项变号。',
      '移项得 2x=9−1=8，所以 x=4，选 C。',
      '等式两边同乘一个不为 0 的数，等式仍成立。题干正确。',
    ][index]
    return {
      ...itemBase,
      id,
      handoutId: 2,
      questionId: qData.questionId,
      seqNo: qData.seqNo,
      questionType: q === 3 ? 'TRUE_FALSE' : 'SINGLE_CHOICE',
      stem: qData.stem,
      options: qData.options,
      standardAnswer: qData.standardAnswer,
      teacherAnalysis: index === 0 ? '移项得 x=3+4。' : index === 1 ? '移项得 2x=8。' : '等式性质 2。',
      source: 'MANUAL',
      status: 'READY',
      sortNo: index + 1,
      currentVersionId: 7 + index,
      content,
      versionCount: 1,
      confirmedBy: 2,
      confirmedAt: isoAgo(120),
      allowedActions: ['CONFIRM', 'EXCLUDE', 'REGENERATE'],
      evidence: [
        evidence('QUESTION_STEM', qData.stem, qData.questionId),
        evidence('STANDARD_ANSWER', qData.standardAnswer, qData.questionId),
      ],
      draft: null,
      versions: [{
        id: 7 + index,
        versionNo: 1,
        content,
        changeNote: null,
        createdAt: isoAgo(180),
        createdBy: 2,
      }],
      excludeReason: null,
    } satisfies MockHandoutItem
  })

  const handout1: MockHandout = {
    id: 1,
    homeworkId: 1,
    homeworkTitle: '一元一次方程 · 第 3 课时',
    status: 'UNPUBLISHED',
    itemCount: 8,
    readyCount: 2,
    pendingReviewCount: 2,
    blockedCount: 3,
    excludedCount: 1,
    publishable: false,
    blockingReasons: ['仍有 2 条待审核', '1 条 AI 失败待手工', '1 条草稿未确认', '1 条依据不足'],
    publishedAt: null,
    publishedBy: null,
    withdrawnAt: null,
    version: 0,
    createdAt: isoAgo(50),
    updatedAt: isoAgo(30),
    items: handout1Items,
    logs: [],
  }

  const handout2: MockHandout = {
    id: 2,
    homeworkId: 2,
    homeworkTitle: '一元一次方程 · 第 4 课时（已发布示例）',
    status: 'PUBLISHED',
    itemCount: 3,
    readyCount: 3,
    pendingReviewCount: 0,
    blockedCount: 0,
    excludedCount: 0,
    publishable: true,
    blockingReasons: [],
    publishedAt: isoAgo(120),
    publishedBy: 2,
    withdrawnAt: null,
    version: 1,
    createdAt: isoAgo(180),
    updatedAt: isoAgo(120),
    items: handout2Items,
    logs: [{
      id: 1,
      action: 'PUBLISH',
      fromStatus: 'UNPUBLISHED',
      toStatus: 'PUBLISHED',
      itemCount: 3,
      operatorId: 2,
      operatorName: '王老师',
      remark: '第 4 课时讲评发布',
      createdAt: isoAgo(120),
    }],
  }

  return [handout1, handout2]
}

function makeCorrections(): CorrectionRecord[] {
  const corrections: CorrectionRecord[] = [{
    id: 1,
    homeworkId: 2,
    questionId: 9,
    seqNo: 1,
    studentId: 113,
    studentNo: 'S2024013',
    studentName: '小李',
    handoutItemId: 9,
    itemVersionId: 7,
    selectedAnswer: 'A',
    content: '上次移项忘记变号，这次先写 x=3+4=7，再验算。',
    versionNo: 1,
    submittedAt: isoAgo(60),
  }]

  let id = 2
  const add = (studentId: number, questionId: number, seqNo: number, itemId: number, itemVersionId: number, answer: string, content: string, minutesAgo: number) => {
    const student = makeStudents().find((item) => item.id === studentId)
    if (!student) return
    corrections.push({
      id: id++,
      homeworkId: 2,
      questionId,
      seqNo,
      studentId,
      studentNo: student.studentNo,
      studentName: student.name,
      handoutItemId: itemId,
      itemVersionId,
      selectedAnswer: answer,
      content,
      versionNo: 1,
      submittedAt: isoAgo(minutesAgo),
    })
  }

  for (let n = 0; n < 10; n += 1) {
    add(101 + n, 9, 1, 9, 7, 'A', '已重做并核对移项步骤。', 150 - n * 8)
  }
  for (let n = 0; n < 5; n += 1) {
    add(114 + n, 10, 2, 10, 8, 'C', '先移项，再两边同除以 2。', 100 - n * 6)
  }
  for (let n = 0; n < 3; n += 1) {
    add(101 + n, 11, 3, 11, 9, 'TRUE', '记住乘的数不能为 0。', 70 - n * 5)
  }
  return corrections
}

function makeDoubts(): Doubt[] {
  return [
    {
      id: 1,
      studentId: 113,
      studentNo: 'S2024013',
      studentName: '小李',
      handoutItemId: 10,
      questionId: 10,
      seqNo: 2,
      content: '为什么移项要变号？和在等式两边同时减一个数有什么区别？',
      status: 'PENDING',
      createdAt: isoAgo(40),
      updatedAt: isoAgo(40),
      replies: [],
      homeworkId: 2,
      homeworkTitle: '一元一次方程 · 第 4 课时（已发布示例）',
    },
    {
      id: 2,
      studentId: 104,
      studentNo: 'S2024004',
      studentName: '虚构学生04',
      handoutItemId: 10,
      questionId: 10,
      seqNo: 2,
      content: '移项后为什么要先合并同类项，再除以系数？',
      status: 'PENDING',
      createdAt: isoAgo(95),
      updatedAt: isoAgo(95),
      replies: [],
      homeworkId: 2,
      homeworkTitle: '一元一次方程 · 第 4 课时（已发布示例）',
    },
    {
      id: 3,
      studentId: 119,
      studentNo: 'S2024019',
      studentName: '虚构学生19',
      handoutItemId: 11,
      questionId: 11,
      seqNo: 3,
      content: '两边同乘 0 为什么不成立？',
      status: 'REPLIED',
      createdAt: isoAgo(1380),
      updatedAt: isoAgo(1200),
      replies: [{
        id: 1,
        teacherId: 2,
        teacherName: '王老师',
        content: '等式性质中的“同一个数”默认不能为 0；同乘 0 会把不同的数都变成 0，等式虽然成立，但原等式的信息丢失了。',
        createdAt: isoAgo(1200),
      }],
      homeworkId: 2,
      homeworkTitle: '一元一次方程 · 第 4 课时（已发布示例）',
    },
  ]
}

function makeTemplates(): PublicTemplate[] {
  return [
    {
      id: 1,
      title: '「去括号」讲评骨架模板',
      category: '方程',
      structure: { sections: ['考查点', '常见错误', '正确思路', '变式练习'] },
      content: '适用于含负号括号的方程讲评，重点呈现变号步骤。',
      status: 'APPROVED',
      createdBy: 2,
      createdByName: '王老师',
      reviewedBy: 1,
      reviewNote: '结构清晰，可通过。',
      reviewedAt: isoAgo(1400),
      createdAt: isoAgo(2800),
      updatedAt: isoAgo(1400),
    },
    {
      id: 2,
      title: '「计算粗心」讲评模板',
      category: '习惯',
      structure: { sections: ['整体情况', '典型错误', '改进建议'] },
      content: '强调“粗心、不认真”等表述。',
      status: 'REJECTED',
      createdBy: 2,
      createdByName: '王老师',
      reviewedBy: 1,
      reviewNote: '含可能标签化学生的表述，请改为具体错误类型（R16）。',
      reviewedAt: isoAgo(1380),
      createdAt: isoAgo(2700),
      updatedAt: isoAgo(1380),
    },
    {
      id: 3,
      title: '「移项」讲评骨架模板',
      category: '方程',
      structure: { sections: ['核心依据', '典型错误', '板演步骤', '自查清单'] },
      content: '围绕等式性质说明移项变号，并给出 3 条自查问题。',
      status: 'PENDING',
      createdBy: 3,
      createdByName: '李老师（他班）',
      reviewedBy: null,
      reviewNote: null,
      reviewedAt: null,
      createdAt: isoAgo(300),
      updatedAt: isoAgo(300),
    },
    {
      id: 4,
      title: '「应用题设元」讲评模板',
      category: '应用',
      structure: { sections: ['等量关系', '设元方式', '常见误区'] },
      content: '先找等量关系，再设未知数；避免直接给结论。',
      status: 'PENDING',
      createdBy: 2,
      createdByName: '王老师',
      reviewedBy: null,
      reviewNote: null,
      reviewedAt: null,
      createdAt: isoAgo(180),
      updatedAt: isoAgo(180),
    },
  ]
}

function makeLogs(): OperationLog[] {
  return [
    { id: 1, actorId: 2, actorRole: 'TEACHER', actorName: '王老师', action: 'LOGIN', targetType: 'USER', targetId: 2, summary: '教师登录成功', detail: null, createdAt: isoAgo(180), ip: '127.0.0.1' },
    { id: 2, actorId: 2, actorRole: 'TEACHER', actorName: '王老师', action: 'IMPORT_GRADING', targetType: 'HOMEWORK', targetId: 1, summary: '导入批改批次 V2，45 × 8 条', detail: { batchId: 2 }, createdAt: isoAgo(50), ip: '127.0.0.1' },
    { id: 3, actorId: 2, actorRole: 'TEACHER', actorName: '王老师', action: 'GENERATE_AI', targetType: 'HOMEWORK', targetId: 1, summary: '生成 8 条讲评草稿：成功 6、依据不足 1、超时 1', detail: { draftCount: 8 }, createdAt: isoAgo(38), ip: '127.0.0.1' },
    { id: 4, actorId: 2, actorRole: 'TEACHER', actorName: '王老师', action: 'PUBLISH', targetType: 'HANDOUT', targetId: 2, summary: '发布讲评，3 条可见', detail: { itemCount: 3 }, createdAt: isoAgo(120), ip: '127.0.0.1' },
    { id: 5, actorId: 4, actorRole: 'STUDENT', actorName: '小李', action: 'SUBMIT_CORRECTION', targetType: 'CORRECTION', targetId: 1, summary: '提交第 9 题订正', detail: { versionNo: 1 }, createdAt: isoAgo(60), ip: '127.0.0.1' },
    { id: 6, actorId: 1, actorRole: 'RESEARCH', actorName: '陈老师（教研）', action: 'REVIEW_TEMPLATE', targetType: 'TEMPLATE', targetId: 1, summary: '通过公共模板「去括号」', detail: null, createdAt: isoAgo(1400), ip: '127.0.0.1' },
    { id: 7, actorId: 1, actorRole: 'RESEARCH', actorName: '陈老师（教研）', action: 'REVIEW_TEMPLATE', targetType: 'TEMPLATE', targetId: 2, summary: '拒绝公共模板「计算粗心」', detail: { reason: '可能标签化学生' }, createdAt: isoAgo(1380), ip: '127.0.0.1' },
    { id: 8, actorId: 4, actorRole: 'STUDENT', actorName: '小李', action: 'CREATE_DOUBT', targetType: 'DOUBT', targetId: 1, summary: '对第 10 题提交疑问', detail: null, createdAt: isoAgo(40), ip: '127.0.0.1' },
  ]
}

function refreshHandout(handout: MockHandout): void {
  const items = handout.items
  const readyCount = items.filter((item) => item.status === 'READY').length
  const pendingReviewCount = items.filter((item) => item.status === 'PENDING_REVIEW').length
  const blockedCount = items.filter((item) => ['DRAFT', 'AI_FAILED_MANUAL', 'INSUFFICIENT_EVIDENCE'].includes(item.status)).length
  const excludedCount = items.filter((item) => item.status === 'EXCLUDED').length
  const reasons: string[] = []
  if (!readyCount) reasons.push('至少需要 1 条发布就绪')
  handout.itemCount = items.length
  handout.readyCount = readyCount
  handout.pendingReviewCount = pendingReviewCount
  handout.blockedCount = blockedCount
  handout.excludedCount = excludedCount
  handout.publishable = readyCount > 0
  handout.blockingReasons = reasons
  handout.updatedAt = new Date().toISOString()
}

export function nextId(db: MockDb, key: string): number {
  const current = db.counters[key] ?? 1
  db.counters[key] = current + 1
  return current
}

export function nowIso(): string {
  return new Date().toISOString()
}

export function createDb(): MockDb {
  const questions = makeQuestions()
  const students = makeStudents()
  const knowledgePoints = makeKnowledgePoints()
  const homeworks: HomeworkDetail[] = [
    {
      id: 1,
      classId: 1,
      className: '初一(3)班',
      title: '一元一次方程 · 第 3 课时',
      description: '完成课本第 42 页练习，重点练习去括号与移项。',
      status: 'PUBLISHED',
      dueAt: isoAhead(24 * 60),
      publishedAt: isoAgo(1440),
      questionCount: 8,
      studentCount: 45,
      currentGradingBatchId: 2,
      currentGradingBatchVersion: 2,
      handoutId: 1,
      handoutStatus: 'UNPUBLISHED',
      createdAt: isoAgo(2880),
      updatedAt: isoAgo(30),
      version: 0,
      questions: questions.filter((item) => item.homeworkId === 1),
    },
    {
      id: 2,
      classId: 1,
      className: '初一(3)班',
      title: '一元一次方程 · 第 4 课时（已发布示例）',
      description: '本次讲评已发布，包含移项与等式性质。',
      status: 'PUBLISHED',
      dueAt: isoAhead(48 * 60),
      publishedAt: isoAgo(4320),
      questionCount: 3,
      studentCount: 45,
      currentGradingBatchId: 3,
      currentGradingBatchVersion: 1,
      handoutId: 2,
      handoutStatus: 'PUBLISHED',
      createdAt: isoAgo(5760),
      updatedAt: isoAgo(120),
      version: 0,
      questions: questions.filter((item) => item.homeworkId === 2),
    },
  ]

  const { batches, records } = makeRecordsAndBatches(homeworks, students, questions)
  const drafts = makeDrafts()
  const handouts = makeHandouts(drafts)
  for (const handout of handouts) refreshHandout(handout)
  // 种子中的草稿按 SQL 快照固定状态，refresh 仅重算计数，不改变状态。
  const corrections = makeCorrections()
  const doubts = makeDoubts()
  const templates = makeTemplates()
  const logs = makeLogs()

  return {
    users: [
      {
        id: 1,
        username: 'research01',
        password: '123456',
        displayName: '陈老师（教研）',
        status: 'ACTIVE',
        roles: ['RESEARCH'],
        studentNo: null,
        className: null,
        classNames: [],
        classIds: [],
        studentId: null,
      },
      {
        id: 2,
        username: 'teacher01',
        password: '123456',
        displayName: '王老师',
        status: 'ACTIVE',
        roles: ['TEACHER'],
        studentNo: null,
        className: '初一(3)班',
        classNames: ['初一(3)班'],
        classIds: [1],
        studentId: null,
      },
      {
        id: 3,
        username: 'teacher02',
        password: '123456',
        displayName: '李老师（他班）',
        status: 'ACTIVE',
        roles: ['TEACHER'],
        studentNo: null,
        className: '初一(4)班',
        classNames: ['初一(4)班'],
        classIds: [2],
        studentId: null,
      },
      {
        id: 4,
        username: 'S2024013',
        password: '123456',
        displayName: '小李',
        status: 'ACTIVE',
        roles: ['STUDENT'],
        studentNo: 'S2024013',
        className: '初一(3)班',
        classNames: ['初一(3)班'],
        classIds: [1],
        studentId: 113,
      },
    ],
    classes: [
      { id: 1, name: '初一(3)班', grade: '初一', subject: '数学', studentCount: 45, activeHomeworkCount: 2 },
      { id: 2, name: '初一(4)班', grade: '初一', subject: '数学', studentCount: 3, activeHomeworkCount: 0 },
    ],
    students,
    teacherClasses: [
      { teacherUserId: 2, classId: 1 },
      { teacherUserId: 3, classId: 2 },
    ],
    homeworks,
    questions,
    knowledgePoints,
    batches,
    records,
    handouts,
    corrections,
    doubts,
    templates,
    operationLogs: logs,
    aiOnline: true,
    counters: {
      class: 1000,
      student: 1000,
      homework: 10,
      question: 100,
      batch: 100,
      record: 20_000,
      handout: 10,
      item: 100,
      version: 100,
      draft: 5000,
      correction: 100,
      doubt: 100,
      reply: 100,
      knowledgePoint: 100,
      template: 100,
      log: 1000,
    },
  }
}

const MOCK_DB_KEY = 'mh.mockDb.v1'

function loadDb(): MockDb {
  try {
    const raw = sessionStorage.getItem(MOCK_DB_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as MockDb
      if (parsed && Array.isArray(parsed.users) && Array.isArray(parsed.records) && Array.isArray(parsed.handouts)) {
        return parsed
      }
    }
  } catch {
    sessionStorage.removeItem(MOCK_DB_KEY)
  }
  return createDb()
}

export let db: MockDb = loadDb()

export function persistDb(): void {
  try {
    sessionStorage.setItem(MOCK_DB_KEY, JSON.stringify(db))
  } catch {
    // 演示数据写入失败不阻塞页面操作。
  }
}

export function resetDb(): void {
  db = createDb()
  sessionStorage.removeItem(MOCK_DB_KEY)
  attemptStore.clear()
  currentMockUserId = null
}

export const attemptStore = new Map<string, { failures: number; lockedUntil: number }>()
export let currentMockUserId: number | null = null

export function setCurrentMockUser(id: number | null): void {
  currentMockUserId = id
}

export function refreshHandoutState(handout: MockHandout): void {
  refreshHandout(handout)
}

export function questionIn(questionId: number): Question | undefined {
  return db.questions.find((item) => item.id === questionId)
}

export function handoutPath(handoutId: number): MockHandout | undefined {
  return db.handouts.find((item) => item.id === handoutId)
}

export { isoAgo, isoAhead }
