<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { teacherApi } from '@/api/teacher'
import { errorMessage } from '@/api/errors'
import type { HomeworkStatus, HomeworkSummary } from '@/types/models'
import { useTeacherWorkspace } from '@/stores/teacherWorkspace'
import { useUiStore } from '@/stores/ui'
import { formatDateTime, toIsoDate } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import AppIcon from '@/components/AppIcon.vue'
import AppEmpty from '@/components/AppEmpty.vue'
import AppModal from '@/components/AppModal.vue'
import AppPagination from '@/components/AppPagination.vue'
import { fetchAllPages } from '@/utils/pagination'

const PAGE_SIZE = 10

const router = useRouter()
const route = useRoute()
const workspace = useTeacherWorkspace()
const ui = useUiStore()

const homeworks = ref<HomeworkSummary[]>([])
const loading = ref(true)
const search = ref('')
const filter = ref<'ALL' | HomeworkStatus>('ALL')
const listPage = ref(1)
const truncated = ref(false)
const createOpen = ref(false)
const creating = ref(false)
const formError = ref('')
const form = reactive({
  classId: 0,
  title: '',
  dueDate: '',
  description: '',
})

const tabs = computed(() => [
  { value: 'ALL' as const, label: '全部', count: homeworks.value.length },
  {
    value: 'DRAFT' as const,
    label: '草稿',
    count: homeworks.value.filter((item) => item.status === 'DRAFT').length,
  },
  {
    value: 'PUBLISHED' as const,
    label: '已发布',
    count: homeworks.value.filter((item) => item.status === 'PUBLISHED').length,
  },
  {
    value: 'ARCHIVED' as const,
    label: '已归档',
    count: homeworks.value.filter((item) => item.status === 'ARCHIVED').length,
  },
])

const filtered = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return homeworks.value
    .filter((homework) => filter.value === 'ALL' || homework.status === filter.value)
    .filter(
      (homework) =>
        !keyword ||
        homework.title.toLowerCase().includes(keyword) ||
        homework.className.toLowerCase().includes(keyword),
    )
})
const paged = computed(() => {
  const start = (listPage.value - 1) * PAGE_SIZE
  return filtered.value.slice(start, start + PAGE_SIZE)
})

const publishedCount = computed(
  () => homeworks.value.filter((homework) => homework.status === 'PUBLISHED').length,
)

const pendingReviewCount = computed(
  () =>
    homeworks.value.filter(
      (homework) =>
        homework.handoutStatus === 'UNPUBLISHED' || homework.handoutStatus === 'WITHDRAWN',
    ).length,
)

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await fetchAllPages((page, pageSize) =>
      teacherApi.listHomeworks({ page, pageSize }),
    )
    homeworks.value = result.items
    truncated.value = result.truncated
    listPage.value = 1
    workspace.loaded = false
    await workspace.load(true)
  } catch (cause) {
    ui.toast('error', '作业列表加载失败', errorMessage(cause))
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  formError.value = ''
  form.title = ''
  form.dueDate = ''
  form.description = ''
  form.classId = workspace.classes[0]?.id ?? 0
  createOpen.value = true
}

async function createHomework(): Promise<void> {
  formError.value = ''
  if (!form.classId) {
    formError.value = '请选择班级。'
    return
  }
  if (!form.title.trim()) {
    formError.value = '请填写作业名称。'
    return
  }
  if (form.title.trim().length > 128) {
    formError.value = '作业名称最多 128 字。'
    return
  }
  creating.value = true
  try {
    const homework = await teacherApi.createHomework({
      classId: form.classId,
      title: form.title.trim(),
      description: form.description.trim() || null,
      dueAt: form.dueDate ? toIsoDate(form.dueDate) : null,
    })
    ui.toast('success', '作业草稿已创建', '接下来添加题目，发布后即可导入批改。')
    createOpen.value = false
    await workspace.load(true)
    await router.push(`/teacher/homeworks/${homework.id}`)
  } catch (cause) {
    formError.value = errorMessage(cause)
  } finally {
    creating.value = false
  }
}

function openHomework(homework: HomeworkSummary): void {
  workspace.selectHomework(homework.id)
  void router.push(`/teacher/homeworks/${homework.id}`)
}

function goGrading(homework: HomeworkSummary): void {
  workspace.selectHomework(homework.id)
  void router.push(`/teacher/homeworks/${homework.id}/grading`)
}

function goReview(homework: HomeworkSummary): void {
  workspace.selectHomework(homework.id)
  void router.push(`/teacher/homeworks/${homework.id}/review`)
}

async function archive(homework: HomeworkSummary): Promise<void> {
  const accepted = await ui.confirm({
    title: '归档这份作业？',
    message: `《${homework.title}》归档后不再出现在进行中列表。历史批改、讲评和订正仍会保留。`,
    confirmText: '确认归档',
    tone: 'danger',
  })
  if (!accepted) return
  try {
    await teacherApi.archiveHomework(homework.id, homework.version, '教师手动归档')
    ui.toast('success', '作业已归档', '历史数据仍可追溯，已发布讲评状态不受影响。')
    await load()
  } catch (cause) {
    ui.toast('error', '归档失败', errorMessage(cause))
  }
}

function dueLabel(homework: HomeworkSummary): string {
  if (!homework.dueAt) return '未设置截止时间'
  return formatDateTime(homework.dueAt)
}

watch(
  () => route.query.create,
  (value) => {
    if (value === '1') {
      openCreate()
      void router.replace({ path: route.path })
    }
  },
)

watch([search, filter], () => {
  listPage.value = 1
})

onMounted(async () => {
  await workspace.load()
  await load()
  if (route.query.create === '1') {
    openCreate()
    void router.replace({ path: route.path })
  }
})
</script>

<template>
  <div class="homework-list-page">
    <PageHeader
      eyebrow="TEACHER · ASSIGNMENTS"
      title="作业与批改"
      subtitle="一份作业从草稿到学生订正，只走一条可追溯的链路：发布作业 → 导入批改 → 审核讲评 → 发布 → 跟踪订正。"
    >
      <template #actions>
        <button class="btn btn--ghost btn--sm" :disabled="loading" @click="load">
          <AppIcon name="refresh" :size="14" />
          刷新
        </button>
        <button class="btn btn--primary btn--sm" @click="openCreate">
          <AppIcon name="plus" :size="15" />
          创建作业
        </button>
      </template>
    </PageHeader>

    <div class="library-strip">
      <div class="library-item">
        <span class="library-item__icon library-item__icon--mint"><AppIcon name="clipboard" :size="17" /></span>
        <div>
          <b>{{ homeworks.length }}</b>
          <span>全部作业</span>
        </div>
      </div>
      <div class="library-item">
        <span class="library-item__icon library-item__icon--peri"><AppIcon name="check-circle" :size="17" /></span>
        <div>
          <b>{{ publishedCount }}</b>
          <span>已发布作业</span>
        </div>
      </div>
      <div class="library-item">
        <span class="library-item__icon library-item__icon--apricot"><AppIcon name="sparkles" :size="17" /></span>
        <div>
          <b>{{ pendingReviewCount }}</b>
          <span>讲评待发布</span>
        </div>
      </div>
      <div class="library-note">
        <AppIcon name="shield" :size="15" />
        <span>跨班作业在这里不可见；批改只追加版本，不覆盖历史。</span>
      </div>
    </div>

    <div class="list-toolbar">
      <div class="segmented" role="tablist" aria-label="筛选作业状态">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          type="button"
          role="tab"
          :aria-selected="filter === tab.value"
          :class="{ 'is-active': filter === tab.value }"
          @click="filter = tab.value"
        >
          {{ tab.label }}
          <span class="count">{{ tab.count }}</span>
        </button>
      </div>
      <label class="search-box">
        <AppIcon name="search" :size="16" />
        <input v-model="search" type="search" placeholder="搜索作业名称或班级…" />
      </label>
    </div>

    <div v-if="loading" class="card loading-card">
      <span class="skeleton" style="width: 220px; height: 10px" />
    </div>

    <div v-else-if="filtered.length" class="homework-ledger">
      <article
        v-for="(homework, index) in paged"
        :key="homework.id"
        class="hw-row"
        :class="{ 'is-active': workspace.activeHomeworkId === homework.id }"
      >
        <button class="hw-row__hit" type="button" :aria-label="`打开《${homework.title}》`" @click="openHomework(homework)" />
        <span class="hw-index">{{ String((listPage - 1) * PAGE_SIZE + index + 1).padStart(2, '0') }}</span>
        <div class="hw-main">
          <div class="hw-titleline">
            <h3>{{ homework.title }}</h3>
            <StatusBadge :status="homework.status" />
            <span v-if="workspace.activeHomeworkId === homework.id" class="active-mark">当前</span>
          </div>
          <div class="hw-meta">
            <span><AppIcon name="users" :size="13" />{{ homework.className }}</span>
            <span><AppIcon name="book" :size="13" />{{ homework.questionCount }} 道题</span>
            <span><AppIcon name="calendar" :size="13" />{{ dueLabel(homework) }}</span>
          </div>
          <div class="hw-tags">
            <span class="tag" :class="{ 'tag--muted': !homework.currentGradingBatchVersion }">
              <AppIcon name="upload" :size="11" />
              {{ homework.currentGradingBatchVersion ? `批改 V${homework.currentGradingBatchVersion}` : '批改待导入' }}
            </span>
            <span v-if="homework.handoutId" class="tag">
              <AppIcon name="sparkles" :size="11" />
              讲评
              <StatusBadge v-if="homework.handoutStatus" :status="homework.handoutStatus" plain />
            </span>
            <span v-else class="tag tag--muted">
              <AppIcon name="sparkles" :size="11" />
              尚未生成讲评
            </span>
            <span class="tag tag--muted">更新于 {{ formatDateTime(homework.updatedAt) }}</span>
          </div>
        </div>
        <div class="hw-actions">
          <button class="btn btn--ghost btn--sm" type="button" @click="openHomework(homework)">
            详情
          </button>
          <button
            v-if="homework.status === 'PUBLISHED'"
            class="btn btn--soft btn--sm"
            type="button"
            @click="goGrading(homework)"
          >
            <AppIcon name="chart" :size="14" />
            批改
          </button>
          <button
            v-if="homework.status === 'PUBLISHED'"
            class="btn btn--primary btn--sm"
            type="button"
            @click="goReview(homework)"
          >
            {{ homework.handoutStatus === 'PUBLISHED' ? '查看讲评' : '审核讲评' }}
            <AppIcon name="arrow-right" :size="14" />
          </button>
          <button
            v-if="homework.status === 'PUBLISHED'"
            class="icon-btn hw-archive"
            type="button"
            title="归档作业"
            @click="archive(homework)"
          >
            <AppIcon name="inbox" :size="16" />
          </button>
        </div>
      </article>
      <AppPagination v-model:page="listPage" :page-size="PAGE_SIZE" :total="filtered.length" />
    </div>

    <section v-else class="card">
      <AppEmpty
        icon="clipboard"
        title="没有符合条件的作业"
        description="清空搜索条件，或从右上角创建一份新的数学作业。"
      >
        <button v-if="!homeworks.length" class="btn btn--primary btn--sm" @click="openCreate">
          创建第一份作业
        </button>
        <button v-else class="btn btn--ghost btn--sm" @click="search = ''; filter = 'ALL'">
          清除筛选
        </button>
      </AppEmpty>
    </section>

    <div class="list-footnote">
      <AppIcon name="info" :size="14" />
      <span>
        批改数据导入后会生成新的完整批次快照，统计随当前批次自动重算；已发布讲评的条目需先撤回才能编辑。
        <template v-if="truncated">当前作业量超过加载上限，仅展示前 2000 份。</template>
      </span>
    </div>

    <AppModal
      v-model="createOpen"
      title="创建作业草稿"
      subtitle="先登记作业，再逐题录入或导入题目；草稿状态不会出现在学生端。"
      size="md"
    >
      <div class="form-grid">
        <div class="field field--full">
          <label for="hw-title">作业名称</label>
          <input id="hw-title" v-model="form.title" class="input" maxlength="128" placeholder="例：一元一次方程 · 第 5 课时" />
          <span class="counter">{{ form.title.length }} / 128</span>
        </div>
        <div class="field">
          <label for="hw-class">班级</label>
          <select id="hw-class" v-model="form.classId" class="select">
            <option v-for="classInfo in workspace.classes" :key="classInfo.id" :value="classInfo.id">
              {{ classInfo.name }} · {{ classInfo.studentCount }} 人
            </option>
          </select>
          <span class="field-hint">只显示本人负责的班级。</span>
        </div>
        <div class="field">
          <label for="hw-due">截止日期</label>
          <input id="hw-due" v-model="form.dueDate" class="input" type="date" />
          <span class="field-hint">可留空，之后在详情中补充。</span>
        </div>
        <div class="field field--full">
          <label for="hw-desc">作业说明</label>
          <textarea
            id="hw-desc"
            v-model="form.description"
            class="textarea"
            maxlength="5000"
            placeholder="例：完成课本第 44 页练习，重点练习去括号与移项。"
          />
          <span class="counter">{{ form.description.length }} / 5000</span>
        </div>
      </div>
      <div v-if="formError" class="error-banner" style="margin-top: 14px">
        <AppIcon name="alert-circle" :size="15" />
        {{ formError }}
      </div>
      <div class="info-banner" style="margin-top: 14px">
        <AppIcon name="info" :size="15" />
        第一版仅支持单选题与判断题；标准答案和教师解析是 AI 生成讲评的必要依据。
      </div>
      <template #footer>
        <button class="btn btn--ghost" type="button" @click="createOpen = false">取消</button>
        <button class="btn btn--primary" type="button" :disabled="creating" @click="createHomework">
          <AppIcon v-if="creating" name="refresh" :size="15" />
          创建并编辑题目
        </button>
      </template>
    </AppModal>
  </div>
</template>

<style scoped>
.homework-list-page {
  max-width: 1240px;
}

.library-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(150px, 0.75fr)) minmax(230px, 1.6fr);
  gap: 1px;
  margin-bottom: 18px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 17px;
  background: var(--line);
}

.library-item {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 13px 16px;
  background: rgba(255, 255, 255, 0.94);
}

.library-item__icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  flex: none;
  border-radius: 10px;
}

.library-item__icon--mint {
  color: var(--mint-deep);
  background: var(--mint-soft);
}

.library-item__icon--peri {
  color: var(--peri-deep);
  background: var(--peri-soft);
}

.library-item__icon--apricot {
  color: var(--apricot-deep);
  background: var(--apricot-soft);
}

.library-item b {
  display: block;
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 19px;
  font-weight: 600;
  line-height: 1;
}

.library-item span {
  display: block;
  margin-top: 3px;
  color: var(--ink-3);
  font-size: 9px;
}

.library-note {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 13px 17px;
  color: var(--ink-3);
  background: var(--surface-soft);
  font-size: 10px;
  line-height: 1.65;
}

.library-note .icon {
  flex: none;
  color: var(--role);
  opacity: 0.7;
}

.list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.search-box {
  position: relative;
  display: flex;
  align-items: center;
  gap: 7px;
  width: 250px;
  height: 36px;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.8);
  color: var(--ink-3);
}

.search-box:focus-within {
  border-color: color-mix(in srgb, var(--role) 40%, var(--line));
  background: #fff;
}

.search-box input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  font-size: 11.5px;
}

.search-box input::placeholder {
  color: var(--ink-4);
}

.homework-ledger {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.hw-row {
  position: relative;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 17px 18px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 17px;
  background: #fff;
  box-shadow: var(--shadow-xs);
  transition: transform 0.24s var(--ease-out), border-color 0.2s, box-shadow 0.24s;
}

.hw-row::before {
  content: '';
  position: absolute;
  top: 16px;
  bottom: 16px;
  left: 0;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: var(--role);
  opacity: 0;
  transition: opacity 0.2s;
}

.hw-row:hover {
  transform: translateY(-1px);
  border-color: color-mix(in srgb, var(--role) 25%, var(--line));
  box-shadow: var(--shadow-sm);
}

.hw-row:hover::before,
.hw-row.is-active::before {
  opacity: 0.7;
}

.hw-row.is-active {
  border-color: color-mix(in srgb, var(--role) 28%, var(--line));
  background: linear-gradient(90deg, color-mix(in srgb, var(--role) 4%, #fff), #fff 22%);
}

.hw-row__hit {
  position: absolute;
  inset: 0;
  z-index: 0;
  cursor: pointer;
}

.hw-index {
  position: relative;
  z-index: 1;
  color: var(--ink-4);
  font-family: var(--font-numbers);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  pointer-events: none;
}

.hw-main {
  min-width: 0;
  flex: 1;
  pointer-events: none;
}

.hw-titleline {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
}

.hw-titleline h3 {
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.005em;
}

.active-mark {
  padding: 1px 6px;
  border-radius: 4px;
  color: var(--role-deep);
  background: var(--role-soft);
  font-size: 8px;
  font-weight: 700;
}

.hw-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px 14px;
  margin-top: 5px;
  color: var(--ink-3);
  font-size: 10px;
}

.hw-meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.hw-meta .icon {
  opacity: 0.7;
}

.hw-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 8px;
}

.hw-tags .tag {
  gap: 4px;
  font-size: 9px;
}

.hw-tags .tag--muted {
  color: var(--ink-4);
  background: #f7faf8;
}

.hw-tags :deep(.badge) {
  min-height: 16px;
  padding: 0;
  border: 0;
  background: transparent;
  font-size: 8px;
}

.hw-tags :deep(.badge::before) {
  display: none;
}

.hw-actions {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 6px;
  flex: none;
}

.hw-actions .btn {
  font-size: 10px;
}

.hw-archive {
  width: 30px;
  height: 30px;
  margin-left: 1px;
}

.hw-archive:hover {
  color: var(--apricot-deep);
  background: var(--apricot-soft);
}

.list-footnote {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin-top: 14px;
  padding: 0 4px;
  color: var(--ink-4);
  font-size: 9.5px;
  line-height: 1.7;
}

.list-footnote .icon {
  flex: none;
  margin-top: 1px;
}

@media (max-width: 1000px) {
  .library-strip {
    grid-template-columns: repeat(3, minmax(130px, 1fr));
  }

  .library-note {
    grid-column: 1 / -1;
  }

  .hw-row {
    flex-wrap: wrap;
  }

  .hw-actions {
    width: 100%;
    padding-left: 28px;
  }
}

@media (max-width: 680px) {
  .library-strip {
    grid-template-columns: 1fr 1fr;
  }

  .library-item:nth-child(3) {
    grid-column: 1 / -1;
  }

  .library-note {
    display: none;
  }

  .list-toolbar {
    align-items: stretch;
  }

  .search-box {
    width: 100%;
  }

  .hw-row {
    gap: 10px;
    padding: 15px 14px;
  }

  .hw-index {
    display: none;
  }

  .hw-titleline h3 {
    font-size: 15px;
  }

  .hw-actions {
    padding-left: 0;
    flex-wrap: wrap;
  }

  .hw-actions .btn--primary {
    flex: 1;
  }

  .hw-tags .tag--muted:last-child {
    display: none;
  }
}
</style>
