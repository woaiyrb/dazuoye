<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { teacherApi } from '@/api/teacher'
import { errorMessage } from '@/api/errors'
import type { GradingRecord, HomeworkDetail, StudentSummary } from '@/types/models'
import { useUiStore } from '@/stores/ui'
import { fetchAllPages } from '@/utils/pagination'
import PageHeader from '@/components/PageHeader.vue'
import AppIcon from '@/components/AppIcon.vue'
import AppEmpty from '@/components/AppEmpty.vue'

type CellState = 'CORRECT' | 'WRONG' | 'UNANSWERED'

const route = useRoute()
const router = useRouter()
const ui = useUiStore()
const homeworkId = computed(() => Number(route.params.id))
const loading = ref(true)
const submitting = ref(false)
const error = ref('')
const homework = ref<HomeworkDetail | null>(null)
const students = ref<StudentSummary[]>([])
const records = ref<GradingRecord[]>([])
const matrix = reactive<Record<string, CellState>>({})

const questions = computed(() => [...(homework.value?.questions ?? [])].sort((a, b) => a.seqNo - b.seqNo))
const existingRecords = computed(() => {
  const map = new Map<string, GradingRecord>()
  for (const record of records.value) {
    map.set(`${record.studentId}-${record.questionId}`, record)
  }
  return map
})
const summary = computed(() => {
  const values = Object.values(matrix)
  return {
    total: values.length,
    correct: values.filter((value) => value === 'CORRECT').length,
    wrong: values.filter((value) => value === 'WRONG').length,
    unanswered: values.filter((value) => value === 'UNANSWERED').length,
  }
})

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const detail = await teacherApi.getHomework(homeworkId.value)
    homework.value = detail
    const [studentResult, recordResult] = await Promise.all([
      fetchAllPages((page, pageSize) =>
        teacherApi.getClassStudents(detail.classId, {
          includeDisabled: false,
          page,
          pageSize,
        }),
      ),
      fetchAllPages((page, pageSize) =>
        teacherApi.listGradingRecords(detail.id, { page, pageSize }),
      ),
    ])
    students.value = studentResult.items.filter((student) => student.status !== 'DISABLED')
    records.value = recordResult.items
    const previousByKey = existingRecords.value
    for (const student of students.value) {
      for (const question of questions.value) {
        const key = `${student.id}-${question.id}`
        const record = previousByKey.get(key)
        matrix[key] = record
          ? record.isCorrect
            ? 'CORRECT'
            : record.studentAnswer == null
              ? 'UNANSWERED'
              : 'WRONG'
          : 'UNANSWERED'
      }
    }
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

async function submit(): Promise<void> {
  if (!homework.value || !students.value.length || !questions.value.length) return
  const expectedCells = students.value.length * questions.value.length
  if (Object.keys(matrix).length !== expectedCells) {
    ui.toast('error', '批改矩阵不完整', `应有 ${expectedCells} 个单元格，请刷新后重试。`)
    return
  }
  submitting.value = true
  try {
    const rows = students.value.flatMap((student) =>
      questions.value.map((question) => {
        const key = `${student.id}-${question.id}`
        const state = matrix[key] ?? 'UNANSWERED'
        const previous = existingRecords.value.get(key)
        return {
          studentNo: student.studentNo,
          seqNo: question.seqNo,
          studentAnswer:
            state === 'UNANSWERED'
              ? null
              : previous?.studentAnswer ??
                (state === 'CORRECT' ? '（手工判定正确）' : '（手工判定错误）'),
          isCorrect: state === 'CORRECT',
          teacherNote: previous?.teacherNote ?? null,
          errorTag: state === 'WRONG' ? '待归类' : null,
        }
      }),
    )
    const result = await teacherApi.importManualGrading(homework.value.id, rows)
    ui.toast(
      'success',
      '手工批改已保存',
      `已生成批次 V${result.batch.versionNo}，共 ${result.batch.validRows} 条记录。`,
    )
    await router.push(`/teacher/homeworks/${homework.value.id}/grading`)
  } catch (cause) {
    ui.toast('error', '保存失败', errorMessage(cause))
  } finally {
    submitting.value = false
  }
}

function goBack(): void {
  void router.push(`/teacher/homeworks/${homeworkId.value}/grading`)
}

onMounted(load)
</script>

<template>
  <div class="manual-grading-page">
    <PageHeader
      eyebrow="TEACHER · MANUAL GRADING"
      title="手工录入批改"
      subtitle="为学生 × 题目逐格标记正确、错误或未作答；提交生成完整批次，历史批次保留。"
    >
      <template #actions>
        <button class="btn btn--ghost btn--sm" type="button" @click="goBack">
          <AppIcon name="arrow-left" :size="14" />
          返回批改
        </button>
      </template>
    </PageHeader>

    <div v-if="loading" class="card loading-card">
      <span class="skeleton" style="width: 220px; height: 10px" />
    </div>
    <div v-else-if="error" class="card">
      <AppEmpty icon="alert-circle" title="无法加载手工录入" :description="error">
        <button class="btn btn--ghost btn--sm" type="button" @click="load">重试</button>
      </AppEmpty>
    </div>
    <template v-else-if="homework">
      <section class="card manual-head">
        <div>
          <span class="eyebrow">MANUAL MATRIX</span>
          <h2>{{ homework.title }}</h2>
          <p>{{ homework.className }} · {{ students.length }} 名学生 × {{ questions.length }} 道题。提交后生成新的完整批次。</p>
        </div>
        <div class="manual-summary">
          <div><span>记录</span><b>{{ summary.total }}</b></div>
          <div><span>正确</span><b>{{ summary.correct }}</b></div>
          <div><span>错误</span><b class="warm">{{ summary.wrong }}</b></div>
          <div><span>未作答</span><b class="danger">{{ summary.unanswered }}</b></div>
        </div>
      </section>

      <section v-if="students.length && questions.length" class="card matrix-card">
        <div class="matrix-scroll">
          <table class="manual-matrix">
            <thead>
              <tr>
                <th class="student-col">学生</th>
                <th v-for="question in questions" :key="question.id">
                  <span>第 {{ question.seqNo }} 题</span>
                  <small>{{ question.score }} 分</small>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="student in students" :key="student.id">
                <th class="student-col">
                  <b>{{ student.name }}</b>
                  <small>{{ student.studentNo }}</small>
                </th>
                <td v-for="question in questions" :key="question.id">
                  <select v-model="matrix[`${student.id}-${question.id}`]" class="matrix-select">
                    <option value="CORRECT">对</option>
                    <option value="WRONG">错</option>
                    <option value="UNANSWERED">未答</option>
                  </select>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="matrix-foot">
          <span><AppIcon name="info" :size="14" />未选择的格子按「未作答」提交；手工标记不会覆盖历史批次。</span>
          <button class="btn btn--primary btn--sm" type="button" :disabled="submitting" @click="submit">
            <AppIcon name="save" :size="14" />
            {{ submitting ? '保存中…' : '保存整批批改' }}
          </button>
        </div>
      </section>
      <AppEmpty
        v-else
        icon="clipboard"
        title="无法开始手工录入"
        description="需要已发布作业、题目和本班在读学生。"
      />
    </template>
  </div>
</template>

<style scoped>
.manual-grading-page {
  max-width: 1320px;
}

.manual-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 18px 20px;
  margin-bottom: 14px;
}

.eyebrow {
  color: var(--mint-deep);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.12em;
}

.manual-head h2 {
  margin-top: 5px;
  font-family: var(--font-serif);
  font-size: 20px;
}

.manual-head p {
  margin-top: 4px;
  color: var(--ink-3);
  font-size: 9px;
}

.manual-summary {
  display: flex;
  gap: 16px;
  flex: none;
}

.manual-summary div {
  text-align: right;
}

.manual-summary span {
  display: block;
  color: var(--ink-3);
  font-size: 8px;
}

.manual-summary b {
  display: block;
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 18px;
}

.manual-summary .warm {
  color: var(--apricot-deep);
}

.manual-summary .danger {
  color: #c97373;
}

.matrix-card {
  padding: 14px;
}

.matrix-scroll {
  overflow: auto;
  max-height: 64vh;
  border: 1px solid var(--line);
  border-radius: 12px;
}

.manual-matrix {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 9px;
}

.manual-matrix th,
.manual-matrix td {
  padding: 8px;
  border-right: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  white-space: nowrap;
}

.manual-matrix thead th {
  position: sticky;
  top: 0;
  z-index: 1;
  color: var(--ink-3);
  background: var(--surface-soft);
  font-weight: 500;
}

.manual-matrix thead th span,
.manual-matrix thead th small {
  display: block;
}

.manual-matrix thead th small {
  margin-top: 2px;
  font-size: 7px;
}

.manual-matrix .student-col {
  position: sticky;
  left: 0;
  z-index: 2;
  min-width: 130px;
  text-align: left;
  background: var(--surface);
}

.manual-matrix thead .student-col {
  z-index: 3;
  background: var(--surface-soft);
}

.manual-matrix tbody .student-col b,
.manual-matrix tbody .student-col small {
  display: block;
}

.manual-matrix tbody .student-col small {
  margin-top: 2px;
  color: var(--ink-3);
  font-family: var(--font-mono);
  font-size: 7px;
}

.matrix-select {
  width: 58px;
  padding: 4px 3px;
  border: 1px solid var(--line);
  border-radius: 6px;
  color: var(--ink-2);
  background: var(--surface);
  font-size: 8px;
}

.matrix-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 12px;
  color: var(--ink-3);
  font-size: 8px;
}

.matrix-foot > span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

@media (max-width: 760px) {
  .manual-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .manual-summary {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
