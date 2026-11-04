import { reactive, ref } from 'vue'
import { defineStore } from 'pinia'
import { devApi } from '@/api/dev'

export type ToastTone = 'success' | 'error' | 'info'

export interface Toast {
  id: number
  tone: ToastTone
  title: string
  message?: string
}

export interface ConfirmOptions {
  title: string
  message: string
  confirmText?: string
  cancelText?: string
  tone?: 'primary' | 'danger'
}

interface ConfirmState extends ConfirmOptions {
  open: boolean
  resolve: ((value: boolean) => void) | null
}

export const useUiStore = defineStore('ui', () => {
  const toasts = ref<Toast[]>([])
  const aiOnline = ref(true)
  const aiBusy = ref(false)
  const confirmState = reactive<ConfirmState>({
    open: false,
    title: '',
    message: '',
    confirmText: '确认',
    cancelText: '取消',
    tone: 'primary',
    resolve: null,
  })
  let toastSeq = 1

  function toast(tone: ToastTone, title: string, message?: string, duration = 3600): void {
    const item: Toast = { id: toastSeq++, tone, title, message }
    toasts.value.push(item)
    window.setTimeout(() => {
      toasts.value = toasts.value.filter((entry) => entry.id !== item.id)
    }, duration)
  }

  function dismissToast(id: number): void {
    toasts.value = toasts.value.filter((entry) => entry.id !== id)
  }

  function confirm(options: ConfirmOptions): Promise<boolean> {
    confirmState.open = true
    confirmState.title = options.title
    confirmState.message = options.message
    confirmState.confirmText = options.confirmText ?? '确认'
    confirmState.cancelText = options.cancelText ?? '取消'
    confirmState.tone = options.tone ?? 'primary'
    return new Promise<boolean>((resolve) => {
      confirmState.resolve = resolve
    })
  }

  function resolveConfirm(value: boolean): void {
    confirmState.open = false
    confirmState.resolve?.(value)
    confirmState.resolve = null
  }

  async function setAiOnline(online: boolean): Promise<void> {
    aiBusy.value = true
    aiOnline.value = online
    try {
      await devApi.setAiOnline(online)
      toast(
        'info',
        online ? 'AI 服务已恢复' : 'AI 服务已模拟离线',
        online ? '新的生成请求将恢复正常。' : '生成与重新生成会降级为「AI 失败待手工」。',
      )
    } finally {
      aiBusy.value = false
    }
  }

  return {
    toasts,
    aiOnline,
    aiBusy,
    confirmState,
    toast,
    dismissToast,
    confirm,
    resolveConfirm,
    setAiOnline,
  }
})
