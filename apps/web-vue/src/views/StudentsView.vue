<script setup>
import { Search, Users } from "@lucide/vue";
import { computed, onMounted, ref } from "vue";
import EmptyState from "../components/EmptyState.vue";
import PageHeader from "../components/PageHeader.vue";
import {
  getAssignment,
  getAssignments,
  getAttempts,
  getUser,
  relativeTime,
} from "../services/api";

const loading = ref(true);
const students = ref([]);
const query = ref("");

const filteredStudents = computed(() => {
  const keyword = query.value.trim().toLowerCase();
  if (!keyword) return students.value;
  return students.value.filter(
    (student) =>
      student.name.toLowerCase().includes(keyword) ||
      student.userId.toLowerCase().includes(keyword),
  );
});

async function loadStudents() {
  loading.value = true;
  try {
    const [session, assignments] = await Promise.all([
      getUser(),
      getAssignments().catch(() => []),
    ]);
    const assignmentIds = assignments.length
      ? assignments.map((assignment) => assignment.id)
      : [session?.assignmentId || 1];
    const rows = [];
    for (const assignmentId of assignmentIds) {
      const [assignment, attempts] = await Promise.all([
        getAssignment(assignmentId).catch(() => null),
        getAttempts(assignmentId),
      ]);
      for (const attempt of attempts) rows.push({ ...attempt, assignment });
    }

    const byStudent = new Map();
    for (const row of rows) {
      const userId = row.userId || "unknown";
      if (!byStudent.has(userId)) {
        const local = userId.split("@")[0];
        byStudent.set(userId, {
          userId,
          name: ["user", "student"].includes(local.toLowerCase())
            ? "学生用户"
            : local,
          assignmentIds: new Set(),
          submittedCount: 0,
          gradeSum: 0,
          gradeCount: 0,
          lastAt: null,
          lastAssignment: "",
        });
      }
      const student = byStudent.get(userId);
      if (row.assignment?.id) student.assignmentIds.add(row.assignment.id);
      if (row.submitted) student.submittedCount += 1;
      if (row.grade !== null && row.grade !== undefined) {
        student.gradeSum += Number(row.grade);
        student.gradeCount += 1;
      }
      const at = row.createdAt ? new Date(row.createdAt) : null;
      if (at && (!student.lastAt || at > student.lastAt)) {
        student.lastAt = at;
        student.lastAssignment = row.assignment?.name || "";
      }
    }

    students.value = [...byStudent.values()]
      .map((student) => ({
        ...student,
        assignmentCount: student.assignmentIds.size,
        averageGrade: student.gradeCount
          ? Math.round(student.gradeSum / student.gradeCount)
          : null,
        lastActivityAt: student.lastAt?.toISOString() || null,
      }))
      .sort(
        (left, right) =>
          new Date(right.lastActivityAt || 0) -
          new Date(left.lastActivityAt || 0),
      );
  } finally {
    loading.value = false;
  }
}

onMounted(loadStudents);
</script>

<template>
  <div class="page">
    <PageHeader
      eyebrow="学习参与情况"
      title="学生"
      description="根据真实作答记录自动聚合学生名单、提交次数和最近活动。"
    >
      <template #actions>
        <RouterLink to="/classes" class="button button-secondary">
          班级管理
        </RouterLink>
        <div class="students-search">
          <Search :size="16" />
          <input
            v-model="query"
            type="search"
            placeholder="搜索学生"
            aria-label="搜索学生"
          />
        </div>
      </template>
    </PageHeader>

    <div v-if="loading" class="student-skeletons">
      <div v-for="index in 5" :key="index" class="skeleton" />
    </div>
    <EmptyState
      v-else-if="!filteredStudents.length"
      title="还没有学生提交记录"
      description="学生完成任意作业后，这里会自动生成名单。"
    >
      <span class="empty-icon"><Users :size="21" /></span>
    </EmptyState>
    <div v-else class="data-table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>学生</th>
            <th>涉及作业</th>
            <th>提交次数</th>
            <th>平均得分率</th>
            <th>最近提交</th>
            <th>最近作业</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="student in filteredStudents" :key="student.userId">
            <td>
              <div class="student-cell">
                <span class="student-avatar">{{ student.name.slice(0, 1) }}</span>
                <div><strong>{{ student.name }}</strong><small>{{ student.userId }}</small></div>
              </div>
            </td>
            <td>{{ student.assignmentCount }} 个</td>
            <td>{{ student.submittedCount }} 次</td>
            <td>
              <span
                class="score"
                :class="{
                  good: student.averageGrade >= 80,
                  warn: student.averageGrade >= 60 && student.averageGrade < 80,
                  low: student.averageGrade < 60,
                }"
              >
                {{ student.averageGrade === null ? "待批改" : `${student.averageGrade}%` }}
              </span>
            </td>
            <td>{{ relativeTime(student.lastActivityAt) }}</td>
            <td>{{ student.lastAssignment || "暂无" }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.students-search {
  position: relative;
}

.students-search svg {
  position: absolute;
  top: 50%;
  left: 12px;
  color: var(--text-muted);
  transform: translateY(-50%);
}

.students-search input {
  height: 44px;
  width: 230px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: white;
  padding: 0 13px 0 37px;
  outline: none;
}

.students-search input:focus {
  border-color: color-mix(in srgb, var(--role) 42%, var(--line));
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--role) 12%, transparent);
}

.student-skeletons {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.student-skeletons .skeleton {
  height: 62px;
}

.student-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.student-avatar {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 10px;
  background: var(--primary-soft);
  color: var(--primary);
  font-size: 11px;
  font-weight: 700;
}

.student-cell div {
  display: flex;
  flex-direction: column;
}

.student-cell small {
  max-width: 220px;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.score {
  font-weight: 700;
}

.score.good {
  color: var(--success);
}

.score.warn {
  color: var(--warning);
}

.score.low {
  color: var(--danger);
}

@media (max-width: 560px) {
  .students-search input {
    width: 100%;
  }
}
</style>
