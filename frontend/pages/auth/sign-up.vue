<script setup lang="ts">
useHead({ title: 'Регистрация — Webizon' })

const { keycloakUrl, keycloakRealm, keycloakClientId } = useRuntimeConfig().public

function redirectToKeycloakRegister() {
  const redirectUri = encodeURIComponent(window.location.origin + '/auth/callback')
  const url =
    `${keycloakUrl}/realms/${keycloakRealm}/protocol/openid-connect/registrations` +
    `?client_id=${encodeURIComponent(keycloakClientId)}` +
    `&redirect_uri=${redirectUri}` +
    `&response_type=code&scope=openid%20profile%20email`
  window.location.href = url
}
</script>

<template>
  <section class="mx-auto flex min-h-[60vh] w-full max-w-md flex-col justify-center px-4 py-16">
    <div class="card p-8">
      <h1 class="text-2xl font-semibold text-slate-900">14 дней бесплатно</h1>
      <p class="mt-2 text-sm text-slate-600">
        Создайте рабочее пространство вашей школы или личного бренда за минуту.
      </p>

      <button class="btn-primary mt-6 w-full" @click="redirectToKeycloakRegister">
        Создать аккаунт
      </button>

      <p class="mt-4 text-center text-xs text-slate-500">
        Уже есть аккаунт?
        <NuxtLink to="/auth/sign-in" class="text-brand-600 hover:underline">Войти</NuxtLink>
      </p>
    </div>
  </section>
</template>
