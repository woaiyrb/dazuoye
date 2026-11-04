import { isMockMode, request } from './client'

export const devApi = {
  async setAiOnline(online: boolean): Promise<void> {
    if (!isMockMode) return
    await request<void>('POST', '/mock/ai-status', { data: { online } })
  },

  async resetDemoData(): Promise<void> {
    if (!isMockMode) return
    await request<void>('POST', '/mock/reset')
  },
}
