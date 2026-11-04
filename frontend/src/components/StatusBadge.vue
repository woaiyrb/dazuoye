<script setup lang="ts">
import { computed } from 'vue'
import {
  AI_DRAFT_STATUS,
  DOUBT_STATUS,
  HANDOUT_STATUS,
  HOMEWORK_STATUS,
  ITEM_STATUS,
  KP_STATUS,
  TEMPLATE_STATUS,
  type LabelMeta,
  type Tone,
} from '@/types/labels'

const props = withDefaults(
  defineProps<{
    status: string
    label?: string
    tone?: Tone
    plain?: boolean
  }>(),
  {
    label: undefined,
    tone: undefined,
    plain: false,
  },
)

const maps: Record<string, LabelMeta> = {
  ...HOMEWORK_STATUS,
  ...HANDOUT_STATUS,
  ...ITEM_STATUS,
  ...AI_DRAFT_STATUS,
  ...DOUBT_STATUS,
  ...TEMPLATE_STATUS,
  ...KP_STATUS,
}

const meta = computed<LabelMeta>(
  () => maps[props.status] ?? { label: props.status, tone: 'slate' },
)
</script>

<template>
  <span
    class="badge"
    :class="{ 'badge--plain': plain }"
    :data-tone="tone ?? meta.tone"
  >
    {{ label ?? meta.label }}
  </span>
</template>
