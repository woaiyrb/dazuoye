<script setup lang="ts">
import { useUiStore } from '@/stores/ui'
import AppIcon from './AppIcon.vue'

const ui = useUiStore()

const icons = {
  success: 'check-circle',
  error: 'alert-circle',
  info: 'info',
} as const
</script>

<template>
  <Teleport to="body">
    <div class="toast-host" aria-live="polite" aria-atomic="false">
      <TransitionGroup name="toast">
        <div
          v-for="item in ui.toasts"
          :key="item.id"
          class="toast"
          :class="`toast--${item.tone}`"
          role="status"
        >
          <span class="toast__icon">
            <AppIcon :name="icons[item.tone]" :size="18" />
          </span>
          <div class="toast__copy">
            <strong>{{ item.title }}</strong>
            <p v-if="item.message">{{ item.message }}</p>
          </div>
          <button class="toast__close" type="button" aria-label="关闭提示" @click="ui.dismissToast(item.id)">
            <AppIcon name="x" :size="14" />
          </button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
.toast-host {
  position: fixed;
  top: 20px;
  left: 50%;
  z-index: 300;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 9px;
  width: min(430px, calc(100vw - 28px));
  transform: translateX(-50%);
  pointer-events: none;
}

.toast {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
  padding: 12px 13px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 15px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 16px 44px -22px rgba(35, 69, 58, 0.38);
  backdrop-filter: blur(16px);
  pointer-events: all;
}

.toast__icon {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  flex: none;
  border-radius: 9px;
  color: var(--mint-deep);
  background: var(--mint-soft);
}

.toast--error .toast__icon {
  color: var(--coral-deep);
  background: var(--coral-soft);
}

.toast--info .toast__icon {
  color: var(--peri-deep);
  background: var(--peri-soft);
}

.toast__copy {
  min-width: 0;
  flex: 1;
}

.toast__copy strong {
  display: block;
  color: var(--ink);
  font-size: 12.5px;
  font-weight: 700;
  line-height: 1.5;
}

.toast__copy p {
  margin-top: 2px;
  color: var(--ink-3);
  font-size: 11.5px;
  line-height: 1.6;
}

.toast__close {
  display: grid;
  place-items: center;
  width: 23px;
  height: 23px;
  flex: none;
  border-radius: 50%;
  color: var(--ink-4);
}

.toast__close:hover {
  color: var(--ink-2);
  background: var(--surface-soft);
}

.toast-enter-active,
.toast-leave-active {
  transition: all 0.3s var(--ease-out);
}

.toast-enter-from {
  opacity: 0;
  transform: translateY(-14px) scale(0.97);
}

.toast-leave-to {
  opacity: 0;
  transform: translateY(-8px) scale(0.98);
}

.toast-move {
  transition: transform 0.25s ease;
}
</style>
