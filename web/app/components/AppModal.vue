<script setup lang="ts">
const props = withDefaults(
  defineProps<{ open: boolean; title: string; subtitle?: string; width?: 'sm' | 'md' | 'lg' }>(),
  { width: 'md' },
)

const emit = defineEmits<{ close: [] }>()

const widths = { sm: 'max-w-sm', md: 'max-w-lg', lg: 'max-w-3xl' }

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && props.open) {
    emit('close')
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => window.removeEventListener('keydown', onKeydown))

// Keep the page behind the dialog from scrolling while it is open.
watch(
  () => props.open,
  (open) => {
    document.body.style.overflow = open ? 'hidden' : ''
  },
)

onUnmounted(() => {
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition duration-150"
      enter-from-class="opacity-0"
      leave-active-class="transition duration-100"
      leave-to-class="opacity-0"
    >
      <div v-if="open" class="fixed inset-0 z-50 flex items-end justify-center sm:items-center">
        <div class="bg-ink/40 absolute inset-0 backdrop-blur-[2px]" @click="emit('close')" />

        <div
          :class="[
            'card relative z-10 max-h-[92vh] w-full overflow-y-auto rounded-b-none shadow-xl sm:rounded-(--radius-card)',
            widths[width],
          ]"
          role="dialog"
          aria-modal="true"
        >
          <header class="border-border bg-surface sticky top-0 flex items-start justify-between gap-4 border-b px-5 py-4">
            <div>
              <h2 class="text-base font-semibold tracking-tight">{{ title }}</h2>
              <p v-if="subtitle" class="text-ink-faint mt-0.5 text-sm">{{ subtitle }}</p>
            </div>

            <button type="button" class="btn-ghost -mr-1.5 -mt-1 p-1.5" aria-label="Close" @click="emit('close')">
              <AppIcon name="x" :size="18" />
            </button>
          </header>

          <div class="px-5 py-4">
            <slot />
          </div>

          <footer v-if="$slots.footer" class="border-border bg-surface sticky bottom-0 border-t px-5 py-3.5">
            <slot name="footer" />
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
