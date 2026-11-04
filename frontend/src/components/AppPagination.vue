<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    page: number
    pageSize: number
    total: number
    unit?: string
  }>(),
  { unit: '条' },
)

const emit = defineEmits<{
  (e: 'update:page', value: number): void
}>()

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / Math.max(1, props.pageSize))))

function go(page: number): void {
  if (page < 1 || page > totalPages.value || page === props.page) return
  emit('update:page', page)
}
</script>

<template>
  <div v-if="total > pageSize" class="app-pagination">
    <span>共 {{ total }} {{ unit }} · 第 {{ page }} / {{ totalPages }} 页</span>
    <div>
      <button class="btn btn--ghost btn--xs" type="button" :disabled="page <= 1" @click="go(page - 1)">
        上一页
      </button>
      <button
        class="btn btn--ghost btn--xs"
        type="button"
        :disabled="page >= totalPages"
        @click="go(page + 1)"
      >
        下一页
      </button>
    </div>
  </div>
</template>

<style scoped>
.app-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--line);
  color: var(--ink-3);
  font-size: 9px;
}

.app-pagination > div {
  display: flex;
  gap: 5px;
}
</style>
