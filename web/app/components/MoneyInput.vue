<script setup lang="ts">
/**
 * Amount entry. The model is a plain string so what the user typed is never
 * reformatted under their cursor; the parent converts it with `useMoney`.
 */
const props = withDefaults(
  defineProps<{ modelValue: string; size?: 'md' | 'lg'; placeholder?: string; autofocus?: boolean }>(),
  { size: 'md', placeholder: '0.00' },
)

const emit = defineEmits<{ 'update:modelValue': [string] }>()

const auth = useAuthStore()
const input = ref<HTMLInputElement>()

function onInput(event: Event): void {
  const raw = (event.target as HTMLInputElement).value

  // Digits, one decimal point, nothing else — typing a letter simply does
  // nothing rather than rejecting the whole field on submit.
  const cleaned = raw
    .replace(/[^0-9.]/g, '')
    .replace(/(\..*)\./g, '$1')

  emit('update:modelValue', cleaned)

  if (cleaned !== raw && input.value) {
    input.value.value = cleaned
  }
}

onMounted(() => {
  if (props.autofocus) {
    input.value?.focus()
  }
})
</script>

<template>
  <div class="relative">
    <span
      :class="[
        'text-ink-faint pointer-events-none absolute inset-y-0 left-0 flex items-center',
        size === 'lg' ? 'pl-4 text-xl' : 'pl-3 text-sm',
      ]"
    >
      {{ auth.symbol }}
    </span>

    <input
      ref="input"
      :value="modelValue"
      type="text"
      inputmode="decimal"
      autocomplete="off"
      :placeholder="placeholder"
      :class="[
        'field tnum',
        size === 'lg' ? 'py-3.5 pl-10 text-2xl font-semibold tracking-tight' : 'pl-8',
      ]"
      @input="onInput"
    >
  </div>
</template>
