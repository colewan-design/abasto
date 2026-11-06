<script setup lang="ts">
import type { Category } from '~/types/api'

/**
 * A category's visual identity: a tile tinted with its own colour carrying a
 * monogram. Drawing the badge from the category record means a user-created
 * category looks exactly as finished as a built-in one.
 */
const props = withDefaults(
  defineProps<{
    category?: Category | { name: string; color?: string | null } | null
    size?: 'sm' | 'md' | 'lg'
    label?: boolean
  }>(),
  { size: 'md', label: false },
)

const name = computed(() => props.category?.name ?? 'Uncategorized')

const monogram = computed(() => {
  const words = name.value.trim().split(/[\s&/]+/).filter(Boolean)

  if (words.length === 0) {
    return '?'
  }

  if (words.length === 1) {
    return words[0]!.slice(0, 2).toUpperCase()
  }

  return (words[0]![0]! + words[1]![0]!).toUpperCase()
})

const color = computed(() => props.category?.color ?? null)

const tile = computed(() => ({
  sm: 'size-7 text-[10px] rounded-md',
  md: 'size-9 text-[11px] rounded-lg',
  lg: 'size-11 text-xs rounded-xl',
}[props.size]))
</script>

<template>
  <span class="inline-flex items-center gap-2.5">
    <span
      :class="['inline-flex items-center justify-center font-semibold tracking-tight', tile]"
      :style="color
        ? { backgroundColor: `color-mix(in oklab, ${color} 18%, transparent)`, color }
        : undefined"
      :title="name"
    >
      <span v-if="color" aria-hidden="true">{{ monogram }}</span>
      <span v-else class="text-ink-faint bg-raised flex size-full items-center justify-center rounded-[inherit]">
        {{ monogram }}
      </span>
    </span>

    <span v-if="label" class="truncate text-sm">{{ name }}</span>
  </span>
</template>
