<script setup lang="ts">
useHead({ title: 'Тарифы — Webizon' })

interface Rate {
  title: string
  price: string
  unit: string
  note: string
}

const rates: Rate[] = [
  { title: 'Живой зритель',      price: '15 ₸', unit: 'за 1 просмотр',    note: 'Платите только за реально подключившихся.' },
  { title: 'Авто-повтор',        price: '10 ₸', unit: 'за 1 просмотр',    note: 'Расписанные повторы дешевле live.' },
  { title: 'Хранение записей',   price: '200 ₸', unit: 'ГБ × мес',        note: 'Записи и материалы в MinIO.' },
]
</script>

<template>
  <section class="mx-auto w-full max-w-5xl px-4 py-16">
    <h1 class="text-3xl font-bold text-slate-900 md:text-4xl">Честная оплата за факт</h1>
    <p class="mt-4 max-w-2xl text-lg text-slate-600">
      Без скрытых пакетов, без «безлимитных» обещаний с мелким шрифтом.
      Первые 14 дней — бесплатно. Дальше вы платите только за реально использованные ресурсы.
    </p>

    <div class="mt-10 grid gap-4 md:grid-cols-3">
      <article
        v-for="rate in rates"
        :key="rate.title"
        class="card p-6"
      >
        <h2 class="text-sm font-medium uppercase tracking-wide text-slate-500">
          {{ rate.title }}
        </h2>
        <p class="mt-3 text-3xl font-bold text-slate-900">{{ rate.price }}</p>
        <p class="text-sm text-slate-500">{{ rate.unit }}</p>
        <p class="mt-4 text-sm text-slate-600">{{ rate.note }}</p>
      </article>
    </div>

    <p class="mt-8 text-sm text-slate-500">
      Все цены указаны без НДС. НДС 12% добавляется автоматически для резидентов РК.
      Комиссия платёжного шлюза — 3% от суммы платежа.
    </p>
  </section>
</template>
