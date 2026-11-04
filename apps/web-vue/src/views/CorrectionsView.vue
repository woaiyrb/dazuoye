<script setup>
import {
  Check,
  CheckCircle2,
  Clock3,
  RotateCcw,
  UserCheck,
} from "@lucide/vue";
import { computed, onMounted, ref } from "vue";
import EmptyState from "../components/EmptyState.vue";
import PageHeader from "../components/PageHeader.vue";
import SectionCard from "../components/SectionCard.vue";
import { getUser, relativeTime } from "../services/api";

const loading = ref(true);
const error = ref("");
const corrections = ref([]);
const assignmentId = ref(1);
const confirming = ref({});

const submittedCount = computed(
  () => corrections.value.filter((record) => record.status === "SUBMITTED").length,
);
const confirmedCount = computed(
  () => corrections.value.filter((record) => record.status === "CONFIRMED").length,
);
async function load() {
  loading.value = true;
  error.value = "";
  try {
    const user = await getUser();
    assignmentId.value = user?.assignmentId || 1;
    const response = await fetch(
      `/java-api/student/assignments/${assignmentId.value}/corrections`,
      { credentials: "include" },
    );
    if (!response.ok) throw new Error("订正记录加载失败");
    corrections.value = await response.json();
  } catch (requestError) {
    error.value = requestError.message || "订正记录加载失败。";
  } finally {
    loading.value = false;
  }
}

async function confirm(record) {
  confirming.value[record.id] = true;
  error.value = "";
  try {
    const response = await fetch(
      `/java-api/student/corrections/${record.id}/confirm`,
      {
        method: "PATCH",
        credentials: "include",
      },
    );
    const body = await response.json().catch(() => null);
    if (!response.ok) throw new Error(body?.message || "确认订正失败");
    corrections.value = corrections.value.map((item) =>
      item.id === body.id ? body : item,
    );
  } catch (requestError) {
    error.value = requestError.message || "确认订正失败。";
  } finally {
    confirming.value[record.id] = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeader
      eyebrow="学习闭环"
      title="学生订正跟踪"
      description="学生提交订正后，由教师检查答案和说明；教师确认后才算订正完成。"
    >
      <template #actions>
        <button type="button" class="button button-secondary" :disabled="loading" @click="load">
          刷新记录
        </button>
      </template>
    </PageHeader>

    <p v-if="error" class="alert alert-danger" role="alert">{{ error }}</p>

    <div class="correction-stats">
      <div><Clock3 :size="18" /><span><strong>{{ submittedCount }}</strong><small>待教师确认</small></span></div>
      <div><RotateCcw :size="18" /><span><strong>{{ corrections.length }}</strong><small>全部订正</small></span></div>
      <div><CheckCircle2 :size="18" /><span><strong>{{ confirmedCount }}</strong><small>已完成</small></span></div>
    </div>

    <SectionCard
      title="订正记录"
      description="记录包含重新选择的答案、学生说明、标准答案和教师确认状态"
    >
      <div v-if="loading" class="skeleton correction-skeleton" />
      <EmptyState
        v-else-if="!corrections.length"
        title="暂无订正记录"
        description="学生查看已发布讲评并提交错题订正后，记录会显示在这里。"
      >
        <span class="empty-icon"><UserCheck :size="21" /></span>
      </EmptyState>
      <div v-else class="correction-list">
        <article v-for="record in corrections" :key="record.id" class="correction-item">
          <div class="correction-main">
            <span class="correction-icon" :class="{ correct: record.correct }">
              <Check v-if="record.correct" :size="17" />
              <RotateCcw v-else :size="17" />
            </span>
            <div>
              <div class="correction-title">
                <strong>{{ record.question }}</strong>
                <span
                  class="badge"
                  :class="record.status === 'CONFIRMED' ? 'badge-success' : 'badge-warning'"
                >
                  {{ record.status === "CONFIRMED" ? "教师已确认" : "等待教师确认" }}
                </span>
              </div>
              <p>
                学生答案：<strong>{{ record.selectedLabel }}</strong>
                · 标准答案：<strong>{{ record.correctLabel }}</strong>
              </p>
              <blockquote v-if="record.explanation">{{ record.explanation }}</blockquote>
              <small>{{ relativeTime(record.submittedAt) }}</small>
            </div>
          </div>
          <button
            v-if="record.status === 'SUBMITTED'"
            type="button"
            class="button button-primary compact-button"
            :disabled="confirming[record.id]"
            @click="confirm(record)"
          >
            <Check :size="15" />
            {{ confirming[record.id] ? "确认中…" : "确认订正" }}
          </button>
          <span v-else class="confirmed-label"><CheckCircle2 :size="16" />已完成</span>
        </article>
      </div>
    </SectionCard>
  </div>
</template>

<style scoped>
.correction-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.correction-stats > div {
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: white;
  padding: 17px;
  color: var(--primary);
}

.correction-stats span {
  display: flex;
  flex-direction: column;
}

.correction-stats strong {
  color: var(--text);
  font-size: 24px;
  line-height: 1.1;
}

.correction-stats small {
  color: var(--text-muted);
  font-size: 10px;
}

.correction-skeleton {
  height: 220px;
}

.correction-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.correction-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--surface-soft);
  padding: 14px;
}

.correction-main {
  display: flex;
  min-width: 0;
  gap: 11px;
}

.correction-icon {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  place-items: center;
  border-radius: 10px;
  background: var(--warning-soft);
  color: var(--warning);
}

.correction-icon.correct {
  background: var(--success-soft);
  color: var(--success);
}

.correction-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.correction-title strong {
  font-size: 12px;
}

.correction-main p,
.correction-main small {
  color: var(--text-muted);
  font-size: 10px;
}

.correction-main p {
  margin-top: 4px;
}

.correction-main p strong {
  color: var(--text-secondary);
}

.correction-main blockquote {
  margin: 8px 0 4px;
  border-left: 2px solid var(--primary);
  border-radius: 0 8px 8px 0;
  background: white;
  padding: 7px 9px;
  color: var(--text-secondary);
  font-size: 10px;
}

.compact-button {
  min-height: 38px;
  padding: 7px 11px;
  font-size: 11px;
  white-space: nowrap;
}

.confirmed-label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--success);
  font-size: 11px;
  font-weight: 650;
}

@media (max-width: 720px) {
  .correction-stats {
    grid-template-columns: minmax(0, 1fr);
  }

  .correction-item {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
