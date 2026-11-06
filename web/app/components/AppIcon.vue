<script setup lang="ts">
/**
 * The app's icon set, inlined.
 *
 * Every glyph is a 24×24 stroked path, so one component covers the whole UI
 * without a font or an icon package — and nothing has to be fetched at
 * runtime. Category icons are drawn by `CategoryBadge` from the category's own
 * colour instead, so this set only needs the chrome.
 */
const props = withDefaults(defineProps<{ name: string; size?: number | string; strokeWidth?: number }>(), {
  size: 20,
  strokeWidth: 1.75,
})

const paths: Record<string, string> = {
  home: 'M3 10.5 12 3l9 7.5M5.5 9.5V20h13V9.5M10 20v-6h4v6',
  list: 'M8 6h13M8 12h13M8 18h13M3.5 6h.01M3.5 12h.01M3.5 18h.01',
  wallet: 'M3 7.5A2.5 2.5 0 0 1 5.5 5h13A2.5 2.5 0 0 1 21 7.5v9A2.5 2.5 0 0 1 18.5 19h-13A2.5 2.5 0 0 1 3 16.5v-9ZM3 10h18M16.5 14.5h.01',
  'pie-chart': 'M12 3a9 9 0 1 0 9 9h-9V3Z',
  target: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18ZM12 17a5 5 0 1 0 0-10 5 5 0 0 0 0 10ZM12 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2Z',
  receipt: 'M6 3h12v18l-3-2-3 2-3-2-3 2V3ZM9.5 8h5M9.5 12h5',
  plus: 'M12 5v14M5 12h14',
  minus: 'M5 12h14',
  camera: 'M3 8.5A2 2 0 0 1 5 6.5h1.8l1.2-2h8l1.2 2H19a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-9ZM12 16.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Z',
  search: 'M10.5 17a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13ZM15.5 15.5 20 20',
  filter: 'M3 6h18l-7 8v6l-4-2v-4L3 6Z',
  'chevron-left': 'M15 5l-7 7 7 7',
  'chevron-right': 'M9 5l7 7-7 7',
  'chevron-down': 'M5 9l7 7 7-7',
  'chevron-up': 'M19 15l-7-7-7 7',
  settings: 'M12 15.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7ZM19.4 15a1.7 1.7 0 0 0 .34 1.87l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.7 1.7 0 0 0-1.87-.34 1.7 1.7 0 0 0-1.03 1.56V21a2 2 0 1 1-4 0v-.09A1.7 1.7 0 0 0 8.9 19.3a1.7 1.7 0 0 0-1.87.35l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.7 1.7 0 0 0 .34-1.87 1.7 1.7 0 0 0-1.56-1.03H3a2 2 0 1 1 0-4h.09a1.7 1.7 0 0 0 1.51-1.11 1.7 1.7 0 0 0-.34-1.87l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.7 1.7 0 0 0 1.87.34h.08A1.7 1.7 0 0 0 10.1 3.1V3a2 2 0 1 1 4 0v.09a1.7 1.7 0 0 0 1.03 1.56 1.7 1.7 0 0 0 1.87-.34l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.7 1.7 0 0 0-.34 1.87v.08a1.7 1.7 0 0 0 1.56 1.03H21a2 2 0 1 1 0 4h-.09a1.7 1.7 0 0 0-1.51 1.03Z',
  bell: 'M18 9A6 6 0 0 0 6 9c0 5-2.5 6-2.5 6h17S18 14 18 9ZM13.7 19.5a2 2 0 0 1-3.4 0',
  logout: 'M9 20H5.5A1.5 1.5 0 0 1 4 18.5v-13A1.5 1.5 0 0 1 5.5 4H9M16 8l4 4-4 4M20 12H9',
  check: 'M4.5 12.5 9.5 17.5 20 7',
  x: 'M6 6l12 12M18 6 6 18',
  'alert-triangle': 'M12 4 2.5 20h19L12 4ZM12 10v4M12 17.5h.01',
  info: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18ZM12 11v5M12 8h.01',
  'trending-up': 'M3 17l6-6 4 4 8-8M15 7h6v6',
  'trending-down': 'M3 7l6 6 4-4 8 8M15 17h6v-6',
  'arrow-right-left': 'M7 8h14l-3.5-3.5M17 16H3l3.5 3.5',
  'arrow-down-left': 'M17 7 7 17M7 10v7h7',
  'arrow-up-right': 'M7 17 17 7M17 14V7h-7',
  calendar: 'M4 7.5A1.5 1.5 0 0 1 5.5 6h13A1.5 1.5 0 0 1 20 7.5v11A1.5 1.5 0 0 1 18.5 20h-13A1.5 1.5 0 0 1 4 18.5v-11ZM4 10.5h16M8.5 4v4M15.5 4v4',
  download: 'M12 4v11M7.5 10.5 12 15l4.5-4.5M4.5 19.5h15',
  upload: 'M12 16V5M7.5 9.5 12 5l4.5 4.5M4.5 19.5h15',
  edit: 'M4 20h4L19.5 8.5a2.12 2.12 0 0 0-3-3L5 17v3Z',
  trash: 'M4.5 7.5h15M9 7.5V5h6v2.5M6.5 7.5 7.5 20h9l1-12.5M10.5 11v5M13.5 11v5',
  sparkles: 'M12 3.5 13.7 9l5.3 1.7-5.3 1.7L12 18l-1.7-5.6L5 10.7 10.3 9 12 3.5ZM19 15.5l.7 2 2 .7-2 .7-.7 2-.7-2-2-.7 2-.7.7-2Z',
  'piggy-bank': 'M4 12.5A5.5 5.5 0 0 1 9.5 7h5A5.5 5.5 0 0 1 20 12.5v2A2.5 2.5 0 0 1 17.5 17h-11A2.5 2.5 0 0 1 4 14.5v-2ZM7 17v2.5M16.5 17v2.5M16 11.5h.01M4 11l-2 1 2 1',
  'credit-card': 'M3 8a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8ZM3 11h18M6.5 14.5h3',
  bank: 'M4 10h16M5 10V9l7-4 7 4v1M6.5 10v8M11 10v8M17.5 10v8M3.5 18.5h17',
  phone: 'M7 3.5h10a1.5 1.5 0 0 1 1.5 1.5v14a1.5 1.5 0 0 1-1.5 1.5H7a1.5 1.5 0 0 1-1.5-1.5V5A1.5 1.5 0 0 1 7 3.5ZM10.5 17.5h3',
  'file-text': 'M6 3h8l4 4v14H6V3ZM14 3v4h4M9 12h6M9 16h6',
  user: 'M12 11.5a3.75 3.75 0 1 0 0-7.5 3.75 3.75 0 0 0 0 7.5ZM4.5 20.5c0-3.3 3.4-5.5 7.5-5.5s7.5 2.2 7.5 5.5',
  shield: 'M12 3.5 5 6v6c0 4 3 7 7 8.5 4-1.5 7-4.5 7-8.5V6l-7-2.5ZM9.5 12l2 2 3.5-3.5',
  lock: 'M6.5 11V8a5.5 5.5 0 0 1 11 0v3M5 11h14v9H5v-9ZM12 15v2',
  refresh: 'M20 11.5A8 8 0 0 0 6.3 6.3L4 8.5M4 12.5a8 8 0 0 0 13.7 5.2L20 15.5M4 4.5v4h4M20 19.5v-4h-4',
  folder: 'M3 7a2 2 0 0 1 2-2h4l2 2.5h8a2 2 0 0 1 2 2V17a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z',
  tag: 'M4 11.5V5a1 1 0 0 1 1-1h6.5l8 8-7.5 7.5-8-8ZM8 8h.01',
  menu: 'M3 6h18M3 12h18M3 18h18',
  split: 'M4 6h5l5 6h6M4 18h5l2.5-3M17 9l3 3-3 3',
  link: 'M9.5 14.5 14.5 9.5M8 12 5.5 14.5a3.5 3.5 0 0 0 5 5L13 17M16 12l2.5-2.5a3.5 3.5 0 0 0-5-5L11 7',
  eye: 'M2.5 12S6 6 12 6s9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6ZM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z',
  google: 'M21 12.2c0-.7-.06-1.2-.17-1.8H12v3.4h5.1a4.4 4.4 0 0 1-1.9 2.9v2.4h3.1A9.2 9.2 0 0 0 21 12.2ZM12 21c2.4 0 4.5-.8 6-2.2l-3.1-2.4a5.6 5.6 0 0 1-8.3-2.9H3.4v2.5A9.3 9.3 0 0 0 12 21ZM6.6 13.5a5.6 5.6 0 0 1 0-3.5V7.5H3.4a9.3 9.3 0 0 0 0 8.4l3.2-2.4ZM12 6.4c1.3 0 2.5.45 3.5 1.35l2.6-2.6A9 9 0 0 0 3.4 7.5l3.2 2.5A5.6 5.6 0 0 1 12 6.4Z',
}

const path = computed(() => paths[props.name] ?? paths.info!)
const dimension = computed(() => (typeof props.size === 'number' ? `${props.size}px` : props.size))
</script>

<template>
  <svg
    :width="dimension"
    :height="dimension"
    viewBox="0 0 24 24"
    fill="none"
    :stroke-width="strokeWidth"
    stroke="currentColor"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    class="shrink-0"
  >
    <path :d="path" />
  </svg>
</template>
