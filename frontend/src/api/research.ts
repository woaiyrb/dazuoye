import type {
  KnowledgePoint,
  OperationLog,
  PageResult,
  PublicTemplate,
  TemplateStatus,
} from '@/types/models'
import { request } from './client'

export interface CreateKnowledgePointPayload {
  parentId: number | null
  code: string
  name: string
  kpLevel: 1 | 2
}

export interface TemplateQuery {
  status?: TemplateStatus
  page?: number
  pageSize?: number
}

export interface LogQuery {
  action?: string
  targetType?: string
  actorId?: number
  page?: number
  pageSize?: number
}

export const researchApi = {
  listKnowledgePoints(includeDisabled = false) {
    return request<KnowledgePoint[]>('GET', '/research/knowledge-points', {
      params: { includeDisabled },
    })
  },

  createKnowledgePoint(payload: CreateKnowledgePointPayload) {
    return request<KnowledgePoint>('POST', '/research/knowledge-points', { data: payload })
  },

  updateKnowledgePoint(knowledgePointId: number, name: string) {
    return request<KnowledgePoint>('PUT', `/research/knowledge-points/${knowledgePointId}`, {
      data: { name },
    })
  },

  disableKnowledgePoint(knowledgePointId: number, impactNote: string) {
    return request<KnowledgePoint>(
      'POST',
      `/research/knowledge-points/${knowledgePointId}/disable`,
      { data: { impactNote } },
    )
  },

  listTemplates(params: TemplateQuery = {}) {
    return request<PageResult<PublicTemplate>>('GET', '/research/templates', { params })
  },

  reviewTemplate(
    templateId: number,
    payload: { decision: 'APPROVED' | 'REJECTED'; reviewNote?: string },
  ) {
    return request<PublicTemplate>('POST', `/research/templates/${templateId}/review`, {
      data: payload,
    })
  },

  listOperationLogs(params: LogQuery = {}) {
    return request<PageResult<OperationLog>>('GET', '/research/operation-logs', { params })
  },
}
