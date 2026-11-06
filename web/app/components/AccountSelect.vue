<script setup lang="ts">
const props = withDefaults(
  defineProps<{ modelValue: number | null; exclude?: number | null; label?: string }>(),
  { label: 'Account' },
)

const emit = defineEmits<{ 'update:modelValue': [number | null] }>()

const reference = useReferenceStore()

const options = computed(() =>
  reference.activeAccounts.filter((account) => account.id !== props.exclude),
)
</script>

<template>
  <select
    :value="modelValue ?? ''"
    class="field"
    :aria-label="label"
    @change="emit('update:modelValue', Number(($event.target as HTMLSelectElement).value) || null)"
  >
    <option value="" disabled>Choose an account</option>
    <option v-for="account in options" :key="account.id" :value="account.id">
      {{ account.name }} · {{ account.formatted.balance }}
    </option>
  </select>
</template>
