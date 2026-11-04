<script setup>
import { GraduationCap, Users } from "@lucide/vue";
import { onMounted, ref } from "vue";
import EmptyState from "../components/EmptyState.vue";
import PageHeader from "../components/PageHeader.vue";
import { getClasses } from "../services/api";

const loading = ref(true);
const classes = ref([]);

onMounted(async () => {
  classes.value = await getClasses();
  loading.value = false;
});
</script>

<template>
  <div class="page">
    <PageHeader
      eyebrow="教学组织"
      title="班级"
      description="查看班级名册、学生数量与已布置作业。"
    >
      <template #actions>
        <RouterLink to="/students" class="button button-secondary">
          学生名单
        </RouterLink>
      </template>
    </PageHeader>
    <div v-if="loading" class="class-grid">
      <div v-for="index in 3" :key="index" class="skeleton class-skeleton" />
    </div>
    <EmptyState
      v-else-if="!classes.length"
      title="还没有创建班级"
      description="班级创建和成员管理将继续接入 Java 后端，目前旧版仍可完整操作。"
    >
      <span class="empty-icon"><GraduationCap :size="21" /></span>
    </EmptyState>
    <div v-else class="class-grid">
      <article v-for="item in classes" :key="item.id" class="card class-card">
        <div class="class-icon"><GraduationCap :size="20" /></div>
        <div class="class-copy">
          <div><strong>{{ item.name }}</strong><span>{{ item.classCode || "未生成班级码" }}</span></div>
          <p>{{ [item.subject, item.term].filter(Boolean).join(" · ") || "未设置学科与学期" }}</p>
        </div>
        <div class="class-meta">
          <span><Users :size="14" />{{ item.studentCount || 0 }} 名学生</span>
          <span>{{ item.assignmentCount || 0 }} 个作业</span>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.class-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.class-skeleton {
  height: 180px;
}

.class-card {
  padding: 19px;
}

.class-icon {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 13px;
  background: var(--primary-soft);
  color: var(--primary);
}

.class-copy {
  margin-top: 15px;
}

.class-copy > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.class-copy strong {
  font-size: 14px;
}

.class-copy span {
  color: var(--text-muted);
  font-size: 10px;
}

.class-copy p {
  margin-top: 5px;
  color: var(--text-secondary);
  font-size: 11px;
}

.class-meta {
  display: flex;
  gap: 13px;
  margin-top: 16px;
  border-top: 1px solid var(--border);
  padding-top: 13px;
  color: var(--text-muted);
  font-size: 10px;
}

.class-meta span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

@media (max-width: 1000px) {
  .class-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .class-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
