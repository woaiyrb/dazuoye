import type {
  CorrectionRecord,
  Doubt,
  PageResult,
  StudentHandoutDetail,
  StudentHandoutSummary,
  StudentHomeworkDetail,
  StudentHomeworkResult,
  StudentHomeworkSummary,
  StudentScoreSummary,
  StudentWrongQuestion,
} from '@/types/models'
import { request } from './client'

export interface StudentCorrectionPayload {
  handoutItemId: number
  selectedAnswer?: string | null
  content?: string | null
}

export interface StudentDoubtPayload {
  handoutItemId: number
  content: string
}

export const studentApi = {
  listMyHandouts() {
    return request<StudentHandoutSummary[]>('GET', '/student/handouts/mine')
  },

  getHandout(handoutId: number) {
    return request<StudentHandoutDetail>('GET', `/student/handouts/${handoutId}`)
  },

  listMyCorrections(params: { page?: number; pageSize?: number } = {}) {
    return request<PageResult<CorrectionRecord>>('GET', '/student/corrections', { params })
  },

  createCorrection(payload: StudentCorrectionPayload) {
    return request<CorrectionRecord>('POST', '/student/corrections', { data: payload })
  },

  listMyDoubts(params: { page?: number; pageSize?: number } = {}) {
    return request<PageResult<Doubt>>('GET', '/student/doubts', { params })
  },

  createDoubt(payload: StudentDoubtPayload) {
    return request<Doubt>('POST', '/student/doubts', { data: payload })
  },

  listHomeworks() {
    return request<StudentHomeworkSummary[]>('GET', '/student/homeworks')
  },

  getHomework(homeworkId: number) {
    return request<StudentHomeworkDetail>('GET', `/student/homeworks/${homeworkId}`)
  },

  submitHomework(homeworkId: number, answers: Record<number, string>) {
    return request<StudentHomeworkResult>('POST', `/student/homeworks/${homeworkId}/submit`, {
      data: { answers },
      timeout: 60_000,
    })
  },

  getLatestResult(homeworkId: number) {
    return request<StudentHomeworkResult>('GET', `/student/homeworks/${homeworkId}/result`)
  },

  getScoreSummary() {
    return request<StudentScoreSummary>('GET', '/student/score-summary')
  },

  getWrongBook(homeworkId?: number) {
    return request<StudentWrongQuestion[]>('GET', '/student/wrong-book', {
      params: homeworkId ? { homeworkId } : undefined,
    })
  },
}
