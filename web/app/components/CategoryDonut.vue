<script setup lang="ts">
import { ArcElement, Chart, DoughnutController, Legend, Tooltip } from 'chart.js'
import type { CategoryBreakdown } from '~/types/api'

Chart.register(DoughnutController, ArcElement, Tooltip, Legend)

/**
 * Share of spending by category.
 *
 * Each arc uses the category's own colour, so the same category reads the same
 * here as it does in a list. Beyond eight slices the tail is pooled into
 * "Other" rather than producing a ring of unreadable slivers.
 */
const props = withDefaults(defineProps<{ breakdown: CategoryBreakdown[]; limit?: number }>(), { limit: 8 })

const canvas = ref<HTMLCanvasElement>()
const money = useMoney()
let chart: Chart | undefined

const slices = computed(() => {
  const sorted = [...props.breakdown].sort((a, b) => b.amount_minor - a.amount_minor)
  const head = sorted.slice(0, props.limit)
  const tail = sorted.slice(props.limit)

  if (tail.length === 0) {
    return head
  }

  return [
    ...head,
    {
      category_id: null,
      name: `Other (${tail.length})`,
      color: null,
      icon: null,
      group: null,
      amount_minor: tail.reduce((total, entry) => total + entry.amount_minor, 0),
      formatted: '',
      transactions: tail.reduce((total, entry) => total + entry.transactions, 0),
      share: tail.reduce((total, entry) => total + entry.share, 0),
    } satisfies CategoryBreakdown,
  ]
})

const fallbackPalette = ['#64748b', '#94a3b8', '#78716c', '#a1a1aa']

function token(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

function render(): void {
  if (!canvas.value) {
    return
  }

  chart?.destroy()

  const divisor = 10 ** money.exponent.value

  chart = new Chart(canvas.value, {
    type: 'doughnut',
    data: {
      labels: slices.value.map((entry) => entry.name),
      datasets: [
        {
          data: slices.value.map((entry) => entry.amount_minor / divisor),
          backgroundColor: slices.value.map(
            (entry, index) => entry.color ?? fallbackPalette[index % fallbackPalette.length]!,
          ),
          borderColor: token('--color-surface'),
          borderWidth: 2,
          hoverOffset: 6,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      cutout: '62%',
      plugins: {
        legend: {
          position: 'right',
          labels: {
            color: token('--color-ink-soft'),
            boxWidth: 9,
            boxHeight: 9,
            usePointStyle: true,
            pointStyle: 'circle',
            padding: 10,
            font: { size: 11 },
          },
        },
        tooltip: {
          callbacks: {
            label: (context) => {
              const slice = slices.value[context.dataIndex]!

              return ` ${money.format(slice.amount_minor)} · ${money.percent(slice.share, 1)}`
            },
          },
        },
      },
    },
  })
}

onMounted(render)
watch(slices, render, { deep: true })
onUnmounted(() => chart?.destroy())
</script>

<template>
  <div class="h-64">
    <canvas ref="canvas" />
  </div>
</template>
