<script setup lang="ts">
import { useUiStore } from '@/stores/ui'
import AppIcon from './AppIcon.vue'

const ui = useUiStore()
</script>

<template>
  <Teleport to="body">
    <Transition name="confirm">
      <div
        v-if="ui.confirmState.open"
        class="confirm-mask"
        @mousedown.self="ui.resolveConfirm(false)"
      >
        <div class="confirm-card" role="alertdialog" aria-modal="true">
          <div class="confirm-symbol" :class="{ 'is-danger': ui.confirmState.tone === 'danger' }">
            <AppIcon :name="ui.confirmState.tone === 'danger' ? 'alert-triangle' : 'info'" :size="22" />
          </div>
          <h3>{{ ui.confirmState.title }}</h3>
          <p>{{ ui.confirmState.message }}</p>
          <div class="confirm-actions">
            <button class="btn btn--ghost btn--sm" type="button" @click="ui.resolveConfirm(false)">
              {{ ui.confirmState.cancelText }}
            </button>
            <button
              class="btn btn--sm"
              :class="ui.confirmState.tone === 'danger' ? 'btn--danger-solid' : 'btn--primary'"
              type="button"
              @click="ui.resolveConfirm(true)"
            >
              {{ ui.confirmState.confirmText }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.confirm-mask {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(32, 50, 44, 0.32);
  backdrop-filter: blur(6px);
}

.confirm-card {
  width: min(400px, 100%);
  padding: 25px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 22px;
  background: #fff;
  box-shadow: 0 36px 90px -30px rgba(20, 53, 43, 0.55);
  animation: pop 0.26s var(--ease-out);
}

.confirm-symbol {
  display: grid;
  place-items: center;
  width: 45px;
  height: 45px;
  margin-bottom: 12px;
  border-radius: 14px;
  color: var(--role-deep);
  background: var(--role-soft);
}

.confirm-symbol.is-danger {
  color: var(--coral-deep);
  background: var(--coral-soft);
}

.confirm-card h3 {
  font-size: 17px;
  font-weight: 800;
}

.confirm-card p {
  margin-top: 7px;
  color: var(--ink-2);
  font-size: 12.5px;
  line-height: 1.8;
  white-space: pre-line;
}

.confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 20px;
}

.confirm-enter-active,
.confirm-leave-active {
  transition: opacity 0.2s ease;
}

.confirm-enter-active .confirm-card,
.confirm-leave-active .confirm-card {
  transition: transform 0.25s var(--ease-out);
}

.confirm-enter-from,
.confirm-leave-to {
  opacity: 0;
}

.confirm-enter-from .confirm-card,
.confirm-leave-to .confirm-card {
  transform: scale(0.96) translateY(8px);
}
</style>
