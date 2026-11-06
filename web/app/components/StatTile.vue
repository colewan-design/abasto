<script setup lang="ts">
withDefaults(
  defineProps<{
    label: string
    value: string
    hint?: string
    icon?: string
    tone?: 'neutral' | 'positive' | 'negative' | 'brand'
  }>(),
  { tone: 'neutral' },
)

const tones = {
  neutral: { value: 'text-ink', chip: 'bg-raised text-ink-soft' },
  positive: { value: 'text-positive', chip: 'bg-positive-soft text-positive' },
  negative: { value: 'text-negative', chip: 'bg-negative-soft text-negative' },
  brand: { value: 'text-ink', chip: 'bg-brand-soft text-brand-ink' },
}
</script>

<template>
  <div class="card p-4">
    <div class="flex items-start justify-between gap-3">
      <p class="text-ink-soft text-xs font-medium tracking-wide uppercase">{{ label }}</p>
      <span v-if="icon" :class="['grid size-7 place-items-center rounded-lg', tones[tone].chip]">
        <AppIcon :name="icon" :size="15" />
      </span>
    </div>

    <p :class="['tnum mt-2 text-xl font-semibold tracking-tight', tones[tone].value]">{{ value }}</p>
    <p v-if="hint" class="text-ink-faint mt-1 text-xs">{{ hint }}</p>
  </div>
</template>
