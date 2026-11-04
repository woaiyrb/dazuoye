import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { teacherApi } from '@/api/teacher'
import type { ClassSummary, HomeworkSummary } from '@/types/models'

const ACTIVE_HOMEWORK_KEY = 'mh.activeHomework'

export const useTeacherWorkspace = defineStore('teacherWorkspace', () => {
  const classes = ref<ClassSummary[]>([])
  const homeworks = ref<HomeworkSummary[]>([])
  const activeHomeworkId = ref<number | null>(
    Number(localStorage.getItem(ACTIVE_HOMEWORK_KEY)) || null,
  )
  const loading = ref(false)
  const loaded = ref(false)

  const activeHomework = computed(
    () => homeworks.value.find((homework) => homework.id === activeHomeworkId.value) ?? null,
  )

  async function load(force = false): Promise<void> {
    if (loading.value) return
    if (loaded.value && !force) return
    loading.value = true
    try {
      const [classList, homeworkPage] = await Promise.all([
        teacherApi.getClasses(),
        teacherApi.listHomeworks({ page: 1, pageSize: 100 }),
      ])
      classes.value = classList
      homeworks.value = homeworkPage.items
      const stored = activeHomeworkId.value
      const exists = stored && homeworkPage.items.some((homework) => homework.id === stored)
      if (!exists) {
        const preferred =
          homeworkPage.items.find((homework) => homework.handoutStatus === 'UNPUBLISHED') ??
          homeworkPage.items.find((homework) => homework.status === 'PUBLISHED') ??
          homeworkPage.items[0]
        activeHomeworkId.value = preferred?.id ?? null
      }
      if (activeHomeworkId.value) {
        localStorage.setItem(ACTIVE_HOMEWORK_KEY, String(activeHomeworkId.value))
      }
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  function selectHomework(homeworkId: number): void {
    activeHomeworkId.value = homeworkId
    localStorage.setItem(ACTIVE_HOMEWORK_KEY, String(homeworkId))
  }

  function reset(): void {
    classes.value = []
    homeworks.value = []
    activeHomeworkId.value = null
    loaded.value = false
    localStorage.removeItem(ACTIVE_HOMEWORK_KEY)
  }

  return {
    classes,
    homeworks,
    activeHomeworkId,
    activeHomework,
    loading,
    loaded,
    load,
    selectHomework,
    reset,
  }
})
