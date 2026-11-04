<script setup>
import katex from "katex";
import { computed } from "vue";

const props = defineProps({
  text: { type: String, default: "" },
  displayMode: { type: Boolean, default: false },
});

const parts = computed(() => {
  const segments = [];
  const pattern = /(\$\$[\s\S]+?\$\$|\$[^$]+?\$)/g;
  let lastIndex = 0;
  for (const match of props.text.matchAll(pattern)) {
    if (match.index > lastIndex) {
      segments.push({
        type: "text",
        value: props.text.slice(lastIndex, match.index),
      });
    }
    const raw = match[0];
    const display = raw.startsWith("$$");
    const expression = raw.slice(display ? 2 : 1, display ? -2 : -1);
    try {
      segments.push({
        type: "math",
        value: katex.renderToString(expression, {
          displayMode: props.displayMode || display,
          throwOnError: false,
          strict: false,
          trust: false,
          output: "html",
        }),
      });
    } catch {
      segments.push({ type: "text", value: raw });
    }
    lastIndex = match.index + raw.length;
  }
  if (lastIndex < props.text.length) {
    segments.push({ type: "text", value: props.text.slice(lastIndex) });
  }
  return segments.length ? segments : [{ type: "text", value: props.text }];
});
</script>

<template>
  <span class="math-text">
    <template v-for="(part, index) in parts" :key="index">
      <span v-if="part.type === 'math'" v-html="part.value"></span>
      <span v-else>{{ part.value }}</span>
    </template>
  </span>
</template>

<style scoped>
.math-text :deep(.katex) {
  font-size: 1em;
}
</style>
