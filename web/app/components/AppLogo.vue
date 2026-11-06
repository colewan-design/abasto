<script setup lang="ts">
/**
 * The app's mark, drawn the way the Android client draws it.
 *
 * The artwork in `public/logo.png` is the launcher icon's own plate — the dark
 * ground and the warm light on it are as much the logo as the coral shape is,
 * so it is used whole rather than reduced to a glyph on a coloured square.
 *
 * The corner is applied here rather than baked into the file, matching
 * `Brandmark` on Android, which clips the same artwork with
 * `RoundedCornerShape(size / 3.2)` — 31.25% of the side. Keeping the rounding
 * in one expression on both sides means the mark in a browser tab, on this
 * site and on a home screen stay the one thing.
 *
 * Decorative by default: every place it appears, the name is written beside
 * it, and a screen reader announcing "Budget Tracker" twice helps nobody.
 * Pass a `label` where the mark stands alone.
 */
withDefaults(
  defineProps<{
    size?: number
    label?: string
  }>(),
  { size: 32, label: '' },
)
</script>

<template>
  <img
    src="/logo.png"
    :alt="label"
    :width="size"
    :height="size"
    :style="{ width: `${size}px`, height: `${size}px` }"
    class="shrink-0 rounded-[31.25%]"
  >
</template>
