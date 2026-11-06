<script setup lang="ts">
/**
 * One place that decides how an amount reads: tabular digits so columns line
 * up, and colour used only to carry the sign.
 */
const props = withDefaults(
  defineProps<{
    minor: number | null | undefined
    /** `auto` colours by sign; `inflow`/`outflow` force it; `none` stays neutral. */
    tone?: 'auto' | 'inflow' | 'outflow' | 'none'
    sign?: boolean
    size?: 'sm' | 'md' | 'lg' | 'xl'
    compact?: boolean
  }>(),
  { tone: 'none', sign: false, size: 'md', compact: false },
)

const money = useMoney()

const text = computed(() =>
  props.compact ? money.compact(props.minor) : money.format(props.minor, { sign: props.sign }),
)

const toneClass = computed(() => {
  if (props.tone === 'inflow') return 'text-positive'
  if (props.tone === 'outflow') return 'text-ink'
  if (props.tone === 'none') return ''

  return (props.minor ?? 0) < 0 ? 'text-negative' : (props.minor ?? 0) > 0 ? 'text-positive' : 'text-ink-soft'
})

const sizeClass = computed(() => ({
  sm: 'text-xs',
  md: 'text-sm',
  lg: 'text-lg font-semibold',
  xl: 'text-2xl font-semibold tracking-tight',
}[props.size]))
</script>

<template>
  <span :class="['tnum', sizeClass, toneClass]">{{ text }}</span>
</template>
