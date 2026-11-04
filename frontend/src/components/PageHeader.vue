<script setup lang="ts">
withDefaults(
  defineProps<{
    eyebrow?: string
    title: string
    subtitle?: string
  }>(),
  {
    eyebrow: '',
    subtitle: '',
  },
)
</script>

<template>
  <header class="page-header">
    <div class="page-header__copy">
      <span v-if="eyebrow" class="eyebrow">{{ eyebrow }}</span>
      <h1 class="page-title">{{ title }}</h1>
      <p v-if="subtitle" class="page-subtitle">{{ subtitle }}</p>
      <slot name="meta" />
    </div>
    <div v-if="$slots.actions" class="page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 22px;
  margin-bottom: 22px;
}

.page-header__copy {
  min-width: 0;
}

.page-header__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  flex: none;
}

@media (max-width: 760px) {
  .page-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 14px;
  }

  .page-header__actions {
    width: 100%;
  }
}
</style>
