<script setup>
import { Sparkles } from "@lucide/vue";
import { onMounted, ref } from "vue";
import EmptyState from "../components/EmptyState.vue";
import PageHeader from "../components/PageHeader.vue";
import { getKnowledgePoints } from "../services/api";

const loading = ref(true);
const points = ref([]);

onMounted(async () => {
  points.value = await getKnowledgePoints();
  loading.value = false;
});
</script>

<template>
  <div class="page">
    <PageHeader
      eyebrow="课程内容"
      title="知识点"
      description="管理题目与知识点之间的关联，为学情分析和针对性讲评提供依据。"
    />
    <div v-if="loading" class="knowledge-grid">
      <div v-for="index in 6" :key="index" class="skeleton point-skeleton" />
    </div>
    <EmptyState
      v-else-if="!points.length"
      title="还没有知识点"
      description="后续创建知识卡片并关联题目后，会在这里形成结构化知识网络。"
    >
      <span class="empty-icon"><Sparkles :size="21" /></span>
    </EmptyState>
    <div v-else class="knowledge-grid">
      <article v-for="point in points" :key="point.id" class="card point-card">
        <span class="point-icon"><Sparkles :size="17" /></span>
        <strong>{{ point.name }}</strong>
        <p>{{ point.description || "暂无知识点说明" }}</p>
        <small>{{ point.questionCount || 0 }} 道关联题目</small>
      </article>
    </div>
  </div>
</template>

<style scoped>
.knowledge-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.point-skeleton {
  height: 160px;
}

.point-card {
  padding: 18px;
}

.point-icon {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  border-radius: 11px;
  background: var(--primary-soft);
  color: var(--primary);
}

.point-card strong {
  display: block;
  margin-top: 13px;
  font-size: 13px;
}

.point-card p {
  margin-top: 5px;
  color: var(--text-secondary);
  font-size: 11px;
}

.point-card small {
  display: block;
  margin-top: 14px;
  color: var(--text-muted);
  font-size: 10px;
}

@media (max-width: 1000px) {
  .knowledge-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 620px) {
  .knowledge-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
