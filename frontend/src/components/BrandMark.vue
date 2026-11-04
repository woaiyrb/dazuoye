<script setup lang="ts">
withDefaults(defineProps<{ size?: number; tone?: 'solid' | 'light' }>(), {
  size: 42,
  tone: 'solid',
})
</script>

<template>
  <span
    class="brand-mark"
    :class="`brand-mark--${tone}`"
    :style="{ width: `${size}px`, height: `${size}px` }"
    aria-hidden="true"
  >
    <svg viewBox="0 0 48 48" fill="none">
      <ellipse cx="24" cy="24" rx="17.5" ry="7.6" transform="rotate(-25 24 24)" stroke="currentColor" stroke-opacity=".34" stroke-width="1.2" />
      <circle cx="37.5" cy="12.5" r="2.2" fill="currentColor" opacity=".82" />
      <path d="M17 14.5h14l-6.4 9L31 33.5H16" stroke="currentColor" stroke-width="3.1" stroke-linecap="round" stroke-linejoin="round" />
      <circle cx="14" cy="36" r="1.3" fill="currentColor" opacity=".5" />
    </svg>
  </span>
</template>

<style scoped>
.brand-mark {
  display: inline-grid;
  place-items: center;
  flex: none;
  border-radius: 30%;
  transform: rotate(-3deg);
}

.brand-mark svg {
  width: 100%;
  height: 100%;
}

.brand-mark--solid {
  color: #fff;
  background: linear-gradient(145deg, color-mix(in srgb, var(--role) 70%, #fff), var(--role) 60%, var(--role-deep));
  box-shadow: 0 11px 24px -12px color-mix(in srgb, var(--role-deep) 60%, transparent);
}

.brand-mark--light {
  color: var(--role-deep);
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(255, 255, 255, 0.72);
  box-shadow: 0 12px 30px -18px rgba(33, 90, 75, 0.28);
  backdrop-filter: blur(12px);
}
</style>
