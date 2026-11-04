import { onBeforeUnmount, type Ref } from 'vue'

export function useOutsideClick(
  element: Ref<HTMLElement | null>,
  handler: () => void,
): () => void {
  function listener(event: MouseEvent): void {
    const target = event.target as Node | null
    if (!element.value || !target || element.value.contains(target)) return
    handler()
  }

  document.addEventListener('mousedown', listener)
  onBeforeUnmount(() => document.removeEventListener('mousedown', listener))
  return () => document.removeEventListener('mousedown', listener)
}
