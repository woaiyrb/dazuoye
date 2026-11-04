<script setup lang="ts">
import { computed } from 'vue'

export type IconName =
  | 'home'
  | 'book'
  | 'book-open'
  | 'clipboard'
  | 'clipboard-check'
  | 'upload'
  | 'chart'
  | 'sparkles'
  | 'sparkle'
  | 'refresh'
  | 'message'
  | 'compass'
  | 'check'
  | 'check-circle'
  | 'clock'
  | 'alert-circle'
  | 'alert-triangle'
  | 'x'
  | 'x-circle'
  | 'chevron-down'
  | 'chevron-right'
  | 'chevron-left'
  | 'chevron-up'
  | 'arrow-right'
  | 'arrow-left'
  | 'search'
  | 'plus'
  | 'minus'
  | 'download'
  | 'users'
  | 'user'
  | 'eye'
  | 'eye-off'
  | 'edit'
  | 'trash'
  | 'save'
  | 'lock'
  | 'logout'
  | 'settings'
  | 'filter'
  | 'file'
  | 'calendar'
  | 'info'
  | 'shield'
  | 'layers'
  | 'menu'
  | 'send'
  | 'wand'
  | 'target'
  | 'copy'
  | 'cloud-off'
  | 'list'
  | 'external'
  | 'star'
  | 'graduation'
  | 'trending-up'
  | 'inbox'
  | 'help'
  | 'key'
  | 'bell'
  | 'sliders'
  | 'rotate'
  | 'more'
  | 'pen'
  | 'link'
  | 'grid'
  | 'check-double'

const props = withDefaults(
  defineProps<{
    name: IconName
    size?: number | string
    strokeWidth?: number
  }>(),
  {
    size: 18,
    strokeWidth: 1.7,
  },
)

const icons: Record<IconName, string> = {
  home: '<path d="M3.5 10.8 12 3.7l8.5 7.1"/><path d="M5.6 9.5v10.1h12.8V9.5"/><path d="M9.7 19.6v-5.4h4.6v5.4"/>',
  book: '<path d="M4.5 5.5A2.5 2.5 0 0 1 7 3h12.5v16.5H6.8a2.3 2.3 0 0 0-2.3 2.3V5.5Z"/><path d="M4.5 5.5v14.1"/><path d="M8 7.5h7.5"/>',
  'book-open': '<path d="M12 6.2C10.2 4.9 7.8 4.4 4.5 4.7v13.6c3.3-.3 5.7.2 7.5 1.5 1.8-1.3 4.2-1.8 7.5-1.5V4.7c-3.3-.3-5.7.2-7.5 1.5Z"/><path d="M12 6.2v13.6"/>',
  clipboard: '<rect x="5" y="5.5" width="14" height="15" rx="2.5"/><path d="M9 5.5V4h6v1.5"/><path d="M8.5 10.5h7M8.5 14h7M8.5 17.5h4"/>',
  'clipboard-check': '<rect x="5" y="5.5" width="14" height="15" rx="2.5"/><path d="M9 5.5V4h6v1.5"/><path d="m8.5 13.5 2.2 2.2 4.8-5"/>',
  upload: '<path d="M12 16V4.5"/><path d="m7.5 9 4.5-4.5L16.5 9"/><path d="M4.5 15.5v3A1.5 1.5 0 0 0 6 20h12a1.5 1.5 0 0 0 1.5-1.5v-3"/>',
  chart: '<path d="M4 20h16"/><path d="M6.5 16v-4.5"/><path d="M11 16V7.5"/><path d="M15.5 16v-7"/><path d="M20 16V4.5"/>',
  sparkles: '<path d="m12 3 1.5 4.2L17.8 8.7l-4.3 1.5L12 14.5l-1.5-4.3-4.3-1.5 4.3-1.5L12 3Z"/><path d="m18.5 14 .8 2.2 2.2.8-2.2.8-.8 2.2-.8-2.2-2.2-.8 2.2-.8.8-2.2Z"/><path d="m5.5 14 .7 1.8 1.8.7-1.8.7-.7 1.8-.7-1.8-1.8-.7 1.8-.7.7-1.8Z"/>',
  sparkle: '<path d="m12 3 1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3Z"/>',
  refresh: '<path d="M20 7v5h-5"/><path d="M19.2 12a7.5 7.5 0 1 1-2.1-5.2L20 9.5"/>',
  message: '<path d="M20 11.5a7.3 7.3 0 0 1-7.6 7.3 8.7 8.7 0 0 1-3.1-.7L5 19.5l1.2-3.7a7 7 0 0 1-1.1-3.8A7.3 7.3 0 0 1 12.7 4 7.3 7.3 0 0 1 20 11.5Z"/><path d="M8.8 11.5h.1M12.1 11.5h.1M15.4 11.5h.1"/>',
  compass: '<circle cx="12" cy="12" r="8.5"/><path d="m15.5 8.5-2 5-5 2 2-5 5-2Z"/>',
  check: '<path d="m5 12.5 4.4 4.4L19 6.5"/>',
  'check-circle': '<circle cx="12" cy="12" r="8.5"/><path d="m8.2 12.2 2.5 2.5 5.2-5.4"/>',
  'check-double': '<path d="m3.5 12.5 4 4L16 7"/><path d="m12.5 15.5 1.5 1.5L22.5 7"/>',
  clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
  save: '<path d="M5 4.5h11l3 3v12H5z"/><path d="M8 4.5v5h7v-5"/><path d="M8 19.5v-6h8v6"/>',
  'alert-circle': '<circle cx="12" cy="12" r="8.5"/><path d="M12 8v4.5M12 16h.01"/>',
  'alert-triangle': '<path d="M10.3 4.5 2.7 18a1.8 1.8 0 0 0 1.6 2.6h15.4A1.8 1.8 0 0 0 21.3 18L13.7 4.5a2 2 0 0 0-3.4 0Z"/><path d="M12 9v4.5M12 17h.01"/>',
  x: '<path d="m6 6 12 12M18 6 6 18"/>',
  'x-circle': '<circle cx="12" cy="12" r="8.5"/><path d="m9 9 6 6M15 9l-6 6"/>',
  'chevron-down': '<path d="m6 9.5 6 6 6-6"/>',
  'chevron-right': '<path d="m9 6 6 6-6 6"/>',
  'chevron-left': '<path d="m15 6-6 6 6 6"/>',
  'chevron-up': '<path d="m6 14.5 6-6 6 6"/>',
  'arrow-right': '<path d="M4.5 12h15"/><path d="m14 6.5 5.5 5.5-5.5 5.5"/>',
  'arrow-left': '<path d="M19.5 12h-15"/><path d="M10 6.5 4.5 12l5.5 5.5"/>',
  search: '<circle cx="10.8" cy="10.8" r="6.3"/><path d="m16 16 4.5 4.5"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  minus: '<path d="M5 12h14"/>',
  download: '<path d="M12 4.5V16"/><path d="m7.5 11.5 4.5 4.5 4.5-4.5"/><path d="M4.5 18.5v1A1.5 1.5 0 0 0 6 21h12a1.5 1.5 0 0 0 1.5-1.5v-1"/>',
  users: '<circle cx="9.2" cy="8.5" r="3.1"/><path d="M3.5 19.5c.4-3 2.6-5 5.7-5s5.3 2 5.7 5"/><path d="M16 5.8a3 3 0 0 1 0 5.8"/><path d="M17.2 14.8c1.9.4 3.1 2 3.4 4.2"/>',
  user: '<circle cx="12" cy="8" r="3.4"/><path d="M5.5 20c.5-3.4 2.9-5.6 6.5-5.6s6 2.2 6.5 5.6"/>',
  eye: '<path d="M2.8 12S6.2 5.8 12 5.8 21.2 12 21.2 12 17.8 18.2 12 18.2 2.8 12 2.8 12Z"/><circle cx="12" cy="12" r="3"/>',
  'eye-off': '<path d="m4 4 16 16"/><path d="M9.6 6.2A9 9 0 0 1 12 5.8c5.8 0 9.2 6.2 9.2 6.2a15 15 0 0 1-2.1 2.9"/><path d="M6.1 8.2A15.8 15.8 0 0 0 2.8 12S6.2 18.2 12 18.2a8.7 8.7 0 0 0 3.6-.8"/><path d="M10.2 10.2a2.5 2.5 0 0 0 3.6 3.6"/>',
  edit: '<path d="m14.5 5.5 4 4"/><path d="m4.5 19.5 1.1-5.2L15.9 4a2.1 2.1 0 0 1 3 0l1.1 1.1a2.1 2.1 0 0 1 0 3l-10.3 10.3-5.2 1.1Z"/>',
  pen: '<path d="m14.5 5.5 4 4"/><path d="m4.5 19.5 1.1-5.2L15.9 4a2.1 2.1 0 0 1 3 0l1.1 1.1a2.1 2.1 0 0 1 0 3l-10.3 10.3-5.2 1.1Z"/>',
  trash: '<path d="M5 7h14"/><path d="M9 7V5.3c0-.7.6-1.3 1.3-1.3h3.4c.7 0 1.3.6 1.3 1.3V7"/><path d="m6.5 7 .8 12.2c0 .8.7 1.3 1.5 1.3h6.4c.8 0 1.5-.5 1.5-1.3L17.5 7"/><path d="M10 11v5.5M14 11v5.5"/>',
  lock: '<rect x="4.5" y="10" width="15" height="10" rx="2.2"/><path d="M8 10V7.5a4 4 0 0 1 8 0V10"/><path d="M12 14v2.5"/>',
  logout: '<path d="M10 4.5H6A1.5 1.5 0 0 0 4.5 6v12A1.5 1.5 0 0 0 6 19.5h4"/><path d="M14.5 8 19 12l-4.5 4"/><path d="M19 12H9"/>',
  settings: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1A1.7 1.7 0 0 0 9 4.6a1.7 1.7 0 0 0 1-1.6v-.2h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1Z"/>',
  filter: '<path d="M4 5.5h16l-6.3 7.1v5.8l-3.4 1.6v-7.4L4 5.5Z"/>',
  file: '<path d="M13.5 3.5H7A1.5 1.5 0 0 0 5.5 5v14A1.5 1.5 0 0 0 7 20.5h10a1.5 1.5 0 0 0 1.5-1.5V8.5l-5-5Z"/><path d="M13.5 3.5v5h5"/><path d="M8.5 13h7M8.5 16.5h5"/>',
  calendar: '<rect x="4" y="5.5" width="16" height="15" rx="2.2"/><path d="M8 3.5v4M16 3.5v4M4 9.5h16"/><path d="M8 13h.01M12 13h.01M16 13h.01M8 17h.01M12 17h.01"/>',
  info: '<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5M12 8h.01"/>',
  shield: '<path d="M12 3 5 6v5.2c0 4.2 2.8 7.8 7 9.8 4.2-2 7-5.6 7-9.8V6l-7-3Z"/><path d="m9 12 2 2 4-4.3"/>',
  layers: '<path d="m12 3 9 5-9 5-9-5 9-5Z"/><path d="m3 12 9 5 9-5"/><path d="m3 16 9 5 9-5"/>',
  menu: '<path d="M4 7h16M4 12h16M4 17h16"/>',
  send: '<path d="m21 3-7.5 18-3.2-7.3L3 10.5 21 3Z"/><path d="M10.3 13.7 21 3"/>',
  wand: '<path d="m15 4 5 5"/><path d="m13 6 5 5"/><path d="M4 20 14.5 9.5"/><path d="m6 4 .7 2.3L9 7l-2.3.7L6 10l-.7-2.3L3 7l2.3-.7L6 4Z"/>',
  target: '<circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="4.6"/><circle cx="12" cy="12" r="1"/>',
  copy: '<rect x="8.5" y="8.5" width="11" height="11" rx="2"/><path d="M15.5 8.5v-2a2 2 0 0 0-2-2h-7a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h2"/>',
  'cloud-off': '<path d="m3 3 18 18"/><path d="M7.8 6.2A6 6 0 0 1 18 10.5h.5a3.5 3.5 0 0 1 2.7 5.7"/><path d="M17 19H6.5a3.5 3.5 0 0 1-.4-7A6 6 0 0 1 7.8 6.2"/>',
  list: '<path d="M9 6h11M9 12h11M9 18h11"/><path d="M4 6h.01M4 12h.01M4 18h.01"/>',
  external: '<path d="M14 4.5h5.5V10"/><path d="m19.5 4.5-8 8"/><path d="M19.5 14v4.5a1.5 1.5 0 0 1-1.5 1.5H6a1.5 1.5 0 0 1-1.5-1.5V6A1.5 1.5 0 0 1 6 4.5h4.5"/>',
  star: '<path d="m12 3 2.8 5.6 6.2.9-4.5 4.4 1.1 6.2-5.6-2.9-5.6 2.9 1.1-6.2L3 9.5l6.2-.9L12 3Z"/>',
  graduation: '<path d="m2.8 9 9.2-4.5L21.2 9 12 13.5 2.8 9Z"/><path d="M6 11v4.5c0 1.7 2.7 3 6 3s6-1.3 6-3V11"/><path d="M21.2 9v5"/>',
  'trending-up': '<path d="m3.5 17 5.8-6 4.2 3.6 7-7.6"/><path d="M15.5 7h5v5"/>',
  inbox: '<path d="M4 5h16v14H4z"/><path d="M4 13h4l1.5 2.5h5L16 13h4"/>',
  help: '<circle cx="12" cy="12" r="8.5"/><path d="M9.5 9.2A2.5 2.5 0 0 1 14.5 10c0 1.8-2.5 2-2.5 3.8"/><path d="M12 17h.01"/>',
  key: '<circle cx="8" cy="15" r="3.5"/><path d="m10.5 12.5 8-8"/><path d="m16 7 2 2M13.5 9.5l2 2"/>',
  bell: '<path d="M18 9.5a6 6 0 0 0-12 0c0 7-3 7-3 7h18s-3 0-3-7Z"/><path d="M10 20h4"/>',
  sliders: '<path d="M4 7h10M18 7h2M4 17h2M10 17h10"/><circle cx="16" cy="7" r="2"/><circle cx="8" cy="17" r="2"/>',
  rotate: '<path d="M20 7v5h-5"/><path d="M19.2 12a7.5 7.5 0 1 1-2.1-5.2L20 9.5"/>',
  more: '<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
  link: '<path d="M10.5 13.5a4 4 0 0 0 5.7 0l2.3-2.3a4 4 0 1 0-5.7-5.7L11.5 6.8"/><path d="M13.5 10.5a4 4 0 0 0-5.7 0l-2.3 2.3a4 4 0 1 0 5.7 5.7l1.3-1.3"/>',
  grid: '<rect x="4" y="4" width="6" height="6" rx="1.5"/><rect x="14" y="4" width="6" height="6" rx="1.5"/><rect x="4" y="14" width="6" height="6" rx="1.5"/><rect x="14" y="14" width="6" height="6" rx="1.5"/>',
}

const inner = computed(() => icons[props.name] ?? icons.info)
</script>

<template>
  <svg
    class="icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    v-html="inner"
  />
</template>

<style scoped>
.icon {
  flex: none;
}
</style>
