import { ref } from 'vue'
import { errorMessage } from '@/api/errors'

export function useAsync() {
  const loading = ref(false)
  const error = ref('')

  async function run<T>(task: () => Promise<T>): Promise<T | null> {
    loading.value = true
    error.value = ''
    try {
      return await task()
    } catch (cause) {
      error.value = errorMessage(cause)
      return null
    } finally {
      loading.value = false
    }
  }

  return { loading, error, run }
}
