<script setup lang="ts">
import { RouterView } from 'vue-router'
import AppToastHost from '@/components/AppToastHost.vue'
import AppConfirm from '@/components/AppConfirm.vue'
</script>

<template>
  <RouterView />
  <AppToastHost />
  <AppConfirm />
</template>
