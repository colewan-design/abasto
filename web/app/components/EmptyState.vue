<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string
    description?: string
    icon?: string
    compact?: boolean
  }>(),
  { icon: 'sparkles', compact: false },
)
</script>

<template>
  <div :class="['text-center', compact ? 'py-8' : 'py-14']">
    <span class="bg-raised text-ink-faint mx-auto grid size-11 place-items-center rounded-xl">
      <AppIcon :name="icon" :size="20" />
    </span>

    <p class="text-ink mt-3 text-sm font-medium">{{ title }}</p>
    <p v-if="description" class="text-ink-faint mx-auto mt-1 max-w-sm text-sm">{{ description }}</p>

    <div v-if="$slots.default" class="mt-4 flex justify-center gap-2">
      <slot />
    </div>
  </div>
</template>
