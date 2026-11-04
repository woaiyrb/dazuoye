<script setup>
import { ArrowLeft, Compass } from "@lucide/vue";
</script>

<template>
  <main class="not-found">
    <span class="not-found-icon"><Compass :size="28" /></span>
    <p class="page-eyebrow">页面未找到</p>
    <h1>这里还没有内容</h1>
    <p>地址可能已变更，或者该页面仍在迁移到新前端。</p>
    <RouterLink to="/dashboard" class="button button-primary">
      <ArrowLeft :size="16" />
      返回工作台
    </RouterLink>
  </main>
</template>

<style scoped>
.not-found {
  display: flex;
  min-height: 100dvh;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px;
  text-align: center;
}

.not-found-icon {
  display: grid;
  width: 64px;
  height: 64px;
  place-items: center;
  margin-bottom: 18px;
  border-radius: 20px;
  background: var(--primary-soft);
  color: var(--primary);
}

.not-found h1 {
  margin-top: 6px;
  font-size: 28px;
}

.not-found > p:not(.page-eyebrow) {
  margin: 8px 0 20px;
  color: var(--text-secondary);
  font-size: 13px;
}
</style>
