<script setup lang="ts">
import {
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  Legend,
  LinearScale,
  LineController,
  LineElement,
  PointElement,
  Tooltip,
} from 'chart.js'
import type { MonthlyTrend } from '~/types/api'

Chart.register(
  BarController, BarElement, LineController, LineElement, PointElement,
  CategoryScale, LinearScale, Tooltip, Legend,
)

/**
 * Income against expenses per month, with net as a line.
 *
 * Colours come from the stylesheet's tokens so the chart follows the theme,
 * including dark mode, instead of carrying its own palette.
 */
const props = defineProps<{ trends: MonthlyTrend[] }>()

const canvas = ref<HTMLCanvasElement>()
const money = useMoney()
let chart: Chart | undefined

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
    type: 'bar',
    data: {
      labels: props.trends.map((entry) => entry.label.replace(' 20', " '")),
      datasets: [
        {
          label: 'Income',
          data: props.trends.map((entry) => entry.income_minor / divisor),
          backgroundColor: token('--color-positive'),
          borderRadius: 4,
          maxBarThickness: 22,
        },
        {
          label: 'Expenses',
          data: props.trends.map((entry) => entry.expense_minor / divisor),
          backgroundColor: token('--color-negative'),
          borderRadius: 4,
          maxBarThickness: 22,
        },
        {
          type: 'line',
          label: 'Net',
          data: props.trends.map((entry) => entry.net_minor / divisor),
          borderColor: token('--color-brand'),
          backgroundColor: token('--color-brand'),
          borderWidth: 2,
          pointRadius: 3,
          tension: 0.3,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: {
          position: 'bottom',
          labels: {
            color: token('--color-ink-soft'),
            boxWidth: 10,
            boxHeight: 10,
            usePointStyle: true,
            pointStyle: 'circle',
            font: { size: 11 },
          },
        },
        tooltip: {
          callbacks: {
            label: (context) =>
              `${context.dataset.label}: ${money.format(Math.round((context.parsed.y ?? 0) * divisor))}`,
          },
        },
      },
      scales: {
        x: {
          grid: { display: false },
          border: { display: false },
          ticks: { color: token('--color-ink-faint'), font: { size: 11 } },
        },
        y: {
          grid: { color: token('--color-border') },
          border: { display: false },
          ticks: {
            color: token('--color-ink-faint'),
            font: { size: 11 },
            callback: (value) => money.compact(Math.round(Number(value) * divisor)),
          },
        },
      },
    },
  })
}

onMounted(render)
watch(() => props.trends, render, { deep: true })
onUnmounted(() => chart?.destroy())
</script>

<template>
  <div class="h-64">
    <canvas ref="canvas" />
  </div>
</template>
