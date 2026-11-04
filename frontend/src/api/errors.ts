export interface ApiErrorPayload {
  code?: string
  message?: string
  data?: unknown
  traceId?: string
}

export class ApiError extends Error {
  readonly code: string
  readonly status: number
  readonly traceId?: string
  readonly data?: unknown

  constructor(
    code: string,
    message: string,
    status = 500,
    data?: unknown,
    traceId?: string,
  ) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.data = data
    this.traceId = traceId
  }

  get isAuthError(): boolean {
    return ['AUTH_INVALID_CREDENTIALS', 'AUTH_SESSION_EXPIRED', 'AUTH_ACCOUNT_LOCKED'].includes(
      this.code,
    )
  }
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof Error) return error.message
  return '请求失败，请稍后重试'
}
