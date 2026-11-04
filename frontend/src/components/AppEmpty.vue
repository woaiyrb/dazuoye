<script setup lang="ts">
import AppIcon, { type IconName } from './AppIcon.vue'

withDefaults(
  defineProps<{
    icon?: IconName
    title: string
    description?: string
  }>(),
  {
    icon: 'inbox',
    description: '',
  },
)
</script>

<template>
  <div class="empty-state">
    <div class="empty-state__art">
      <AppIcon :name="icon" :size="28" :stroke-width="1.5" />
    </div>
    <h3>{{ title }}</h3>
    <p v-if="description">{{ description }}</p>
    <slot />
  </div>
</template>
