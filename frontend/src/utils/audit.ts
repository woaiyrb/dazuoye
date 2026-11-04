import type { OperationLog, RoleCode } from '@/types/models'
import type { Tone } from '@/types/labels'

export const AUDIT_ACTION: Record<string, string> = {
  LOGIN: '登录系统',
  CREATE_HOMEWORK: '新建作业',
  UPDATE_HOMEWORK: '修改作业',
  PUBLISH_HOMEWORK: '发布作业',
  ARCHIVE_HOMEWORK: '归档作业',
  ARCHIVE: '归档作业',
  CREATE_QUESTION: '新增题目',
  UPDATE_QUESTION: '修改题目',
  REVISE_QUESTION: '修订题目',
  DELETE_QUESTION: '删除题目',
  IMPORT_GRADING: '导入批改',
  REVISE_GRADING: '修订批改记录',
  GENERATE_AI: '生成 AI 草稿',
  SAVE_ITEM_DRAFT: '保存讲评草稿',
  CONFIRM_ITEM: '确认讲评条目',
  EXCLUDE_ITEM: '剔除讲评条目',
  REGENERATE_ITEM: '重新生成讲评',
  PUBLISH: '发布讲评',
  WITHDRAW: '撤回讲评',
  REPLY_DOUBT: '回复学生疑问',
  CREATE_DOUBT: '提交疑问',
  SUBMIT_CORRECTION: '提交订正',
  CREATE_KNOWLEDGE_POINT: '新增知识点',
  UPDATE_KNOWLEDGE_POINT: '重命名知识点',
  DISABLE_KNOWLEDGE_POINT: '停用知识点',
  SUBMIT_TEMPLATE: '提交公共模板',
  REVIEW_TEMPLATE: '审核公共模板',
}

export const AUDIT_TARGET: Record<string, string> = {
  USER: '账号',
  HOMEWORK: '作业',
  QUESTION: '题目',
  GRADING: '批改',
  GRADING_BATCH: '批改批次',
  GRADING_RECORD: '批改记录',
  HANDOUT: '讲评',
  HANDOUT_ITEM: '讲评条目',
  AI_DRAFT: 'AI 草稿',
  DOUBT: '疑问',
  CORRECTION: '订正',
  KNOWLEDGE_POINT: '知识点',
  TEMPLATE: '模板',
}

export const AUDIT_ROLE: Record<RoleCode | 'SYSTEM', string> = {
  TEACHER: '教师',
  STUDENT: '学生',
  RESEARCH: '教研',
  SYSTEM: '系统',
}

export function actionLabel(action: string): string {
  return AUDIT_ACTION[action] ?? action
}

export function targetLabel(targetType: string | null): string {
  if (!targetType) return '—'
  return AUDIT_TARGET[targetType] ?? targetType
}

export function actorLabel(log: OperationLog): string {
  if (log.actorRole === 'STUDENT') return '学生（脱敏）'
  if (log.actorName) return log.actorName
  return log.actorRole ? AUDIT_ROLE[log.actorRole] : '系统'
}

export function actionTone(action: string): Tone {
  if (action.includes('TEMPLATE') || action.includes('KNOWLEDGE_POINT')) return 'apricot'
  if (
    action === 'PUBLISH' ||
    action === 'WITHDRAW' ||
    action === 'GENERATE_AI' ||
    action === 'CONFIRM_ITEM' ||
    action === 'EXCLUDE_ITEM' ||
    action === 'REGENERATE_ITEM'
  ) {
    return 'lav'
  }
  if (action.includes('GRADING')) return 'mint'
  if (action === 'ARCHIVE' || action === 'REVISE_QUESTION') return 'peri'
  if (action.includes('HOMEWORK') || action.includes('QUESTION')) return 'peri'
  if (action.includes('DOUBT') || action.includes('CORRECTION')) return 'coral'
  return 'slate'
}

export function isStudentLog(log: OperationLog): boolean {
  return log.actorRole === 'STUDENT'
}

export function isResearchVisibleLog(log: OperationLog): boolean {
  if (isStudentLog(log)) return false
  if (log.targetType === 'DOUBT' || log.targetType === 'CORRECTION') return false
  if (log.targetType === 'GRADING_BATCH' || log.targetType === 'GRADING_RECORD') return false
  return !['CREATE_DOUBT', 'SUBMIT_CORRECTION', 'REPLY_DOUBT'].includes(log.action)
}

export function researchLogSummary(log: OperationLog): string {
  if (log.action === 'IMPORT_GRADING' || log.action === 'REVISE_GRADING') {
    return '批改数据操作已完成；成绩与逐题明细不向教研视图展示。'
  }
  return log.summary || '无附加说明'
}
