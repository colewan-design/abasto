<script setup lang="ts">
const auth = useAuthStore()

// Restore the session before the first route renders so the global middleware
// can decide where to send the visitor.
await auth.restore()
</script>

<template>
  <div>
    <NuxtLayout>
      <NuxtPage />
    </NuxtLayout>

    <ToastStack />
  </div>
</template>
