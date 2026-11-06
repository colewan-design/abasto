/**
 * The site root is the public download page. Everything under `/app` is the
 * signed-in client, which routes as:
 *
 *   signed out            -> /welcome
 *   signed in, no setup   -> /onboarding
 *   signed in, set up     -> the page they asked for
 */
const PUBLIC_ROUTES = ['/', '/welcome']

export default defineNuxtRouteMiddleware(async (to) => {
  if (to.path === '/') {
    return
  }

  const auth = useAuthStore()

  await auth.restore()

  const isPublic = PUBLIC_ROUTES.includes(to.path)

  if (!auth.isAuthenticated) {
    return isPublic ? undefined : navigateTo('/welcome')
  }

  if (to.path === '/welcome') {
    return navigateTo(auth.needsOnboarding ? '/onboarding' : '/app')
  }

  if (auth.needsOnboarding && to.path !== '/onboarding') {
    return navigateTo('/onboarding')
  }

  if (!auth.needsOnboarding && to.path === '/onboarding') {
    return navigateTo('/app')
  }
})
