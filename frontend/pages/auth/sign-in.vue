<script setup lang="ts">
useHead({ title: 'Вход — Webizon' })

const { keycloakUrl, keycloakRealm, keycloakClientId } = useRuntimeConfig().public

function redirectToKeycloak() {
  const redirectUri = encodeURIComponent(window.location.origin + '/auth/callback')
  const url =
    `${keycloakUrl}/realms/${keycloakRealm}/protocol/openid-connect/auth` +
    `?client_id=${encodeURIComponent(keycloakClientId)}` +
    `&redirect_uri=${redirectUri}` +
    `&response_type=code&scope=openid%20profile%20email`
  window.location.href = url
}
</script>

<template>
  <section class="mx-auto flex min-h-[60vh] w-full max-w-md flex-col justify-center px-4 py-16">
    <div class="card p-8">
      <h1 class="text-2xl font-semibold text-slate-900">Вход в Webizon</h1>
      <p class="mt-2 text-sm text-slate-600">
        Авторизация через единый аккаунт Webizon (Keycloak SSO).
      </p>

      <button class="btn-primary mt-6 w-full" @click="redirectToKeycloak">
        Продолжить через Webizon ID
      </button>

      <p class="mt-4 text-center text-xs text-slate-500">
        Ещё нет аккаунта?
        <NuxtLink to="/auth/sign-up" class="text-brand-600 hover:underline">Создать</NuxtLink>
      </p>
    </div>
  </section>
</template>
