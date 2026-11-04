<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { teacherApi } from '@/api/teacher'
import { ApiError, errorMessage } from '@/api/errors'
import { isMockMode } from '@/api/client'
import type {
  GradingBatch,
  GradingImportResult,
  GradingRecord,
  HomeworkDetail,
  HomeworkStatistics,
  ImportPreview,
  ImportRow,
  ImportRowError,
  Question,
  StudentSummary,
} from '@/types/models'
import { useTeacherWorkspace } from '@/stores/teacherWorkspace'
import { useUiStore } from '@/stores/ui'
import { formatDateTime, formatFullDateTime, pct } from '@/utils/format'
import { fetchAllPages } from '@/utils/pagination'
import PageHeader from '@/components/PageHeader.vue'
import AppIcon from '@/components/AppIcon.vue'
import AppEmpty from '@/components/AppEmpty.vue'
import AppModal from '@/components/AppModal.vue'

const route = useRoute()
const router = useRouter()
const workspace = useTeacherWorkspace()
const ui = useUiStore()

const homeworkId = computed(() => Number(route.params.id))
const homework = ref<HomeworkDetail | null>(null)
const statistics = ref<HomeworkStatistics | null>(null)
const batches = ref<GradingBatch[]>([])
const records = ref<GradingRecord[]>([])
const students = ref<StudentSummary[]>([])
const loading = ref(true)
const pageError = ref('')
const activeTab = ref<'import' | 'records'>('import')

const fileInput = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const preview = ref<ImportPreview | null>(null)
const previewRows = computed(() => preview.value?.rows.slice(0, 10) ?? [])
const rowErrors = computed(() => preview.value?.rowErrors ?? [])
const importing = ref(false)
const lastImport = ref<GradingImportResult | null>(null)
const previewNotice = ref('')

const selectedBatchId = ref<number | null>(null)
const recordsLoading = ref(false)
const recordsPage = ref(1)
const recordsPageSize = ref(6)
const recordsTotal = ref(0)
const recordFilters = reactive({ keyword: '', seqNo: '', correctness: '' })

const editOpen = ref(false)
const editingRecord = ref<GradingRecord | null>(null)
const editingQuestion = ref<Question | null>(null)
const recordSaving = ref(false)
const recordError = ref('')
const recordForm = reactive({
  studentAnswer: '',
  isCorrect: true,
  teacherNote: '',
})

const currentBatch = computed(
  () => batches.value.find((batch) => batch.id === homework.value?.currentGradingBatchId) ?? batches.value[0] ?? null,
)
const selectedBatch = computed(
  () => batches.value.find((batch) => batch.id === selectedBatchId.value) ?? currentBatch.value,
)
const isHistoricalBatch = computed(
  () => Boolean(selectedBatch.value && selectedBatch.value.id !== homework.value?.currentGradingBatchId),
)
const totalPages = computed(() => Math.max(1, Math.ceil(recordsTotal.value / recordsPageSize.value)))
const canImport = computed(
  () =>
    homework.value?.status === 'PUBLISHED' &&
    Boolean(selectedFile.value) &&
    preview.value !== null &&
    !rowErrors.value.length &&
    !importing.value,
)
const questionMap = computed(
  () => new Map((homework.value?.questions ?? []).map((question) => [question.seqNo, question])),
)

async function load(): Promise<void> {
  loading.value = true
  pageError.value = ''
  try {
    const detail = await teacherApi.getHomework(homeworkId.value)
    homework.value = detail
    workspace.selectHomework(detail.id)
    workspace.loaded = false
    await workspace.load(true)
    const [batchList, studentResult] = await Promise.all([
      teacherApi.listGradingBatches(detail.id),
      fetchAllPages((page, pageSize) =>
        teacherApi.getClassStudents(detail.classId, { page, pageSize }),
      ),
    ])
    batches.value = batchList
    students.value = studentResult.items
    selectedBatchId.value = detail.currentGradingBatchId
    await Promise.all([loadStatistics(), loadRecords()])
  } catch (cause) {
    pageError.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

async function loadStatistics(): Promise<void> {
  if (!homework.value?.currentGradingBatchId) {
    statistics.value = null
    return
  }
  try {
    statistics.value = await teacherApi.getStatistics(homework.value.id)
  } catch {
    statistics.value = null
  }
}

async function loadRecords(): Promise<void> {
  if (!homework.value) return
  recordsLoading.value = true
  try {
    const page = await teacherApi.listGradingRecords(homework.value.id, {
      batchId: selectedBatchId.value ?? undefined,
      keyword: recordFilters.keyword || undefined,
      seqNo: recordFilters.seqNo ? Number(recordFilters.seqNo) : undefined,
      isCorrect:
        recordFilters.correctness === ''
          ? undefined
          : recordFilters.correctness === 'true',
      page: recordsPage.value,
      pageSize: recordsPageSize.value,
    })
    records.value = page.items
    recordsTotal.value = page.total
  } catch (cause) {
    ui.toast('error', '批改记录加载失败', errorMessage(cause))
  } finally {
    recordsLoading.value = false
  }
}

const templateRows = ref<ImportRow[]>([])

async function fetchAllCurrentRows(): Promise<ImportRow[]> {
  if (!homework.value?.currentGradingBatchId) return []
  const page = await teacherApi.listGradingRecords(homework.value.id, {
    batchId: homework.value.currentGradingBatchId,
    page: 1,
    pageSize: 100,
  })
  const items = [...page.items]
  // 45 人 × 8 题超过 100 行，按总页数补齐。
  const totalPages = Math.ceil(page.total / page.pageSize)
  for (let index = 2; index <= totalPages; index += 1) {
    const next = await teacherApi.listGradingRecords(homework.value.id, {
      batchId: homework.value.currentGradingBatchId,
      page: index,
      pageSize: page.pageSize,
    })
    items.push(...next.items)
  }
  return items.map((record) => ({
    studentNo: record.studentNo,
    questionSeq: record.seqNo,
    studentAnswer: record.studentAnswer ?? '',
    isCorrect: record.isCorrect,
  }))
}

async function prepareTemplateRows(): Promise<ImportRow[]> {
  if (templateRows.value.length) return templateRows.value
  templateRows.value = await fetchAllCurrentRows()
  return templateRows.value
}

function csvEscape(value: string): string {
  return /[",\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value
}

async function downloadTemplate(): Promise<void> {
  let rows = await prepareTemplateRows()
  if (!rows.length) {
    rows = students.value.flatMap((student) =>
      (homework.value?.questions ?? []).map((question) => ({
        studentNo: student.studentNo,
        questionSeq: question.seqNo,
        studentAnswer: '',
        isCorrect: false,
      })),
    )
  }
  const header = ['学号', '题号', '学生答案', '是否正确']
  const lines = [
    header.join(','),
    ...rows.map((row) =>
      [row.studentNo, String(row.questionSeq), csvEscape(row.studentAnswer), String(row.isCorrect)].join(','),
    ),
  ]
  const blob = new Blob([`\uFEFF${lines.join('\r\n')}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `批改导入模板_${homework.value?.title ?? '作业'}.csv`
  anchor.click()
  URL.revokeObjectURL(url)
  ui.toast('info', '模板已生成', '学号、题号、学生答案、是否正确四列为导入必填。')
}

function parseBoolean(value: string): boolean | null {
  const text = value.trim().toLowerCase()
  if (['true', '1', '正确', '对', '是'].includes(text)) return true
  if (['false', '0', '错误', '错', '否'].includes(text)) return false
  return null
}

function validateRows(rows: ImportRow[], errors: ImportRowError[]): ImportRowError[] {
  const result = [...errors]
  const studentMap = new Map(students.value.map((student) => [student.studentNo, student]))
  const questionSet = new Set((homework.value?.questions ?? []).map((question) => question.seqNo))
  const keys = new Set<string>()
  rows.forEach((row, index) => {
    const rowNumber = index + 2
    if (!studentMap.has(row.studentNo)) {
      result.push({
        rowNumber,
        field: '学号',
        message: `学号 ${row.studentNo || '（空）'} 不在本班名单中`,
        rejectedValue: row.studentNo,
      })
    }
    if (!questionSet.has(row.questionSeq)) {
      result.push({
        rowNumber,
        field: '题号',
        message: `题号 ${row.questionSeq || '（空）'} 不存在`,
        rejectedValue: String(row.questionSeq),
      })
    }
    if (typeof row.isCorrect !== 'boolean') {
      result.push({
        rowNumber,
        field: '是否正确',
        message: '是否正确需填写 true / false 或 正确 / 错误',
      })
    }
    const key = `${row.studentNo}-${row.questionSeq}`
    if (keys.has(key)) {
      result.push({
        rowNumber,
        field: '学号 + 题号',
        message: `重复行：${row.studentNo} 第 ${row.questionSeq} 题`,
      })
    }
    keys.add(key)
  })
  let missing = 0
  for (const student of students.value) {
    for (const question of homework.value?.questions ?? []) {
      if (!keys.has(`${student.studentNo}-${question.seqNo}`)) missing += 1
    }
  }
  if (missing) {
    result.push({
      rowNumber: 0,
      field: '整批覆盖',
      message: `缺少 ${missing} 条学生 × 题目记录。Excel 导入要求整批完整，任一行出错整批不写入。`,
    })
  }
  return result
}

function parseCsv(text: string, fileName: string): ImportPreview {
  const errors: ImportRowError[] = []
  const lines = text.replace(/^\uFEFF/, '').split(/\r?\n/).filter((line) => line.trim() !== '')
  if (!lines.length) {
    return { fileName, rows: [], rowErrors: [{ rowNumber: 0, message: '文件为空。', field: 'file' }] }
  }
  const headers = lines[0].split(',').map((header) => header.trim())
  const indexes = {
    studentNo: headers.findIndex((header) => ['学号', 'studentNo'].includes(header)),
    questionSeq: headers.findIndex((header) => ['题号', 'questionSeq'].includes(header)),
    studentAnswer: headers.findIndex((header) => ['学生答案', 'studentAnswer'].includes(header)),
    isCorrect: headers.findIndex((header) => ['是否正确', 'isCorrect'].includes(header)),
  }
  if (Object.values(indexes).some((index) => index === -1)) {
    return {
      fileName,
      rows: [],
      rowErrors: [
        {
          rowNumber: 1,
          field: '表头',
          message: '表头必须包含：学号、题号、学生答案、是否正确。',
        },
      ],
    }
  }
  const rows: ImportRow[] = []
  for (let index = 1; index < lines.length; index += 1) {
    const cells = lines[index].split(',').map((cell) => cell.trim().replace(/^"|"$/g, ''))
    const isCorrect = parseBoolean(cells[indexes.isCorrect] ?? '')
    if (isCorrect === null) {
      errors.push({
        rowNumber: index + 1,
        field: '是否正确',
        message: '是否正确需填写 true / false 或 正确 / 错误',
        rejectedValue: cells[indexes.isCorrect] ?? '',
      })
      continue
    }
    rows.push({
      studentNo: cells[indexes.studentNo] ?? '',
      questionSeq: Number(cells[indexes.questionSeq]),
      studentAnswer: cells[indexes.studentAnswer] ?? '',
      isCorrect,
    })
  }
  return { fileName, rows, rowErrors: validateRows(rows, errors) }
}

async function onFileSelected(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  selectedFile.value = file
  lastImport.value = null
  previewNotice.value = ''
  const extension = file.name.slice(file.name.lastIndexOf('.')).toLowerCase()
  if (extension === '.csv') {
    const text = await file.text()
    preview.value = parseCsv(text, file.name)
    previewNotice.value = 'CSV 已在本地预检；正式提交仍由后端做原子校验。'
    return
  }
  if (extension === '.xlsx' || extension === '.xls') {
    if (isMockMode) {
      const rows = await fetchAllCurrentRows()
      preview.value = {
        fileName: file.name,
        rows,
        rowErrors: rows.length ? [] : [{ rowNumber: 0, message: '当前批次没有可用记录，无法模拟 xlsx 解析。', field: 'file' }],
      }
      previewNotice.value = 'Mock 模式使用当前批次记录模拟服务端 xlsx 解析结果；真实模式由 Spring Boot 解析文件并返回行级错误。'
    } else {
      preview.value = {
        fileName: file.name,
        rows: [],
        rowErrors: [],
      }
      previewNotice.value = '文件将在提交后由后端解析，前端展示服务端返回的行级校验结果。'
    }
    return
  }
  preview.value = {
    fileName: file.name,
    rows: [],
    rowErrors: [{ rowNumber: 0, field: 'file', message: '仅支持 .xlsx / .xls / .csv 文件。' }],
  }
}

async function loadDemoPreview(withError = false): Promise<void> {
  const rows = await prepareTemplateRows()
  if (!rows.length) {
    ui.toast('info', '暂无可复用批次', '请先生成模板并填写，或先完成一次批改导入。')
    return
  }
  const nextRows = rows.map((row) => ({ ...row }))
  const errors: ImportRowError[] = []
  if (withError && nextRows.length) {
    nextRows[0].questionSeq = 999
    errors.push({
      rowNumber: 2,
      field: '题号',
      message: '题号 999 不存在（演示行级错误，整批将被拒绝）',
      rejectedValue: '999',
    })
  }
  selectedFile.value = new File(
    [JSON.stringify(nextRows)],
    withError ? '演示_含错误行.csv' : '演示_完整批次.csv',
    { type: 'text/csv' },
  )
  preview.value = {
    fileName: selectedFile.value.name,
    rows: nextRows,
    rowErrors: withError ? validateRows(nextRows, errors) : [],
  }
  previewNotice.value = withError
    ? '这是一份故意含错误的示例：后端会拒绝整批，不写入任何记录。'
    : '这是一份可导入的完整示例，复用当前批次的 45 × 题数条记录。'
}

async function submitImport(): Promise<void> {
  if (!preview.value || !homework.value) return
  if (!canImport.value) return
  const accepted = await ui.confirm({
    title: '导入这份批改数据？',
    message: `将生成新的完整批次快照，当前批次 V${homework.value.currentGradingBatchVersion ?? '—'} 会保留。统计将按新批次自动重算，历史批次不覆盖。`,
    confirmText: '确认导入',
  })
  if (!accepted) return
  importing.value = true
  try {
    const result = await teacherApi.importGrading(homework.value.id, selectedFile.value, {
      rows: preview.value.rows,
      remark: `导入 ${preview.value.fileName}`,
      fileName: preview.value.fileName,
      confirmNewBatch: true,
    })
    lastImport.value = result
    ui.toast('success', `批改批次 V${result.batch.versionNo} 已导入`, '统计已按新批次重新计算，旧批次保留可追溯。')
    selectedFile.value = null
    preview.value = null
    if (fileInput.value) fileInput.value.value = ''
    await load()
    activeTab.value = 'records'
  } catch (cause) {
    ui.toast('error', '导入失败', importErrorMessage(cause))
  } finally {
    importing.value = false
  }
}

function importErrorMessage(cause: unknown): string {
  if (cause instanceof ApiError && cause.code === 'GRADING_IMPORT_INVALID_ROWS') {
    const data = cause.data as
      | { rowErrors?: Array<{ rowNumber: number; message: string }> }
      | undefined
    const errors = data?.rowErrors ?? []
    if (errors.length) {
      const preview = errors
        .slice(0, 3)
        .map((item) => `${item.rowNumber > 0 ? `第 ${item.rowNumber} 行：` : ''}${item.message}`)
        .join('；')
      return errors.length > 3 ? `${preview}；另有 ${errors.length - 3} 条错误` : preview
    }
  }
  return errorMessage(cause)
}

function openEditRecord(record: GradingRecord): void {
  editingRecord.value = record
  editingQuestion.value = questionMap.value.get(record.seqNo) ?? null
  recordForm.studentAnswer = record.studentAnswer ?? ''
  recordForm.isCorrect = record.isCorrect
  recordForm.teacherNote = record.teacherNote ?? ''
  recordError.value = ''
  editOpen.value = true
}

async function saveRecord(): Promise<void> {
  if (!editingRecord.value || !homework.value) return
  recordSaving.value = true
  recordError.value = ''
  try {
    const result = await teacherApi.updateGradingRecord(editingRecord.value.id, {
      studentAnswer: recordForm.studentAnswer.trim() || null,
      isCorrect: recordForm.isCorrect,
      score: recordForm.isCorrect ? editingQuestion.value?.score ?? editingRecord.value.score : 0,
      teacherNote: recordForm.teacherNote.trim() || null,
      expectedBatchId: editingRecord.value.batchId,
      remark: '人工修订单条批改记录',
    })
    ui.toast('success', `已生成新批次 V${result.batch.versionNo}`, '原批次完整保留，统计已按新版本重算。')
    editOpen.value = false
    await load()
  } catch (cause) {
    recordError.value = errorMessage(cause)
  } finally {
    recordSaving.value = false
  }
}

function applyFilters(): void {
  recordsPage.value = 1
  void loadRecords()
}

function changePage(page: number): void {
  if (page < 1 || page > totalPages.value) return
  recordsPage.value = page
  void loadRecords()
}

function selectBatch(batch: GradingBatch): void {
  selectedBatchId.value = batch.id
  recordsPage.value = 1
  activeTab.value = 'records'
  void loadRecords()
}

function backToCurrent(): void {
  selectedBatchId.value = homework.value?.currentGradingBatchId ?? null
  recordsPage.value = 1
  void loadRecords()
}

function goStatistics(): void {
  if (!homework.value) return
  void router.push(`/teacher/homeworks/${homework.value.id}/statistics`)
}

function goManual(): void {
  if (!homework.value) return
  void router.push(`/teacher/homeworks/${homework.value.id}/grading/manual`)
}

function goReview(): void {
  if (!homework.value) return
  void router.push(`/teacher/homeworks/${homework.value.id}/review`)
}

onMounted(load)
</script>

<template>
  <div class="grading-page">
    <div v-if="loading" class="card loading-card">
      <span class="skeleton" style="width: 220px; height: 10px" />
    </div>

    <div v-else-if="pageError" class="card">
      <AppEmpty icon="alert-circle" title="无法加载批改数据" :description="pageError">
        <button class="btn btn--ghost btn--sm" @click="router.push('/teacher/homeworks')">返回作业列表</button>
      </AppEmpty>
    </div>

    <template v-else-if="homework">
      <PageHeader
        eyebrow="TEACHER · GRADING"
        title="批改与统计"
        :subtitle="`《${homework.title}》 · ${homework.className}。每次导入/修订都会生成完整批次快照，统计不可手工修改。`"
      >
        <template #actions>
          <button class="btn btn--ghost btn--sm" @click="goManual">
            <AppIcon name="edit" :size="14" />
            手工录入
          </button>
          <button class="btn btn--ghost btn--sm" @click="goReview">
            <AppIcon name="sparkles" :size="14" />
            讲评审核
          </button>
          <button class="btn btn--primary btn--sm" @click="goStatistics">
            <AppIcon name="chart" :size="14" />
            错误统计
          </button>
        </template>
      </PageHeader>

      <div class="grading-summary">
        <div>
          <span>当前批次</span>
          <b>{{ currentBatch ? `V${currentBatch.versionNo}` : '未导入' }}</b>
          <small>{{ currentBatch ? formatDateTime(currentBatch.createdAt) : '尚无批改事实' }}</small>
        </div>
        <div>
          <span>有效记录</span>
          <b>{{ currentBatch?.validRows ?? 0 }}<small> 条</small></b>
          <small>{{ students.length }} 人 × {{ homework.questionCount }} 题</small>
        </div>
        <div>
          <span>错题总数</span>
          <b>{{ statistics?.overview.totalWrong ?? '—' }}<small> 条</small></b>
          <small>{{ statistics ? `题均得分 ${statistics.overview.averageScore ?? '—'}` : '待统计' }}</small>
        </div>
        <div>
          <span>重点错题</span>
          <b>{{ statistics?.questions.length ? statistics.questions.reduce((best, item) => item.errorRate > best.errorRate ? item : best).seqNo : '—' }}</b>
          <small>{{ statistics?.questions.length ? `最高错误率 ${pct(Math.max(...statistics.questions.map((item) => item.errorRate)), 1)}` : '待统计' }}</small>
        </div>
      </div>

      <div v-if="homework.status !== 'PUBLISHED'" class="info-banner info-banner--warm" style="margin-bottom: 16px">
        <AppIcon name="alert-triangle" :size="16" />
        作业当前为{{ homework.status === 'DRAFT' ? '草稿' : '已归档' }}状态。批改导入只对已发布作业开放。
        <RouterLink :to="`/teacher/homeworks/${homework.id}`" class="text-link">去作业详情处理</RouterLink>
      </div>

      <div class="grading-layout">
        <div class="grading-main">
          <div class="tabs grading-tabs">
            <button :class="{ 'is-active': activeTab === 'import' }" type="button" @click="activeTab = 'import'">
              导入 / 修订批次
            </button>
            <button :class="{ 'is-active': activeTab === 'records' }" type="button" @click="activeTab = 'records'">
              逐条核查
              <span class="tab-count">{{ recordsTotal }}</span>
            </button>
          </div>

          <template v-if="activeTab === 'import'">
            <section class="card import-card">
              <div class="section-head">
                <div>
                  <h3 class="section-title">Excel / CSV 整批导入</h3>
                  <p class="section-desc">
                    必填列：学号、题号、学生答案、是否正确。任一行错误则整批不写入（422）。
                  </p>
                </div>
                <span class="source-badge">ALL-OR-NOTHING</span>
              </div>

              <div v-if="lastImport" class="success-banner">
                <AppIcon name="check-circle" :size="17" />
                <div>
                  <b>批次 V{{ lastImport.batch.versionNo }} 已生效</b>
                  <span>
                    {{ lastImport.batch.validRows }} 条记录 · 统计{{ lastImport.statisticsCalculated ? '已重算' : '待重算' }} ·
                    旧批次仍保留
                  </span>
                </div>
              </div>

              <div class="import-steps">
                <div class="import-step">
                  <span>01</span>
                  <div>
                    <b>准备数据</b>
                    <p>下载模板，按学生一行一题填写；题号与学号必须属于本班当前作业。</p>
                  </div>
                </div>
                <div class="import-step">
                  <span>02</span>
                  <div>
                    <b>本地预检</b>
                    <p>Mock 模式下 CSV 在浏览器预检；真实模式由 Spring Boot 返回行级错误。</p>
                  </div>
                </div>
                <div class="import-step">
                  <span>03</span>
                  <div>
                    <b>整批写入</b>
                    <p>成功即生成新版本快照。修订单条记录同样生成新批次，不覆盖历史。</p>
                  </div>
                </div>
              </div>

              <div class="dropzone-row">
                <button class="dropzone" type="button" :disabled="homework.status !== 'PUBLISHED'" @click="fileInput?.click()">
                  <span class="dropzone-icon"><AppIcon name="upload" :size="22" /></span>
                  <b>{{ selectedFile ? selectedFile.name : '选择批改文件' }}</b>
                  <span>支持 .xlsx / .xls / .csv，最大由后端限制</span>
                  <em>或拖入文件（演示页使用点击选择）</em>
                </button>
                <div class="import-tools">
                  <button class="btn btn--ghost btn--sm" type="button" @click="downloadTemplate">
                    <AppIcon name="download" :size="14" />
                    下载模板
                  </button>
                  <button class="btn btn--soft btn--sm" type="button" :disabled="!homework.currentGradingBatchId" @click="loadDemoPreview(false)">
                    载入完整示例
                  </button>
                  <button class="btn btn--quiet btn--sm" type="button" :disabled="!homework.currentGradingBatchId" @click="loadDemoPreview(true)">
                    载入含错示例
                  </button>
                </div>
                <input
                  ref="fileInput"
                  class="visually-hidden"
                  type="file"
                  accept=".xlsx,.xls,.csv"
                  @change="onFileSelected"
                />
              </div>

              <div v-if="preview" class="preview-block">
                <div class="preview-head">
                  <div>
                    <h4>导入预览</h4>
                    <p>{{ previewNotice }}</p>
                  </div>
                  <div class="preview-counts">
                    <span><b>{{ preview.rows.length }}</b> 行数据</span>
                    <span :class="{ 'has-error': rowErrors.length }"><b>{{ rowErrors.length }}</b> 处问题</span>
                  </div>
                </div>

                <div v-if="rowErrors.length" class="error-list">
                  <div v-for="(item, index) in rowErrors.slice(0, 8)" :key="`${item.rowNumber}-${index}`" class="error-row">
                    <span>{{ item.rowNumber ? `第 ${item.rowNumber} 行` : '整批' }}</span>
                    <b>{{ item.field || '文件' }}</b>
                    <p>{{ item.message }}</p>
                  </div>
                  <p v-if="rowErrors.length > 8" class="more-errors">还有 {{ rowErrors.length - 8 }} 处问题未显示，整批暂不可提交。</p>
                </div>

                <div v-if="previewRows.length" class="table-wrap preview-table">
                  <table class="data-table">
                    <thead>
                      <tr><th>学号</th><th>题号</th><th>学生答案</th><th>是否正确</th></tr>
                    </thead>
                    <tbody>
                      <tr v-for="(row, index) in previewRows" :key="index">
                        <td class="cell-strong">{{ row.studentNo }}</td>
                        <td>第 {{ row.questionSeq }} 题</td>
                        <td>{{ row.studentAnswer || '未作答' }}</td>
                        <td>
                          <span class="badge" :data-tone="row.isCorrect ? 'mint' : 'coral'">
                            {{ row.isCorrect ? '正确' : '错误' }}
                          </span>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                  <p v-if="preview.rows.length > 10" class="preview-more">仅展示前 10 行，共 {{ preview.rows.length }} 行。</p>
                </div>

                <div class="preview-actions">
                  <button class="btn btn--ghost btn--sm" type="button" @click="preview = null; selectedFile = null">
                    取消
                  </button>
                  <button class="btn btn--primary btn--sm" type="button" :disabled="!canImport" @click="submitImport">
                    <AppIcon v-if="importing" name="refresh" :size="14" />
                    确认整批导入
                  </button>
                </div>
              </div>
            </section>

            <section class="card audit-note">
              <span class="audit-icon"><AppIcon name="shield" :size="18" /></span>
              <div>
                <h4>统计口径与不可变事实</h4>
                <p>
                  对错与得分由后端判定并写入记录；前端只展示。统计通过原始记录重算，页面没有修改统计数字的入口。
                  未作答单独计数，错误率分母为本应交人数（暂定口径，待业务确认）。
                </p>
              </div>
            </section>
          </template>

          <template v-else>
            <section class="card records-card">
              <div class="section-head">
                <div>
                  <h3 class="section-title">
                    逐条核查
                    <span v-if="isHistoricalBatch" class="history-chip">只读历史批次 V{{ selectedBatch?.versionNo }}</span>
                  </h3>
                  <p class="section-desc">
                    {{ isHistoricalBatch ? '历史批次仅供追溯，修订请回到当前批次。' : '点击修订会创建新的完整批次，原记录保留。' }}
                  </p>
                </div>
                <button v-if="isHistoricalBatch" class="btn btn--soft btn--sm" type="button" @click="backToCurrent">
                  回到当前批次
                </button>
              </div>

              <div class="record-filters">
                <label class="search-box">
                  <AppIcon name="search" :size="15" />
                  <input v-model="recordFilters.keyword" type="search" placeholder="学号或姓名…" @keyup.enter="applyFilters" />
                </label>
                <select v-model="recordFilters.seqNo" class="select filter-select" @change="applyFilters">
                  <option value="">全部题目</option>
                  <option v-for="question in homework.questions" :key="question.id" :value="String(question.seqNo)">
                    第 {{ question.seqNo }} 题
                  </option>
                </select>
                <select v-model="recordFilters.correctness" class="select filter-select" @change="applyFilters">
                  <option value="">全部结果</option>
                  <option value="false">只看错误</option>
                  <option value="true">只看正确</option>
                </select>
                <button class="btn btn--ghost btn--sm" type="button" @click="applyFilters">
                  <AppIcon name="filter" :size="14" />
                  筛选
                </button>
              </div>

              <div v-if="recordsLoading" class="loading-card"><span class="skeleton" style="width: 180px; height: 8px" /></div>
              <div v-else-if="records.length" class="table-wrap">
                <table class="data-table records-table">
                  <thead>
                    <tr>
                      <th>学生</th>
                      <th>题目</th>
                      <th>作答</th>
                      <th>结果</th>
                      <th>得分</th>
                      <th>备注</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="record in records" :key="record.id">
                      <td>
                        <div class="student-cell">
                          <span class="avatar avatar--sm">{{ record.studentName.slice(0, 1) }}</span>
                          <div>
                            <b>{{ record.studentName }}</b>
                            <span>{{ record.studentNo }}</span>
                          </div>
                        </div>
                      </td>
                      <td>第 {{ record.seqNo }} 题</td>
                      <td>{{ record.studentAnswer || '未作答' }}</td>
                      <td>
                        <span class="badge" :data-tone="record.isCorrect ? 'mint' : 'coral'">
                          {{ record.isCorrect ? '正确' : '错误' }}
                        </span>
                      </td>
                      <td class="cell-num">{{ record.score }}</td>
                      <td class="note-cell">{{ record.teacherNote || '—' }}</td>
                      <td class="actions">
                        <button
                          class="btn btn--quiet btn--xs"
                          type="button"
                          :disabled="isHistoricalBatch"
                          @click="openEditRecord(record)"
                        >
                          修订
                        </button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <AppEmpty
                v-else
                icon="search"
                title="没有匹配的批改记录"
                description="尝试清空筛选条件，或先导入一个批改批次。"
              />

              <div v-if="recordsTotal > 0" class="pagination">
                <span>共 {{ recordsTotal }} 条 · 第 {{ recordsPage }} / {{ totalPages }} 页</span>
                <div>
                  <button class="btn btn--ghost btn--xs" type="button" :disabled="recordsPage <= 1" @click="changePage(recordsPage - 1)">
                    上一页
                  </button>
                  <button class="btn btn--ghost btn--xs" type="button" :disabled="recordsPage >= totalPages" @click="changePage(recordsPage + 1)">
                    下一页
                  </button>
                </div>
              </div>
            </section>
          </template>
        </div>

        <aside class="batch-side">
          <section class="card batch-card">
            <div class="section-head">
              <div>
                <h3 class="section-title">批次历史</h3>
                <p class="section-desc">追加式快照，不覆盖旧版本。</p>
              </div>
              <span class="batch-total">{{ batches.length }} 版</span>
            </div>
            <div v-if="batches.length" class="batch-list">
              <button
                v-for="batch in batches"
                :key="batch.id"
                type="button"
                class="batch-item"
                :class="{ 'is-current': batch.id === homework.currentGradingBatchId, 'is-selected': batch.id === selectedBatchId }"
                @click="selectBatch(batch)"
              >
                <span class="batch-version">V{{ batch.versionNo }}</span>
                <div class="batch-copy">
                  <b>{{ batch.remark || '批改批次' }}</b>
                  <span>{{ batch.source }} · {{ batch.validRows }} 条 · {{ formatDateTime(batch.createdAt) }}</span>
                </div>
                <span v-if="batch.id === homework.currentGradingBatchId" class="batch-current">当前</span>
                <AppIcon v-else name="chevron-right" :size="13" class="batch-arrow" />
              </button>
            </div>
            <AppEmpty v-else icon="upload" title="还没有批改批次" description="导入第一份批改文件后，这里会记录完整版本链。" />
          </section>

          <section class="card batch-help">
            <span class="batch-help__icon"><AppIcon name="help" :size="17" /></span>
            <h4>为什么不能直接改统计？</h4>
            <p>
              统计是 L2 派生结果，只能随原始记录重算。修改一条批改记录会生成新批次，
              原批次与对应讲评依据仍可追溯，不会静默覆盖历史。
            </p>
          </section>

          <section v-if="statistics" class="card top-wrong-card">
            <h3 class="section-title">重点错题</h3>
            <button
              v-for="question in [...statistics.questions].sort((a, b) => b.errorRate - a.errorRate).slice(0, 3)"
              :key="question.questionId"
              class="top-wrong-row"
              type="button"
              @click="goStatistics"
            >
              <span>第 {{ question.seqNo }} 题</span>
              <div class="top-wrong-bar"><i :style="{ width: `${question.errorRate}%` }" /></div>
              <b>{{ question.errorRate.toFixed(1) }}%</b>
            </button>
          </section>
        </aside>
      </div>

      <AppModal v-model="editOpen" title="人工修订批改记录" subtitle="修订会生成新批次快照，原批次不会被覆盖" size="md">
        <div v-if="editingRecord" class="record-edit">
          <div class="record-edit__head">
            <span class="avatar">{{ editingRecord.studentName.slice(0, 1) }}</span>
            <div>
              <b>{{ editingRecord.studentName }} · 第 {{ editingRecord.seqNo }} 题</b>
              <span>{{ editingRecord.studentNo }} · {{ formatFullDateTime(editingRecord.createdAt) }}</span>
            </div>
            <span class="tag">原批次 V{{ currentBatch?.versionNo }}</span>
          </div>

          <div v-if="editingQuestion" class="standard-line">
            <span>标准答案</span>
            <b>{{ editingQuestion.standardAnswer === 'TRUE' ? '正确' : editingQuestion.standardAnswer === 'FALSE' ? '错误' : editingQuestion.standardAnswer || '未填写' }}</b>
            <span class="standard-divider" />
            <span>知识点</span>
            <b>{{ editingQuestion.knowledgePointIds.length }} 个关联</b>
          </div>

          <div class="field">
            <label for="record-answer">学生答案</label>
            <input id="record-answer" v-model="recordForm.studentAnswer" class="input" placeholder="未作答可留空" />
          </div>

          <div class="field">
            <label>判定结果</label>
            <div class="truth-options">
              <label :class="{ 'is-active': recordForm.isCorrect }">
                <input v-model="recordForm.isCorrect" type="radio" :value="true" />
                正确
              </label>
              <label :class="{ 'is-active': !recordForm.isCorrect }">
                <input v-model="recordForm.isCorrect" type="radio" :value="false" />
                错误
              </label>
            </div>
            <span class="field-hint">得分由后端按题目分值和判定结果计算；前端不参与判分。</span>
          </div>

          <div class="field">
            <label for="record-note">教师备注</label>
            <textarea id="record-note" v-model="recordForm.teacherNote" class="textarea" maxlength="200" placeholder="可选：记录修订原因或补充说明" />
          </div>

          <div v-if="recordError" class="error-banner">
            <AppIcon name="alert-circle" :size="15" />
            {{ recordError }}
          </div>
        </div>
        <template #footer>
          <button class="btn btn--ghost" type="button" @click="editOpen = false">取消</button>
          <button class="btn btn--primary" type="button" :disabled="recordSaving" @click="saveRecord">
            <AppIcon v-if="recordSaving" name="refresh" :size="15" />
            保存并生成新批次
          </button>
        </template>
      </AppModal>
    </template>
  </div>
</template>

<style scoped>
.grading-page {
  max-width: 1260px;
}

.grading-summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1px;
  margin-bottom: 16px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--line);
}

.grading-summary > div {
  padding: 13px 16px;
  background: #fff;
}

.grading-summary span {
  display: block;
  color: var(--ink-3);
  font-size: 9px;
}

.grading-summary b {
  display: block;
  margin-top: 2px;
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 22px;
  font-weight: 600;
  line-height: 1.2;
}

.grading-summary b small {
  color: var(--ink-3);
  font-family: var(--font-body);
  font-size: 8.5px;
  font-weight: 400;
}

.grading-summary > div > small {
  display: block;
  margin-top: 3px;
  color: var(--ink-4);
  font-size: 8.5px;
}

.grading-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 16px;
  align-items: start;
}

.grading-main {
  min-width: 0;
}

.grading-tabs {
  margin-bottom: 12px;
}

.tab-count {
  margin-left: 5px;
  color: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 9px;
}

.import-card,
.records-card {
  padding: 22px;
}

.source-badge {
  color: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.08em;
}

.success-banner {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 14px;
  padding: 10px 12px;
  border: 1px solid #d5ebdf;
  border-radius: 9px;
  color: var(--mint-deep);
  background: #f1fbf6;
}

.success-banner .icon {
  flex: none;
  margin-top: 2px;
}

.success-banner b {
  display: block;
  font-size: 10.5px;
}

.success-banner span {
  display: block;
  margin-top: 1px;
  color: #6f9a8b;
  font-size: 9px;
}

.import-steps {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 9px;
  margin-bottom: 14px;
}

.import-step {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: var(--surface-soft);
}

.import-step > span {
  color: var(--role);
  font-family: var(--font-numbers);
  font-size: 12px;
  font-weight: 600;
}

.import-step b {
  display: block;
  color: var(--ink-2);
  font-size: 9.5px;
}

.import-step p {
  margin-top: 2px;
  color: var(--ink-4);
  font-size: 8.5px;
  line-height: 1.6;
}

.dropzone-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 150px;
  gap: 10px;
  align-items: stretch;
}

.dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 152px;
  padding: 16px;
  border: 1.5px dashed color-mix(in srgb, var(--role) 32%, var(--line));
  border-radius: 12px;
  background:
    radial-gradient(circle at 50% 25%, color-mix(in srgb, var(--role) 7%, transparent), transparent 54%),
    #fdfefd;
  text-align: center;
  transition: 0.2s;
}

.dropzone:hover:not(:disabled) {
  border-color: var(--role);
  background: var(--role-wash);
}

.dropzone:disabled {
  opacity: 0.55;
}

.dropzone-icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  margin-bottom: 7px;
  border-radius: 12px;
  color: var(--role-deep);
  background: var(--role-soft);
}

.dropzone b {
  color: var(--ink);
  font-size: 11px;
  font-weight: 600;
}

.dropzone span {
  margin-top: 3px;
  color: var(--ink-3);
  font-size: 9px;
}

.dropzone em {
  margin-top: 6px;
  color: var(--ink-4);
  font-size: 8px;
  font-style: normal;
}

.import-tools {
  display: flex;
  flex-direction: column;
  gap: 6px;
  justify-content: center;
}

.preview-block {
  margin-top: 16px;
  padding-top: 15px;
  border-top: 1px dashed var(--line);
}

.preview-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.preview-head h4 {
  font-size: 12px;
  font-weight: 700;
}

.preview-head p {
  max-width: 58ch;
  margin-top: 2px;
  color: var(--ink-3);
  font-size: 9px;
  line-height: 1.6;
}

.preview-counts {
  display: flex;
  gap: 10px;
  flex: none;
  color: var(--ink-3);
  font-size: 8.5px;
}

.preview-counts b {
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 13px;
  font-weight: 600;
}

.preview-counts .has-error b {
  color: var(--coral-deep);
}

.error-list {
  margin-bottom: 10px;
  padding: 9px 11px;
  border: 1px solid #f0dcd8;
  border-radius: 9px;
  background: var(--coral-soft);
}

.error-row {
  display: grid;
  grid-template-columns: 50px 58px minmax(0, 1fr);
  gap: 6px;
  padding: 4px 0;
  font-size: 9px;
}

.error-row > span {
  color: #b57b73;
  font-family: var(--font-mono);
  font-size: 8px;
}

.error-row > b {
  color: #9b5b53;
}

.error-row > p {
  color: #a66d66;
  line-height: 1.6;
}

.more-errors {
  margin-top: 4px;
  color: #b57b73;
  font-size: 8.5px;
}

.preview-table :deep(th),
.preview-table :deep(td) {
  padding: 7px 9px;
  font-size: 9.5px;
}

.preview-table :deep(.badge) {
  min-height: 18px;
  padding: 0 6px;
  font-size: 8px;
}

.preview-more {
  margin-top: 5px;
  color: var(--ink-4);
  font-size: 8.5px;
}

.preview-actions {
  display: flex;
  justify-content: flex-end;
  gap: 7px;
  margin-top: 12px;
}

.audit-note {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  margin-top: 12px;
  padding: 16px 18px;
  background: var(--surface-soft);
  box-shadow: none;
}

.audit-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex: none;
  border-radius: 9px;
  color: var(--role-deep);
  background: var(--role-soft);
}

.audit-note h4 {
  color: var(--ink-2);
  font-size: 10.5px;
}

.audit-note p {
  margin-top: 2px;
  color: var(--ink-3);
  font-size: 9px;
  line-height: 1.75;
}

/* 核查 */
.record-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
  margin-bottom: 12px;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 210px;
  height: 33px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--ink-3);
  background: #fdfefd;
}

.search-box input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  font-size: 10px;
}

.filter-select {
  width: auto;
  height: 33px;
  min-width: 108px;
  padding: 0 30px 0 10px;
  font-size: 10px;
}

.records-table :deep(td) {
  font-size: 10px;
}

.student-cell {
  display: flex;
  align-items: center;
  gap: 7px;
}

.student-cell b {
  display: block;
  color: var(--ink);
  font-size: 10px;
  font-weight: 600;
}

.student-cell span {
  display: block;
  color: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 8px;
}

.records-table :deep(.badge) {
  min-height: 18px;
  padding: 0 6px;
  font-size: 8px;
}

.note-cell {
  max-width: 130px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pagination {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--line);
  color: var(--ink-4);
  font-size: 8.5px;
}

.pagination > span {
  grid-column: 2;
  text-align: center;
}

.pagination > div {
  display: flex;
  grid-column: 3;
  justify-self: end;
  gap: 5px;
}

.history-chip {
  margin-left: 5px;
  padding: 2px 7px;
  border-radius: 999px;
  color: var(--apricot-deep);
  background: var(--apricot-soft);
  font-size: 8px;
  font-weight: 500;
}

/* 侧栏 */
.batch-side {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.batch-card,
.batch-help,
.top-wrong-card {
  padding: 17px;
}

.batch-total {
  color: var(--ink-4);
  font-family: var(--font-numbers);
  font-size: 11px;
}

.batch-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.batch-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 7px;
  border: 1px solid transparent;
  border-radius: 9px;
  text-align: left;
  transition: 0.18s;
}

.batch-item:hover {
  background: var(--surface-soft);
}

.batch-item.is-selected {
  border-color: var(--line);
  background: #fdfefd;
}

.batch-item.is-current {
  border-color: color-mix(in srgb, var(--role) 22%, var(--line));
  background: var(--role-wash);
}

.batch-version {
  display: grid;
  place-items: center;
  width: 29px;
  height: 29px;
  flex: none;
  border-radius: 8px;
  color: var(--ink-3);
  background: #f2f6f4;
  font-family: var(--font-numbers);
  font-size: 10px;
}

.batch-item.is-current .batch-version {
  color: var(--role-deep);
  background: var(--role-soft);
}

.batch-copy {
  min-width: 0;
  flex: 1;
}

.batch-copy b {
  display: block;
  overflow: hidden;
  color: var(--ink-2);
  font-size: 9.5px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.batch-copy span {
  display: block;
  margin-top: 1px;
  color: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 7.5px;
}

.batch-current {
  flex: none;
  padding: 1px 5px;
  border-radius: 4px;
  color: var(--role-deep);
  background: color-mix(in srgb, var(--role) 9%, white);
  font-size: 7px;
}

.batch-arrow {
  color: var(--ink-4);
}

.batch-help {
  background: linear-gradient(145deg, #fffdf9, #fdf8ef);
  border-color: #f0e4d2;
}

.batch-help__icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  margin-bottom: 7px;
  border-radius: 9px;
  color: var(--apricot-deep);
  background: rgba(255, 255, 255, 0.7);
}

.batch-help h4 {
  color: #8c673c;
  font-size: 10.5px;
}

.batch-help p {
  margin-top: 3px;
  color: #a38663;
  font-size: 8.5px;
  line-height: 1.75;
}

.top-wrong-card .section-title {
  font-size: 12px;
  margin-bottom: 7px;
}

.top-wrong-row {
  display: flex;
  align-items: center;
  gap: 7px;
  width: 100%;
  padding: 5px 0;
  color: var(--ink-2);
  font-size: 9px;
}

.top-wrong-row > span {
  width: 46px;
  flex: none;
  text-align: left;
}

.top-wrong-bar {
  height: 5px;
  flex: 1;
  overflow: hidden;
  border-radius: 99px;
  background: #f1f5f3;
}

.top-wrong-bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #efb6a5, var(--coral));
}

.top-wrong-row b {
  width: 38px;
  color: var(--coral-deep);
  font-family: var(--font-numbers);
  font-size: 10px;
  font-weight: 600;
  text-align: right;
}

.record-edit {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.record-edit__head {
  display: flex;
  align-items: center;
  gap: 9px;
  padding-bottom: 11px;
  border-bottom: 1px dashed var(--line);
}

.record-edit__head > div {
  min-width: 0;
  flex: 1;
}

.record-edit__head b {
  display: block;
  color: var(--ink);
  font-size: 11.5px;
}

.record-edit__head span:not(.avatar):not(.tag) {
  display: block;
  margin-top: 2px;
  color: var(--ink-4);
  font-size: 8.5px;
}

.standard-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 8px;
  color: var(--ink-3);
  background: var(--surface-soft);
  font-size: 9px;
}

.standard-line b {
  color: var(--ink-2);
}

.standard-divider {
  width: 1px;
  height: 12px;
  background: var(--line);
}

.truth-options {
  display: flex;
  gap: 8px;
}

.truth-options label {
  flex: 1;
  padding: 8px;
  border: 1px solid var(--line);
  border-radius: 9px;
  color: var(--ink-2);
  font-size: 11px;
  text-align: center;
  cursor: pointer;
}

.truth-options label.is-active {
  border-color: var(--role);
  color: var(--role-deep);
  background: var(--role-soft);
}

.truth-options input {
  position: absolute;
  opacity: 0;
}

@media (max-width: 1020px) {
  .grading-layout {
    grid-template-columns: 1fr;
  }

  .batch-side {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }

  .batch-card {
    grid-row: span 2;
  }

  .dropzone-row {
    grid-template-columns: 1fr;
  }

  .import-tools {
    flex-direction: row;
    flex-wrap: wrap;
  }
}

@media (max-width: 720px) {
  .grading-summary {
    grid-template-columns: 1fr 1fr;
  }

  .import-steps {
    grid-template-columns: 1fr;
  }

  .import-card,
  .records-card {
    padding: 17px;
  }

  .batch-side {
    display: flex;
  }

  .record-filters {
    align-items: stretch;
  }

  .search-box {
    width: 100%;
  }

  .filter-select {
    flex: 1;
  }

  .preview-head {
    flex-direction: column;
  }

  .error-row {
    grid-template-columns: 46px 52px minmax(0, 1fr);
  }
}
</style>
