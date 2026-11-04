<script setup>
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: "" },
});
</script>

<template>
  <section class="card">
    <header class="card-header">
      <div>
        <h2 class="card-title">{{ title }}</h2>
        <p v-if="description" class="card-description">{{ description }}</p>
      </div>
      <slot name="actions" />
    </header>
    <div class="card-body">
      <slot />
    </div>
  </section>
</template>
