import type { AiDraft, AiExplainResult } from '@/types/models'
import { request } from './client'

export const aiApi = {
  generateDraft(payload: { homeworkId: number; questionId: number }) {
    return request<AiDraft>('POST', '/ai/draft', {
      data: payload,
      timeout: 180_000,
    })
  },

  // 首版契约固定返回 501 FEATURE_DISABLED；学生端仅保留禁用态说明。
  explain(payload: { handoutItemId: number; question: string }) {
    return request<AiExplainResult>('POST', '/ai/explain', { data: payload })
  },
}
