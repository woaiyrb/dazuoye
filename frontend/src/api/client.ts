import axios, { AxiosError } from 'axios'
import type { ApiResponse } from '@/types/models'
import { ApiError } from './errors'

export const apiMode = (import.meta.env.VITE_API_MODE ?? 'http') as 'mock' | 'http'
export const isMockMode = apiMode === 'mock'

export interface RequestOptions {
  params?: object
  data?: unknown
  headers?: Record<string, string>
  timeout?: number
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 35_000,
  withCredentials: true,
  headers: { Accept: 'application/json' },
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('mh.accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiResponse<unknown>>) => {
    const body = error.response?.data
    const code = body?.code || 'INTERNAL_ERROR'
    const isLoginAttempt =
      error.config?.method?.toLowerCase() === 'post' &&
      (error.config?.url ?? '').endsWith('/auth/session')
    if (error.response?.status === 401 && !isLoginAttempt) {
      window.dispatchEvent(new CustomEvent('auth:expired'))
    }
    return Promise.reject(
      new ApiError(
        code,
        body?.message || error.message || '网络请求失败',
        error.response?.status ?? 0,
        body?.data,
        body?.traceId,
      ),
    )
  },
)

export async function request<T>(
  method: string,
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  if (isMockMode) {
    const { mockRequest } = await import('./mock/router')
    return mockRequest<T>(method, path, options)
  }

  try {
    const response = await http.request<ApiResponse<T> | T>({
      method,
      url: path,
      params: options.params,
      data: options.data,
      headers: options.headers,
      timeout: options.timeout,
    })

    if (response.status === 204 || response.data === '') {
      return undefined as T
    }

    const payload = response.data as ApiResponse<T>
    if (payload && typeof payload === 'object' && 'code' in payload) {
      if (payload.code !== 'OK') {
        throw new ApiError(payload.code, payload.message || '请求失败', response.status, payload.data, payload.traceId)
      }
      return payload.data as T
    }
    return response.data as T
  } catch (error) {
    if (error instanceof ApiError) throw error
    if (error instanceof AxiosError) {
      const body = error.response?.data as ApiResponse<unknown> | undefined
      throw new ApiError(
        body?.code || 'INTERNAL_ERROR',
        body?.message || error.message,
        error.response?.status ?? 0,
        body?.data,
        body?.traceId,
      )
    }
    throw error
  }
}

export { http }
