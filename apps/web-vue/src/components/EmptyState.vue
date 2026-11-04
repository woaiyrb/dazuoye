<script setup>
import { Inbox } from "@lucide/vue";

defineProps({
  title: { type: String, default: "暂无数据" },
  description: { type: String, default: "完成相关操作后，这里会自动显示最新内容。" },
});
</script>

<template>
  <div class="empty-state">
    <div class="empty-icon"><Inbox :size="22" /></div>
    <p class="empty-title">{{ title }}</p>
    <p class="empty-description">{{ description }}</p>
    <slot />
  </div>
</template>
