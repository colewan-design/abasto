<script setup lang="ts">
/**
 * A budget or goal meter. The colour carries the status, and the bar is capped
 * at 100% with an overflow marker so "over budget" is visible without the bar
 * running off its track.
 */
const props = withDefaults(
  defineProps<{
    value: number | null | undefined
    status?: 'ok' | 'approaching' | 'exceeded' | 'unset' | 'goal'
    height?: 'sm' | 'md'
  }>(),
  { status: 'ok', height: 'md' },
)

const ratio = computed(() => Math.max(0, Math.min(1, props.value ?? 0)))
const overflow = computed(() => (props.value ?? 0) > 1)

const barClass = computed(() => ({
  ok: 'bg-brand',
  goal: 'bg-positive',
  approaching: 'bg-warning',
  exceeded: 'bg-negative',
  unset: 'bg-border-strong',
}[props.status]))
</script>

<template>
  <div
    class="bg-raised relative w-full overflow-hidden rounded-full"
    :class="height === 'sm' ? 'h-1.5' : 'h-2.5'"
    role="progressbar"
    :aria-valuenow="Math.round(ratio * 100)"
    aria-valuemin="0"
    aria-valuemax="100"
  >
    <div
      :class="['h-full rounded-full transition-[width] duration-500', barClass]"
      :style="{ width: `${ratio * 100}%` }"
    />
    <div
      v-if="overflow"
      class="bg-surface absolute inset-y-0 right-0 w-1"
      aria-hidden="true"
    />
  </div>
</template>
