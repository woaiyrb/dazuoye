import type {
  AiDraftStatus,
  DoubtStatus,
  EvidenceType,
  HandoutItemStatus,
  HandoutStatus,
  HomeworkStatus,
  KnowledgePointStatus,
  QuestionType,
  RoleCode,
  TemplateStatus,
} from './models'

export type Tone =
  | 'mint'
  | 'peri'
  | 'apricot'
  | 'lav'
  | 'coral'
  | 'slate'
  | 'ink'

export interface LabelMeta {
  label: string
  tone: Tone
}

export const ROLE_META: Record<RoleCode, LabelMeta & { portal: string; home: string }> = {
  TEACHER: { label: '教师', tone: 'mint', portal: '教师端', home: '/teacher' },
  STUDENT: { label: '学生', tone: 'peri', portal: '学生端', home: '/student' },
  RESEARCH: { label: '教研负责人', tone: 'apricot', portal: '教研管理', home: '/research' },
}

export const HOMEWORK_STATUS: Record<HomeworkStatus, LabelMeta> = {
  DRAFT: { label: '草稿', tone: 'slate' },
  PUBLISHED: { label: '已发布', tone: 'mint' },
  ARCHIVED: { label: '已归档', tone: 'ink' },
}

export const HANDOUT_STATUS: Record<HandoutStatus, LabelMeta> = {
  UNPUBLISHED: { label: '未发布', tone: 'apricot' },
  PUBLISHED: { label: '已发布', tone: 'mint' },
  WITHDRAWN: { label: '已撤回', tone: 'slate' },
}

export const ITEM_STATUS: Record<HandoutItemStatus, LabelMeta> = {
  DRAFT: { label: '草稿', tone: 'lav' },
  PENDING_REVIEW: { label: '待审核', tone: 'apricot' },
  READY: { label: '发布就绪', tone: 'mint' },
  EXCLUDED: { label: '已剔除', tone: 'slate' },
  INSUFFICIENT_EVIDENCE: { label: '依据不足', tone: 'coral' },
  AI_FAILED_MANUAL: { label: 'AI 失败待手工', tone: 'coral' },
}

export const AI_DRAFT_STATUS: Record<AiDraftStatus, LabelMeta> = {
  GENERATED: { label: '已生成', tone: 'mint' },
  INSUFFICIENT_EVIDENCE: { label: '依据不足', tone: 'apricot' },
  FAILED: { label: '调用失败', tone: 'coral' },
}

export const DOUBT_STATUS: Record<DoubtStatus, LabelMeta> = {
  PENDING: { label: '待回复', tone: 'apricot' },
  REPLIED: { label: '已回复', tone: 'mint' },
  CLOSED: { label: '已关闭', tone: 'slate' },
}

export const TEMPLATE_STATUS: Record<TemplateStatus, LabelMeta> = {
  PENDING: { label: '待审核', tone: 'apricot' },
  APPROVED: { label: '已通过', tone: 'mint' },
  REJECTED: { label: '已拒绝', tone: 'coral' },
}

export const KP_STATUS: Record<KnowledgePointStatus, LabelMeta> = {
  ACTIVE: { label: '启用', tone: 'mint' },
  DISABLED: { label: '已停用', tone: 'slate' },
}

export const QUESTION_TYPE: Record<QuestionType, LabelMeta> = {
  SINGLE_CHOICE: { label: '单选题', tone: 'peri' },
  TRUE_FALSE: { label: '判断题', tone: 'lav' },
}

export const EVIDENCE_LABEL: Record<EvidenceType, string> = {
  QUESTION_STEM: '原题',
  STANDARD_ANSWER: '标准答案',
  TEACHER_ANALYSIS: '教师解析',
  KNOWLEDGE_POINT: '知识点',
  STATISTICS: '统计数据',
  TEMPLATE: '公共模板',
}

export const ROLE_NAV_HINT: Record<RoleCode, string> = {
  TEACHER: '批改数据只进版本，AI 草稿只作候选',
  STUDENT: '只看自己的错题与老师确认过的讲解',
  RESEARCH: '维护知识点与模板口径，不接触班级成绩',
}
