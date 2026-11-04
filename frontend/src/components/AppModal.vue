<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'
import AppIcon from './AppIcon.vue'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    subtitle?: string
    size?: 'sm' | 'md' | 'lg'
    closeOnBackdrop?: boolean
  }>(),
  {
    subtitle: '',
    size: 'md',
    closeOnBackdrop: true,
  },
)

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
}>()

function close(): void {
  emit('update:modelValue', false)
}

function onBackdrop(): void {
  if (props.closeOnBackdrop) close()
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && props.modelValue) close()
}

watch(
  () => props.modelValue,
  (open) => {
    document.body.style.overflow = open ? 'hidden' : ''
    if (open) window.addEventListener('keydown', onKeydown)
    else window.removeEventListener('keydown', onKeydown)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  document.body.style.overflow = ''
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="modelValue" class="modal-mask" @mousedown.self="onBackdrop">
        <section
          class="modal-panel"
          :class="`modal-panel--${size}`"
          role="dialog"
          aria-modal="true"
          :aria-label="title"
        >
          <header class="modal-head">
            <div>
              <h2>{{ title }}</h2>
              <p v-if="subtitle">{{ subtitle }}</p>
            </div>
            <button class="icon-btn" type="button" aria-label="关闭" @click="close">
              <AppIcon name="x" :size="19" />
            </button>
          </header>
          <div class="modal-body">
            <slot />
          </div>
          <footer v-if="$slots.footer" class="modal-foot">
            <slot name="footer" />
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 22px;
  background: rgba(32, 50, 44, 0.34);
  backdrop-filter: blur(7px);
}

.modal-panel {
  width: min(560px, 100%);
  max-height: min(88vh, 940px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 24px;
  background: #fff;
  box-shadow: 0 40px 100px -36px rgba(20, 53, 43, 0.6);
}

.modal-panel--sm {
  width: min(430px, 100%);
}

.modal-panel--lg {
  width: min(820px, 100%);
}

.modal-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding: 22px 24px 16px;
  border-bottom: 1px solid var(--line);
  background:
    radial-gradient(circle at 100% 0, color-mix(in srgb, var(--role) 10%, transparent), transparent 55%),
    #fff;
}

.modal-head h2 {
  font-family: var(--font-display);
  font-size: 23px;
  font-weight: 400;
  letter-spacing: 0.02em;
}

.modal-head p {
  margin-top: 4px;
  color: var(--ink-3);
  font-size: 12px;
  line-height: 1.7;
}

.modal-body {
  overflow-y: auto;
  padding: 22px 24px;
}

.modal-foot {
  display: flex;
  justify-content: flex-end;
  gap: 9px;
  padding: 15px 24px 20px;
  border-top: 1px solid var(--line);
  background: #fcfefd;
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.22s ease;
}

.modal-enter-active .modal-panel,
.modal-leave-active .modal-panel {
  transition: transform 0.32s var(--ease-out), opacity 0.25s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .modal-panel,
.modal-leave-to .modal-panel {
  opacity: 0;
  transform: translateY(18px) scale(0.975);
}

@media (max-width: 620px) {
  .modal-mask {
    align-items: flex-end;
    padding: 0;
  }

  .modal-panel,
  .modal-panel--sm,
  .modal-panel--lg {
    width: 100%;
    max-height: 92vh;
    border-radius: 22px 22px 0 0;
  }

  .modal-head,
  .modal-body {
    padding-right: 18px;
    padding-left: 18px;
  }

  .modal-foot {
    padding-right: 18px;
    padding-left: 18px;
  }
}
</style>
