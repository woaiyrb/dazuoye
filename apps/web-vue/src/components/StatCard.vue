<script setup>
defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], required: true },
  unit: { type: String, default: "" },
  hint: { type: String, default: "" },
  tone: { type: String, default: "primary" },
});
</script>

<template>
  <article class="stat-card" :class="`tone-${tone}`">
    <div class="stat-topline">
      <span>{{ label }}</span>
      <span class="stat-dot" aria-hidden="true"></span>
    </div>
    <div class="stat-value-row">
      <strong>{{ value }}</strong>
      <span v-if="unit">{{ unit }}</span>
    </div>
    <p v-if="hint">{{ hint }}</p>
    <svg class="stat-arc" viewBox="0 0 120 44" fill="none" aria-hidden="true">
      <path d="M2 38c18-5 25-20 41-20 17 0 21 15 36 15 13 0 21-10 39-22" stroke="currentColor" stroke-opacity=".17" stroke-width="1.2" />
      <circle cx="43" cy="18" r="2" fill="currentColor" fill-opacity=".24" />
      <circle cx="79" cy="33" r="2" fill="currentColor" fill-opacity=".24" />
    </svg>
    <slot />
  </article>
</template>

<style scoped>
.stat-card {
  --stat-color: var(--role);
  position: relative;
  overflow: hidden;
  min-height: 132px;
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: #fff;
  padding: 17px 18px;
  box-shadow: var(--shadow-sm);
  color: var(--stat-color);
}

.stat-card::before {
  position: absolute;
  top: -50px;
  right: -42px;
  width: 132px;
  height: 132px;
  border-radius: 50%;
  background: currentColor;
  content: "";
  opacity: 0.035;
}

.tone-success { --stat-color: var(--success); }
.tone-warning { --stat-color: var(--apricot); }
.tone-accent { --stat-color: var(--coral); }
.tone-info { --stat-color: var(--peri); }

.stat-topline {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--ink-3);
  font-size: 11.5px;
  font-weight: 500;
}

.stat-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 0 4px color-mix(in srgb, currentColor 10%, white);
}

.stat-value-row {
  position: relative;
  display: flex;
  align-items: baseline;
  gap: 5px;
  margin-top: 8px;
  color: var(--ink);
}

.stat-value-row strong {
  font-family: var(--font-numbers);
  font-size: 36px;
  font-variation-settings: "opsz" 110;
  font-weight: 600;
  line-height: 1;
  letter-spacing: -0.025em;
}

.stat-value-row span,
.stat-card p {
  color: var(--ink-3);
  font-size: 10.5px;
}

.stat-card p {
  position: relative;
  margin-top: 7px;
  line-height: 1.55;
}

.stat-arc {
  position: absolute;
  right: 12px;
  bottom: 10px;
  width: 105px;
  height: 40px;
  color: currentColor;
  pointer-events: none;
}
</style>
