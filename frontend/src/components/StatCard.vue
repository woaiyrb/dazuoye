<script setup lang="ts">
import AppIcon, { type IconName } from './AppIcon.vue'
import type { Tone } from '@/types/labels'

withDefaults(
  defineProps<{
    label: string
    value: string | number
    suffix?: string
    note?: string
    icon: IconName
    tone?: Tone
    trend?: string
  }>(),
  {
    suffix: '',
    note: '',
    tone: 'mint',
    trend: '',
  },
)
</script>

<template>
  <article class="stat-card" :data-tone="tone">
    <div class="stat-card__top">
      <span class="stat-card__label">{{ label }}</span>
      <span class="stat-card__icon">
        <AppIcon :name="icon" :size="18" :stroke-width="1.6" />
      </span>
    </div>
    <div class="stat-card__value">
      <span class="numbers">{{ value }}</span>
      <small v-if="suffix">{{ suffix }}</small>
    </div>
    <div class="stat-card__bottom">
      <span v-if="trend" class="stat-card__trend">{{ trend }}</span>
      <span v-if="note" class="stat-card__note">{{ note }}</span>
    </div>
    <svg class="stat-card__arc" viewBox="0 0 120 50" fill="none" aria-hidden="true">
      <path d="M2 44c18-5 26-22 42-22 17 0 21 17 37 17 13 0 20-11 37-24" stroke="currentColor" stroke-opacity=".18" stroke-width="1.2" />
      <circle cx="42" cy="22" r="2" fill="currentColor" fill-opacity=".28" />
      <circle cx="81" cy="39" r="2" fill="currentColor" fill-opacity=".28" />
    </svg>
  </article>
</template>

<style scoped>
.stat-card {
  position: relative;
  min-height: 136px;
  padding: 18px 19px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  background: #fff;
  box-shadow: var(--shadow-sm);
  animation: rise 0.5s var(--ease-out) both;
}

.stat-card::before {
  content: '';
  position: absolute;
  top: -50px;
  right: -40px;
  width: 130px;
  height: 130px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.035;
}

.stat-card[data-tone='mint'] {
  color: var(--mint);
}

.stat-card[data-tone='peri'] {
  color: var(--peri);
}

.stat-card[data-tone='apricot'] {
  color: var(--apricot);
}

.stat-card[data-tone='lav'] {
  color: var(--lav);
}

.stat-card[data-tone='coral'] {
  color: var(--coral);
}

.stat-card[data-tone='slate'] {
  color: #81948d;
}

.stat-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.stat-card__label {
  color: var(--ink-3);
  font-size: 12px;
  font-weight: 500;
}

.stat-card__icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  color: currentColor;
  background: color-mix(in srgb, currentColor 9%, #fff);
}

.stat-card__value {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-top: 8px;
  color: var(--ink);
}

.stat-card__value .numbers {
  font-size: 35px;
  font-weight: 600;
  line-height: 1.08;
  letter-spacing: -0.02em;
}

.stat-card__value small {
  color: var(--ink-3);
  font-size: 11.5px;
  font-weight: 500;
}

.stat-card__bottom {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 20px;
  margin-top: 6px;
  font-size: 10.5px;
}

.stat-card__trend {
  color: currentColor;
  font-weight: 700;
}

.stat-card__note {
  overflow: hidden;
  color: var(--ink-3);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stat-card__arc {
  position: absolute;
  right: 12px;
  bottom: 12px;
  width: 104px;
  height: 44px;
  color: currentColor;
  pointer-events: none;
}
</style>
