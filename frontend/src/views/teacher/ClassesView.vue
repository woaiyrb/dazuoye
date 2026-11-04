<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { teacherApi } from '@/api/teacher'
import { errorMessage } from '@/api/errors'
import type { ClassSummary, StudentSummary } from '@/types/models'
import { useUiStore } from '@/stores/ui'
import { fetchAllPages } from '@/utils/pagination'
import PageHeader from '@/components/PageHeader.vue'
import AppIcon from '@/components/AppIcon.vue'
import AppEmpty from '@/components/AppEmpty.vue'
import AppModal from '@/components/AppModal.vue'
import AppPagination from '@/components/AppPagination.vue'

const PAGE_SIZE = 10
const ui = useUiStore()

const loading = ref(true)
const error = ref('')
const classes = ref<ClassSummary[]>([])
const selectedClassId = ref<number | null>(null)
const students = ref<StudentSummary[]>([])
const studentLoading = ref(false)
const search = ref('')
const studentPage = ref(1)
const classSaving = ref(false)
const studentSaving = ref(false)
const importBusy = ref(false)

const classForm = reactive({ name: '', grade: '初一' })
const classCreateForm = reactive({ name: '', grade: '初一' })
const studentForm = reactive({ studentNo: '', name: '' })
const classOpen = ref(false)
const studentOpen = ref(false)
const classError = ref('')
const studentError = ref('')

const selectedClass = computed(
  () => classes.value.find((item) => item.id === selectedClassId.value) ?? null,
)
const filteredStudents = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return students.value.filter(
    (student) =>
      !keyword ||
      student.name.toLowerCase().includes(keyword) ||
      student.studentNo.toLowerCase().includes(keyword),
  )
})
const pagedStudents = computed(() => {
  const start = (studentPage.value - 1) * PAGE_SIZE
  return filteredStudents.value.slice(start, start + PAGE_SIZE)
})

async function loadClasses(preferredId?: number | null): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    classes.value = await teacherApi.getClasses()
    const target =
      classes.value.find((item) => item.id === preferredId) ??
      classes.value.find((item) => item.id === selectedClassId.value) ??
      classes.value[0] ??
      null
    if (target) {
      await selectClass(target)
    } else {
      selectedClassId.value = null
      students.value = []
    }
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

async function selectClass(item: ClassSummary): Promise<void> {
  selectedClassId.value = item.id
  classForm.name = item.name
  classForm.grade = item.grade
  studentPage.value = 1
  search.value = ''
  await loadStudents()
}

function handleClassChange(event: Event): void {
  const id = Number((event.target as HTMLSelectElement).value)
  const target = classes.value.find((item) => item.id === id)
  if (target) void selectClass(target)
}

async function loadStudents(): Promise<void> {
  if (!selectedClassId.value) return
  studentLoading.value = true
  try {
    const result = await fetchAllPages((page, pageSize) =>
      teacherApi.getClassStudents(selectedClassId.value!, {
        includeDisabled: true,
        page,
        pageSize,
      }),
    )
    students.value = result.items
  } catch (cause) {
    ui.toast('error', '学生名单加载失败', errorMessage(cause))
    students.value = []
  } finally {
    studentLoading.value = false
  }
}

function openClassCreate(): void {
  classError.value = ''
  classCreateForm.name = ''
  classCreateForm.grade = '初一'
  classOpen.value = true
}

async function createClass(): Promise<void> {
  classError.value = ''
  if (!classCreateForm.name.trim()) {
    classError.value = '请填写班级名称。'
    return
  }
  classSaving.value = true
  try {
    const created = await teacherApi.createClass({
      name: classCreateForm.name.trim(),
      grade: classCreateForm.grade.trim() || '初一',
      subject: '数学',
    })
    classOpen.value = false
    ui.toast('success', '班级已创建', `已创建 ${created.name}，可继续添加学生。`)
    await loadClasses(created.id)
  } catch (cause) {
    classError.value = errorMessage(cause)
  } finally {
    classSaving.value = false
  }
}

async function saveClass(): Promise<void> {
  if (!selectedClass.value) return
  classSaving.value = true
  try {
    await teacherApi.updateClass(selectedClass.value.id, {
      name: classForm.name.trim(),
      grade: classForm.grade.trim() || '初一',
    })
    ui.toast('success', '班级信息已更新', '班级名称与年级已保存。')
    await loadClasses(selectedClass.value.id)
  } catch (cause) {
    ui.toast('error', '更新班级失败', errorMessage(cause))
  } finally {
    classSaving.value = false
  }
}

function openStudentCreate(): void {
  studentError.value = ''
  studentForm.studentNo = ''
  studentForm.name = ''
  studentOpen.value = true
}

async function createStudent(): Promise<void> {
  if (!selectedClassId.value) return
  studentError.value = ''
  if (!studentForm.studentNo.trim() || !studentForm.name.trim()) {
    studentError.value = '请填写学号与姓名。'
    return
  }
  studentSaving.value = true
  try {
    await teacherApi.createStudent(selectedClassId.value, {
      studentNo: studentForm.studentNo.trim(),
      name: studentForm.name.trim(),
    })
    studentOpen.value = false
    ui.toast('success', '学生已添加', '名单变化会同步到作业与批改数据范围。')
    await loadStudents()
    await refreshClassCount()
  } catch (cause) {
    studentError.value = errorMessage(cause)
  } finally {
    studentSaving.value = false
  }
}

async function toggleStudentStatus(student: StudentSummary): Promise<void> {
  if (!selectedClassId.value) return
  const next = student.status === 'DISABLED' ? 'ACTIVE' : 'DISABLED'
  const accepted = await ui.confirm({
    title: next === 'DISABLED' ? '停用该学生？' : '恢复该学生？',
    message:
      next === 'DISABLED'
        ? '停用后该学生不再计入班级名册、统计和批改导入覆盖范围，历史记录保留。'
        : '恢复后该学生会重新进入班级名册。',
    confirmText: next === 'DISABLED' ? '停用' : '恢复',
    tone: next === 'DISABLED' ? 'danger' : 'primary',
  })
  if (!accepted) return
  try {
    await teacherApi.updateStudent(selectedClassId.value, student.id, { status: next })
    ui.toast('info', next === 'DISABLED' ? '学生已停用' : '学生已恢复', '历史订正与批改记录不受影响。')
    await loadStudents()
    await refreshClassCount()
  } catch (cause) {
    ui.toast('error', '更新学生失败', errorMessage(cause))
  }
}

async function refreshClassCount(): Promise<void> {
  const currentId = selectedClassId.value
  classes.value = await teacherApi.getClasses()
  if (currentId) {
    const current = classes.value.find((item) => item.id === currentId)
    if (current) {
      selectedClassId.value = current.id
      classForm.name = current.name
      classForm.grade = current.grade
    }
  }
}

async function importStudents(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !selectedClassId.value) return
  importBusy.value = true
  try {
    const text = (await file.text()).replace(/^\uFEFF/, '')
    const lines = text
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean)
    const dataLines = lines[0]?.includes('学号') ? lines.slice(1) : lines
    const rows = dataLines
      .map((line) => {
        const [studentNo, name] = line.split(',').map((value) => value.trim())
        return { studentNo: studentNo ?? '', name: name ?? '' }
      })
      .filter((row) => row.studentNo && row.name)
    if (!rows.length) {
      ui.toast('error', '没有可导入的学生', 'CSV 至少需要两列：学号,姓名。')
      return
    }
    const result = await teacherApi.importStudents(selectedClassId.value, rows)
    ui.toast('success', '学生名单已导入', `新增 ${result.created} 名学生。`)
    await loadStudents()
    await refreshClassCount()
  } catch (cause) {
    ui.toast('error', '导入失败', errorMessage(cause))
  } finally {
    importBusy.value = false
  }
}

function downloadTemplate(): void {
  const blob = new Blob(['\uFEFF学号,姓名\r\nS2024099,示例学生\r\n'], {
    type: 'text/csv;charset=utf-8',
  })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = '学生名单模板.csv'
  anchor.click()
  URL.revokeObjectURL(url)
}

onMounted(() => {
  void loadClasses()
})
</script>

<template>
  <div class="classes-page">
    <PageHeader
      eyebrow="TEACHER · CLASSROOM"
      title="班级与学生"
      subtitle="只维护本人负责的班级与虚构学生名单。停用学生会退出统计口径，但不会删除历史记录。"
    >
      <template #actions>
        <button class="btn btn--primary btn--sm" type="button" @click="openClassCreate">
          <AppIcon name="plus" :size="14" />
          新建班级
        </button>
      </template>
    </PageHeader>

    <div v-if="loading" class="card loading-card">
      <span class="skeleton" style="width: 220px; height: 10px" />
    </div>
    <div v-else-if="error" class="card">
      <AppEmpty icon="alert-circle" title="班级加载失败" :description="error">
        <button class="btn btn--ghost btn--sm" type="button" @click="loadClasses()">重试</button>
      </AppEmpty>
    </div>
    <div v-else-if="!classes.length" class="card">
      <AppEmpty icon="users" title="还没有班级" description="先创建一个班级，再添加或导入虚构学生名单。">
        <button class="btn btn--primary btn--sm" type="button" @click="openClassCreate">新建班级</button>
      </AppEmpty>
    </div>

    <div v-else class="classes-layout">
      <section v-if="selectedClass" class="card class-settings">
        <div class="section-head">
          <div>
            <h3 class="section-title">班级信息</h3>
            <p class="section-desc">修改名称或年级后，作业与名单仍按班级 ID 关联。</p>
          </div>
          <span class="badge" data-tone="mint">ACTIVE</span>
        </div>
        <div class="class-form-grid">
          <label class="field">
            <span>班级名称</span>
            <input v-model="classForm.name" class="input" maxlength="64" />
          </label>
          <label class="field">
            <span>年级</span>
            <input v-model="classForm.grade" class="input" maxlength="32" />
          </label>
          <button class="btn btn--primary btn--sm" type="button" :disabled="classSaving" @click="saveClass">
            保存班级
          </button>
        </div>
      </section>

      <aside class="card class-rail">
        <div class="rail-head">
          <div>
            <h3 class="section-title">我的班级</h3>
            <p class="section-desc">仅显示本人负责的启用班级</p>
          </div>
          <span class="rail-count">{{ classes.length }}</span>
        </div>

        <label class="class-switch">
          <span>当前班级</span>
          <div class="class-select">
            <select class="select" :value="selectedClassId ?? ''" @change="handleClassChange">
              <option v-for="item in classes" :key="item.id" :value="item.id">
                {{ item.name }} · {{ item.studentCount }} 人
              </option>
            </select>
            <AppIcon name="chevron-down" :size="15" class="class-select__arrow" />
          </div>
        </label>

        <div v-if="selectedClass" class="class-summary">
          <div class="class-summary__row">
            <span>班级名称</span>
            <b>{{ selectedClass.name }}</b>
          </div>
          <div class="class-summary__row">
            <span>年级</span>
            <b>{{ selectedClass.grade }}</b>
          </div>
          <div class="class-summary__row">
            <span>学生人数</span>
            <b>{{ selectedClass.studentCount }} 人</b>
          </div>
        </div>

        <p class="rail-foot">切换班级后，班级信息与右侧学生名单会同步更新。</p>
      </aside>

      <section v-if="selectedClass" class="card students-card">
        <div class="section-head">
          <div>
            <h3 class="section-title">学生名单</h3>
            <p class="section-desc">按学号排序；停用学生不参与新批次覆盖校验。</p>
          </div>
          <div class="student-actions">
            <button class="btn btn--ghost btn--sm" type="button" @click="downloadTemplate">
              <AppIcon name="download" :size="14" />
              模板
            </button>
            <label class="btn btn--ghost btn--sm">
              <AppIcon name="upload" :size="14" />
              {{ importBusy ? '导入中…' : '导入 CSV' }}
              <input type="file" accept=".csv,text/csv" hidden :disabled="importBusy" @change="importStudents" />
            </label>
            <button class="btn btn--primary btn--sm" type="button" @click="openStudentCreate">
              <AppIcon name="plus" :size="14" />
              添加学生
            </button>
          </div>
        </div>

        <div class="student-toolbar">
          <label class="search-box">
            <AppIcon name="search" :size="15" />
            <input v-model="search" type="search" placeholder="搜索姓名或学号…" />
          </label>
          <span class="record-total">{{ filteredStudents.length }} 人</span>
        </div>

        <div v-if="studentLoading" class="loading-card">
          <span class="skeleton" style="width: 180px; height: 8px" />
        </div>
        <div v-else-if="pagedStudents.length" class="student-table">
          <div class="student-table-head">
            <span>学生</span>
            <span>学号</span>
            <span>状态</span>
            <span />
          </div>
          <div v-for="student in pagedStudents" :key="student.id" class="student-table-row">
            <span class="student-cell">
              <span class="avatar avatar--sm">{{ student.name.slice(0, 1) }}</span>
              <b>{{ student.name }}</b>
            </span>
            <span class="mono">{{ student.studentNo }}</span>
            <span class="badge" :data-tone="student.status === 'DISABLED' ? 'apricot' : 'mint'">
              {{ student.status === 'DISABLED' ? '已停用' : '在读' }}
            </span>
            <button class="btn btn--ghost btn--xs" type="button" @click="toggleStudentStatus(student)">
              {{ student.status === 'DISABLED' ? '恢复' : '停用' }}
            </button>
          </div>
          <AppPagination
            v-model:page="studentPage"
            :page-size="PAGE_SIZE"
            :total="filteredStudents.length"
            unit="人"
            class="student-pagination"
          />
        </div>
        <AppEmpty
          v-else
          icon="users"
          title="还没有学生"
          description="可以单个添加，也可以按“学号,姓名”两列导入 CSV。"
        >
          <button class="btn btn--primary btn--sm" type="button" @click="openStudentCreate">添加学生</button>
        </AppEmpty>
      </section>
    </div>

    <AppModal v-model="classOpen" title="新建班级" subtitle="创建后你会自动成为该班级的负责教师。" size="sm">
      <div class="stack-form">
        <label class="field">
          <span>班级名称</span>
          <input v-model="classCreateForm.name" class="input" maxlength="64" placeholder="例：初一(3)班" />
        </label>
        <label class="field">
          <span>年级</span>
          <input v-model="classCreateForm.grade" class="input" maxlength="32" placeholder="例：初一" />
        </label>
        <div v-if="classError" class="error-banner">{{ classError }}</div>
      </div>
      <template #footer>
        <button class="btn btn--ghost" type="button" @click="classOpen = false">取消</button>
        <button class="btn btn--primary" type="button" :disabled="classSaving" @click="createClass">创建班级</button>
      </template>
    </AppModal>

    <AppModal v-model="studentOpen" title="添加学生" subtitle="学生使用虚构学号与姓名；登录账号绑定不在本页处理。" size="sm">
      <div class="stack-form">
        <label class="field">
          <span>学号</span>
          <input v-model="studentForm.studentNo" class="input" maxlength="32" placeholder="例：S2024099" />
        </label>
        <label class="field">
          <span>姓名</span>
          <input v-model="studentForm.name" class="input" maxlength="64" placeholder="例：虚构学生99" />
        </label>
        <div v-if="studentError" class="error-banner">{{ studentError }}</div>
      </div>
      <template #footer>
        <button class="btn btn--ghost" type="button" @click="studentOpen = false">取消</button>
        <button class="btn btn--primary" type="button" :disabled="studentSaving" @click="createStudent">添加学生</button>
      </template>
    </AppModal>
  </div>
</template>

<style scoped>
.classes-page {
  max-width: 1220px;
}

.classes-layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 16px;
}

.class-rail {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
  padding: 16px;
}

.rail-head,
.section-head,
.student-toolbar,
.student-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.rail-count,
.record-total {
  color: var(--ink-3);
  font-family: var(--font-mono);
  font-size: 8px;
}

.class-switch {
  display: block;
}

.class-switch > span {
  display: block;
  margin-bottom: 6px;
  color: var(--ink-3);
  font-size: 10.5px;
  letter-spacing: 0.04em;
}

.class-select {
  position: relative;
}

.class-select__arrow {
  position: absolute;
  top: 50%;
  right: 12px;
  color: var(--role-deep);
  pointer-events: none;
  transform: translateY(-50%);
}

.class-select .select {
  padding-right: 34px;
  border-color: color-mix(in srgb, var(--role) 16%, var(--line-strong));
  background: color-mix(in srgb, var(--role) 5%, #fff);
  cursor: pointer;
}

.class-select .select:focus {
  background: color-mix(in srgb, var(--role) 9%, #fff);
}

.class-select :deep(option) {
  color: var(--ink);
  background: color-mix(in srgb, var(--role) 4%, #fff);
}

.class-summary {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface-soft);
}

.class-summary__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 11px;
}

.class-summary__row span {
  flex: none;
  color: var(--ink-3);
}

.class-summary__row b {
  overflow: hidden;
  color: var(--ink);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rail-foot {
  margin-top: auto;
  padding-top: 12px;
  color: var(--ink-4);
  font-size: 9.5px;
  line-height: 1.7;
}

.class-settings {
  grid-column: 1 / -1;
  min-width: 0;
}

.rail-head {
  margin-bottom: 0;
}

.class-settings,
.students-card {
  padding: 18px;
}

.student-pagination.app-pagination {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 10px;
}

.student-pagination.app-pagination :deep(> span) {
  grid-column: 2;
  text-align: center;
}

.student-pagination.app-pagination :deep(> div) {
  grid-column: 3;
  justify-self: end;
}

.class-form-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 170px auto;
  gap: 10px;
  align-items: end;
  margin-top: 14px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 5px;
  color: var(--ink-3);
  font-size: 8px;
}

.student-actions {
  flex-wrap: wrap;
}

.student-toolbar {
  margin: 12px 0;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 6px;
  width: min(300px, 100%);
  padding: 7px 10px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
}

.search-box input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  font-size: 9px;
}

.student-table {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.student-table-head,
.student-table-row {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(120px, 0.8fr) 90px 70px;
  gap: 10px;
  align-items: center;
}

.student-table-head {
  padding: 0 10px 7px;
  color: var(--ink-3);
  font-size: 8px;
}

.student-table-row {
  padding: 9px 10px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
  font-size: 9px;
}

.student-cell {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
}

.student-cell b {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mono {
  color: var(--ink-3);
  font-family: var(--font-mono);
  font-size: 8px;
}

.stack-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

@media (max-width: 860px) {
  .classes-layout {
    grid-template-columns: 1fr;
  }

  .class-form-grid {
    grid-template-columns: 1fr 1fr;
  }

  .class-form-grid .btn {
    grid-column: 1 / -1;
  }

  .student-table-head,
  .student-table-row {
    grid-template-columns: minmax(0, 1fr) minmax(90px, 0.7fr) 70px;
  }

  .student-table-head span:last-child,
  .student-table-row > :last-child {
    display: none;
  }
}
</style>
