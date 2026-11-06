<script setup lang="ts">
const { toasts, dismiss } = useToasts()

const tones = {
  info: 'bg-surface border-border text-ink',
  success: 'bg-positive-soft border-positive/30 text-positive',
  error: 'bg-negative-soft border-negative/30 text-negative',
}

const icons = { info: 'info', success: 'check', error: 'alert-triangle' }
</script>

<template>
  <div class="pointer-events-none fixed inset-x-0 bottom-20 z-60 flex flex-col items-center gap-2 px-4 sm:bottom-6">
    <TransitionGroup
      enter-active-class="transition duration-200"
      enter-from-class="translate-y-2 opacity-0"
      leave-active-class="transition duration-150"
      leave-to-class="opacity-0"
    >
      <div
        v-for="toast in toasts"
        :key="toast.id"
        :class="['pointer-events-auto flex max-w-md items-start gap-2.5 rounded-xl border px-3.5 py-2.5 text-sm shadow-lg', tones[toast.tone]]"
        role="status"
      >
        <AppIcon :name="icons[toast.tone]" :size="16" class="mt-0.5" />
        <p class="flex-1">{{ toast.message }}</p>

        <button type="button" class="opacity-60 hover:opacity-100" aria-label="Dismiss" @click="dismiss(toast.id)">
          <AppIcon name="x" :size="14" />
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>
