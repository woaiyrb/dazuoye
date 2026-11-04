import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi } from '@/api/auth'
import { errorMessage } from '@/api/errors'
import type { AuthMode, CurrentUser, LoginResult, PortalRole, RoleCode } from '@/types/models'

const AUTH_KEY = 'mh.session.v1'
const TOKEN_KEY = 'mh.accessToken'
const REMEMBER_KEY = 'mh.rememberedUsername'

export interface SessionState {
  user: CurrentUser
  authMode: AuthMode
  accessToken: string | null
  expiresAt: string
}

function readStoredSession(): SessionState | null {
  const raw = localStorage.getItem(AUTH_KEY)
  if (!raw) return null
  try {
    const session = JSON.parse(raw) as SessionState
    if (!session.user) return null
    if (session.expiresAt && new Date(session.expiresAt).getTime() < Date.now()) {
      localStorage.removeItem(AUTH_KEY)
      localStorage.removeItem(TOKEN_KEY)
      return null
    }
    return session
  } catch {
    localStorage.removeItem(AUTH_KEY)
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const stored = readStoredSession()
  const session = ref<SessionState | null>(stored)
  const user = ref<CurrentUser | null>(stored?.user ?? null)
  const loading = ref(false)
  const error = ref('')
  const rememberedUsername = ref(localStorage.getItem(REMEMBER_KEY) ?? '')

  const isAuthenticated = computed(() => Boolean(user.value))
  const role = computed<RoleCode | null>(() => user.value?.roles[0] ?? null)
  const displayName = computed(() => user.value?.displayName ?? '')
  const homePath = computed(() => {
    switch (role.value) {
      case 'TEACHER':
        return '/teacher'
      case 'STUDENT':
        return '/student'
      case 'RESEARCH':
        return '/research'
      default:
        return '/login'
    }
  })

  function persist(result: LoginResult): void {
    const next: SessionState = {
      user: result.user,
      authMode: result.authMode,
      accessToken: result.accessToken,
      expiresAt: result.expiresAt,
    }
    session.value = next
    user.value = result.user
    localStorage.setItem(AUTH_KEY, JSON.stringify(next))
    if (result.accessToken) {
      localStorage.setItem(TOKEN_KEY, result.accessToken)
    } else {
      localStorage.removeItem(TOKEN_KEY)
    }
  }

  function clear(): void {
    session.value = null
    user.value = null
    error.value = ''
    localStorage.removeItem(AUTH_KEY)
    localStorage.removeItem(TOKEN_KEY)
  }

  async function login(username: string, password: string, portal: PortalRole): Promise<void> {
    loading.value = true
    error.value = ''
    try {
      const result = await authApi.login({ username, password, portal })
      persist(result)
      rememberedUsername.value = username
      localStorage.setItem(REMEMBER_KEY, username)
    } catch (cause) {
      error.value = errorMessage(cause)
      throw cause
    } finally {
      loading.value = false
    }
  }

  function rememberUsername(username: string, remember: boolean): void {
    if (remember) {
      rememberedUsername.value = username
      localStorage.setItem(REMEMBER_KEY, username)
    } else {
      rememberedUsername.value = ''
      localStorage.removeItem(REMEMBER_KEY)
    }
  }

  async function logout(): Promise<void> {
    try {
      await authApi.logout()
    } catch {
      // 会话清理优先，后端注销失败不阻塞退出。
    } finally {
      clear()
    }
  }

  async function refreshMe(): Promise<void> {
    if (!session.value) return
    try {
      const current = await authApi.me()
      user.value = current
      if (session.value) {
        session.value = { ...session.value, user: current }
        localStorage.setItem(AUTH_KEY, JSON.stringify(session.value))
      }
    } catch {
      clear()
    }
  }

  window.addEventListener('auth:expired', clear)

  return {
    session,
    user,
    loading,
    error,
    rememberedUsername,
    isAuthenticated,
    role,
    displayName,
    homePath,
    login,
    logout,
    clear,
    refreshMe,
    rememberUsername,
  }
})
