<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { teacherApi } from '@/api/teacher'
import { ApiError, errorMessage } from '@/api/errors'
import type {
  HandoutDetail,
  HandoutItemTeacherView,
  HandoutItemVersion,
  HandoutLog,
  HomeworkDetail,
  HomeworkStatistics,
  PublicTemplate,
} from '@/types/models'
import { useTeacherWorkspace } from '@/stores/teacherWorkspace'
import { useUiStore } from '@/stores/ui'
import { EVIDENCE_LABEL, QUESTION_TYPE } from '@/types/labels'
import { formatDateTime, formatRelative } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
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
const handout = ref<HandoutDetail | null>(null)
const loading = ref(true)
const pageError = ref('')
const actionError = ref('')
const actionBusy = ref('')
const filter = ref<'ALL' | 'TODO' | 'READY' | 'EXCLUDED'>('ALL')
const selectedItemId = ref<number | null>(null)
const editorContent = ref('')
const evidenceOpen = ref(true)

const excludeOpen = ref(false)
const excludeReason = ref('')
const excludeTarget = ref<HandoutItemTeacherView | null>(null)
const publishOpen = ref(false)
const publishRemark = ref('')
const logsOpen = ref(false)
const logsLoading = ref(false)
const studentPreviewOpen = ref(false)
const logs = ref<HandoutLog[]>([])
const versionsOpen = ref(false)
const versionsLoading = ref(false)
const versionsError = ref('')
const versions = ref<HandoutItemVersion[]>([])
const approvedTemplates = ref<PublicTemplate[]>([])
const selectedTemplateId = ref(0)

const selectedItem = computed(
  () => handout.value?.items.find((item) => item.id === selectedItemId.value) ?? null,
)
const filteredItems = computed(() => {
  const items = [...(handout.value?.items ?? [])].sort((a, b) => a.sortNo - b.sortNo)
  switch (filter.value) {
    case 'TODO':
      return items.filter((item) =>
        ['DRAFT', 'PENDING_REVIEW', 'AI_FAILED_MANUAL', 'INSUFFICIENT_EVIDENCE'].includes(item.status),
      )
    case 'READY':
      return items.filter((item) => item.status === 'READY')
    case 'EXCLUDED':
      return items.filter((item) => item.status === 'EXCLUDED')
    default:
      return items
  }
})
const readyRatio = computed(() => {
  if (!handout.value?.itemCount) return 0
  return Math.round((handout.value.readyCount / handout.value.itemCount) * 100)
})
const dirty = computed(() => {
  if (!selectedItem.value) return false
  return editorContent.value.trim() !== (selectedItem.value.content ?? '').trim()
})
const isPublished = computed(() => handout.value?.status === 'PUBLISHED')
const canEdit = computed(() => Boolean(handout.value && handout.value.status !== 'PUBLISHED'))
const publishLabel = computed(() => (handout.value?.status === 'WITHDRAWN' ? '重新发布讲评' : '发布讲评'))
const gateLabel = computed(() => {
  if (!handout.value) return '未生成'
  if (handout.value.status === 'PUBLISHED') return '已发布'
  if (handout.value.status === 'WITHDRAWN') return '已撤回'
  return handout.value.publishable ? '可发布' : '未达到发布条件'
})
const filterCounts = computed(() => {
  const items = handout.value?.items ?? []
  return {
    ALL: items.length,
    TODO: items.filter((item) =>
      ['DRAFT', 'PENDING_REVIEW', 'AI_FAILED_MANUAL', 'INSUFFICIENT_EVIDENCE'].includes(item.status),
    ).length,
    READY: items.filter((item) => item.status === 'READY').length,
    EXCLUDED: items.filter((item) => item.status === 'EXCLUDED').length,
  }
})
const filterOptions = computed(() => [
  { value: 'ALL' as const, label: '全部', count: filterCounts.value.ALL },
  { value: 'TODO' as const, label: '待处理', count: filterCounts.value.TODO },
  { value: 'READY' as const, label: '已就绪', count: filterCounts.value.READY },
  { value: 'EXCLUDED' as const, label: '已剔除', count: filterCounts.value.EXCLUDED },
])

async function load(preferredItemId?: number | null): Promise<void> {
  loading.value = true
  pageError.value = ''
  actionError.value = ''
  try {
    const detail = await teacherApi.getHomework(homeworkId.value)
    homework.value = detail
    workspace.selectHomework(detail.id)
    workspace.loaded = false
    await workspace.load(true)
    if (detail.currentGradingBatchId) {
      try {
        statistics.value = await teacherApi.getStatistics(detail.id)
      } catch {
        statistics.value = null
      }
    } else {
      statistics.value = null
    }
    try {
      approvedTemplates.value = await teacherApi.listApprovedTemplates()
    } catch {
      approvedTemplates.value = []
    }
    if (detail.handoutId) {
      try {
        handout.value = await teacherApi.getHandout(detail.id)
      } catch (cause) {
        if (cause instanceof ApiError && cause.status === 404) {
          handout.value = null
        } else {
          throw cause
        }
      }
    } else {
      handout.value = null
    }
    pickDefaultItem(preferredItemId ?? selectedItemId.value)
  } catch (cause) {
    pageError.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

function pickDefaultItem(preferred?: number | null): void {
  const items = handout.value?.items ?? []
  if (!items.length) {
    selectedItemId.value = null
    editorContent.value = ''
    return
  }
  const preferredItem = items.find((item) => item.id === preferred)
  const firstTodo = items.find((item) =>
    ['AI_FAILED_MANUAL', 'INSUFFICIENT_EVIDENCE', 'DRAFT', 'PENDING_REVIEW'].includes(item.status),
  )
  const target = preferredItem ?? firstTodo ?? items[0]
  selectItem(target)
}

function selectItem(item: HandoutItemTeacherView): void {
  selectedItemId.value = item.id
  editorContent.value = item.content ?? ''
  actionError.value = ''
  evidenceOpen.value = true
}

async function generateHandout(): Promise<void> {
  if (!homework.value) return
  actionBusy.value = 'generate'
  actionError.value = ''
  try {
    handout.value = await teacherApi.generateHandout(homework.value.id, {
      templateId: selectedTemplateId.value || null,
    })
    ui.toast('success', '讲评草稿已生成', 'AI 只提供候选，仍需你逐条确认后才会进入学生可见范围。')
    await load()
    pickDefaultItem()
  } catch (cause) {
    actionError.value = errorMessage(cause)
    ui.toast('error', '生成讲评失败', errorMessage(cause))
  } finally {
    actionBusy.value = ''
  }
}

async function confirmItem(): Promise<void> {
  if (!selectedItem.value) return
  const content = editorContent.value.trim()
  if (!content) {
    actionError.value = '讲评正文不能为空；若本题不需要讲评，请选择「剔除」。'
    return
  }
  actionBusy.value = 'confirm'
  actionError.value = ''
  try {
    const updated = await teacherApi.confirmHandoutItem(selectedItem.value.id, {
      content,
      expectedVersionId: selectedItem.value.currentVersionId,
      changeNote: dirty.value ? '教师修改讲评正文' : '教师确认讲评草稿',
    })
    ui.toast(
      'success',
      `第 ${updated.seqNo} 题已确认`,
      '条目现已进入发布就绪状态，整份讲评发布后学生才可见。',
    )
    await load(updated.id)
  } catch (cause) {
    actionError.value = errorMessage(cause)
    ui.toast('error', '确认失败', errorMessage(cause))
  } finally {
    actionBusy.value = ''
  }
}

function openExclude(item: HandoutItemTeacherView): void {
  excludeTarget.value = item
  excludeReason.value = item.excludeReason ?? ''
  excludeOpen.value = true
}

async function excludeItem(): Promise<void> {
  if (!excludeTarget.value) return
  if (!excludeReason.value.trim()) {
    actionError.value = '请填写剔除原因。'
    return
  }
  actionBusy.value = 'exclude'
  actionError.value = ''
  try {
    await teacherApi.excludeHandoutItem(
      excludeTarget.value.id,
      excludeReason.value.trim(),
      excludeTarget.value.currentVersionId,
    )
    ui.toast('info', '条目已剔除', '被剔除的条目不会出现在学生的已发布讲评中。')
    excludeOpen.value = false
    await load(excludeTarget.value.id)
  } catch (cause) {
    actionError.value = errorMessage(cause)
  } finally {
    actionBusy.value = ''
  }
}

async function saveDraft(): Promise<void> {
  if (!selectedItem.value) return
  const content = editorContent.value.trim()
  if (!content) {
    actionError.value = '讲评正文不能为空；若本题不需要讲评，请选择「剔除」。'
    return
  }
  actionBusy.value = 'save'
  actionError.value = ''
  try {
    const updated = await teacherApi.saveHandoutItem(selectedItem.value.id, {
      content,
      expectedVersionId: selectedItem.value.currentVersionId,
      changeNote: dirty.value ? '教师保存讲评草稿' : '保存讲评草稿',
    })
    ui.toast('success', '草稿已保存', '条目回到待审核；确认后才进入发布就绪。')
    await load(updated.id)
  } catch (cause) {
    actionError.value = errorMessage(cause)
    ui.toast('error', '保存失败', errorMessage(cause))
  } finally {
    actionBusy.value = ''
  }
}

async function openVersions(): Promise<void> {
  if (!selectedItem.value) return
  versionsOpen.value = true
  versionsLoading.value = true
  versionsError.value = ''
  try {
    versions.value = await teacherApi.listHandoutItemVersions(selectedItem.value.id)
  } catch (cause) {
    versionsError.value = errorMessage(cause)
    versions.value = []
  } finally {
    versionsLoading.value = false
  }
}

function useVersion(version: HandoutItemVersion): void {
  editorContent.value = version.content
  versionsOpen.value = false
  ui.toast('info', `已载入版本 V${version.versionNo}`, '当前修改尚未保存；保存草稿或确认后才会生效。')
}

async function regenerateItem(): Promise<void> {
  if (!selectedItem.value) return
  const accepted = await ui.confirm({
    title: '重新生成这条讲评？',
    message: '旧版本会保留在版本记录中，新草稿仍需你再次确认。若 AI 服务不可用，将转为「手工填写」。',
    confirmText: '重新生成',
  })
  if (!accepted) return
  actionBusy.value = 'regenerate'
  actionError.value = ''
  try {
    const updated = await teacherApi.regenerateHandoutItem(
      selectedItem.value.id,
      selectedItem.value.currentVersionId,
    )
    if (updated.status === 'AI_FAILED_MANUAL') {
      ui.toast('error', 'AI 调用失败', '条目已标记为手工填写，可保留原内容或直接补写。')
    } else if (updated.status === 'INSUFFICIENT_EVIDENCE') {
      ui.toast('info', '依据不足', '该题缺少标准答案或教师解析，不能生成确定性讲解。')
    } else {
      ui.toast('success', '已生成新的讲评草稿', '请核对措辞与依据后再次确认。')
    }
    await load(updated.id)
  } catch (cause) {
    actionError.value = errorMessage(cause)
  } finally {
    actionBusy.value = ''
  }
}

function openPublish(): void {
  if (!handout.value) return
  publishOpen.value = true
  publishRemark.value = ''
}

async function publishHandout(): Promise<void> {
  if (!handout.value) return
  actionBusy.value = 'publish'
  actionError.value = ''
  try {
    handout.value = await teacherApi.publishHandout(
      handout.value.id,
      handout.value.version,
      publishRemark.value.trim() || '教师发布讲评',
    )
    ui.toast('success', '讲评已发布', `${handout.value.readyCount} 条发布就绪内容现已对学生可见。`)
    publishOpen.value = false
    await load(selectedItemId.value)
  } catch (cause) {
    actionError.value = errorMessage(cause)
    ui.toast('error', '发布失败', errorMessage(cause))
  } finally {
    actionBusy.value = ''
  }
}

async function withdrawHandout(): Promise<void> {
  if (!handout.value) return
  const accepted = await ui.confirm({
    title: '撤回已发布讲评？',
    message: '撤回后学生端立即不可见；条目会回到待审核，重新发布前需要逐条重新确认。已提交的订正与疑问都会保留。',
    confirmText: '撤回讲评',
    tone: 'danger',
  })
  if (!accepted) return
  actionBusy.value = 'withdraw'
  try {
    handout.value = await teacherApi.withdrawHandout(
      handout.value.id,
      handout.value.version,
      '教师撤回讲评',
    )
    ui.toast('info', '讲评已撤回', '条目已回到待审核；重新确认后才能再次发布，订正与疑问未删除。')
    await load(selectedItemId.value)
  } catch (cause) {
    ui.toast('error', '撤回失败', errorMessage(cause))
  } finally {
    actionBusy.value = ''
  }
}

async function openLogs(): Promise<void> {
  if (!handout.value) return
  logsOpen.value = true
  logsLoading.value = true
  try {
    const page = await teacherApi.getHandoutLogs(handout.value.id, { page: 1, pageSize: 50 })
    logs.value = page.items
  } catch (cause) {
    ui.toast('error', '发布记录加载失败', errorMessage(cause))
    logs.value = []
  } finally {
    logsLoading.value = false
  }
}

function actionLabel(action: HandoutLog['action']): string {
  if (action === 'PUBLISH') return '发布'
  if (action === 'WITHDRAW') return '撤回'
  return '重新发布'
}

function goGrading(): void {
  if (!homework.value) return
  void router.push(`/teacher/homeworks/${homework.value.id}/grading`)
}

onMounted(load)
</script>

<template>
  <div class="review-page">
    <div v-if="loading" class="card loading-card">
      <span class="skeleton" style="width: 220px; height: 10px" />
    </div>

    <div v-else-if="pageError" class="card">
      <AppEmpty icon="alert-circle" title="无法加载讲评审核台" :description="pageError">
        <button class="btn btn--ghost btn--sm" @click="router.push('/teacher/homeworks')">返回作业列表</button>
      </AppEmpty>
    </div>

    <template v-else-if="homework">
      <PageHeader
        eyebrow="TEACHER · REVIEW DESK"
        title="讲评审核台"
        :subtitle="`《${homework.title}》 · ${homework.className}。AI 只生成候选草稿，逐条由你确认；发布权只属于教师。`"
      >
        <template #actions>
          <button v-if="handout" class="btn btn--ghost btn--sm" @click="openLogs">
            <AppIcon name="list" :size="14" />
            发布记录
          </button>
          <button
            v-if="handout && handout.status === 'PUBLISHED'"
            class="btn btn--danger btn--sm"
            :disabled="actionBusy !== ''"
            @click="withdrawHandout"
          >
            <AppIcon name="rotate" :size="14" />
            撤回讲评
          </button>
          <button
            v-else-if="handout && handout.publishable"
            class="btn btn--primary btn--sm"
            :disabled="actionBusy !== ''"
            @click="openPublish"
          >
            <AppIcon name="send" :size="14" />
            {{ publishLabel }}
          </button>
        </template>
      </PageHeader>

      <template v-if="handout">
        <section class="gate-console">
          <div class="gate-state">
            <span class="gate-icon" :class="{ 'is-live': handout.status === 'PUBLISHED' }">
              <AppIcon :name="handout.status === 'PUBLISHED' ? 'check-double' : 'shield'" :size="20" />
            </span>
            <div>
              <div class="gate-topline">
                <StatusBadge :status="handout.status" />
                <span class="gate-label">{{ gateLabel }}</span>
                <span v-if="handout.status === 'PUBLISHED'" class="gate-live">学生端可见中</span>
              </div>
              <h2 v-if="handout.status === 'PUBLISHED'">
                {{ handout.readyCount }} 条讲评正在向本班学生展示
              </h2>
              <h2 v-else-if="handout.status === 'WITHDRAWN'">讲评已撤回，请重新确认要再次发布的内容</h2>
              <h2 v-else-if="handout.publishable">已有确认内容，可以发布</h2>
              <h2 v-else>至少确认 1 条讲评后才能发布</h2>
              <p v-if="handout.status === 'PUBLISHED'">
                发布于 {{ formatDateTime(handout.publishedAt) }}。学生只能看到 READY 条目、自己的作答和本人疑问；未处理条目不会展示，如需继续处理请先撤回。
              </p>
              <p v-else-if="handout.status === 'WITHDRAWN'">
                撤回后条目已回到待审核；重新发布前需要逐条重新确认。历史订正与疑问仍保留。
              </p>
              <p v-else-if="handout.publishable">
                发布后，仅 {{ handout.readyCount }} 条已确认内容会出现在学生端；待审核、依据不足或 AI 失败条目不会展示，也不会阻断本次发布。
              </p>
              <p v-else>{{ handout.blockingReasons.join(' · ') }}。已剔除和未处理条目不展示。</p>
            </div>
          </div>

          <div class="gate-metrics">
            <div>
              <span>就绪</span>
              <b>{{ handout.readyCount }}</b>
            </div>
            <div>
              <span>待审核</span>
              <b class="warm">{{ handout.pendingReviewCount }}</b>
            </div>
            <div>
              <span>未处理</span>
              <b class="danger">{{ handout.blockedCount }}</b>
            </div>
            <div>
              <span>剔除</span>
              <b>{{ handout.excludedCount }}</b>
            </div>
          </div>

          <div class="gate-progress">
            <div class="row row--between">
              <span>整份讲评就绪度</span>
              <b>{{ readyRatio }}%</b>
            </div>
            <div class="progress"><span :style="{ width: `${readyRatio}%` }" /></div>
            <div class="gate-actions">
              <button
                v-if="handout.status !== 'PUBLISHED'"
                class="btn btn--primary btn--sm"
                :disabled="!handout.publishable || actionBusy !== ''"
                @click="openPublish"
              >
                <AppIcon name="send" :size="14" />
                {{ publishLabel }}
              </button>
              <button
                v-else
                class="btn btn--ghost btn--sm"
                type="button"
                @click="openLogs"
              >
                <AppIcon name="clock" :size="14" />
                查看发布历史
              </button>
              <span v-if="handout.status !== 'PUBLISHED'" class="gate-hint">
                发布 = 全量发布 READY 条目
              </span>
            </div>
          </div>
        </section>

        <div class="review-layout">
          <aside class="item-rail card">
            <div class="rail-head">
              <div>
                <h3 class="section-title">待审条目</h3>
                <p class="section-desc">按题号排序，点击开始核对。</p>
              </div>
              <span class="rail-count">{{ handout.itemCount }}</span>
            </div>
            <div class="rail-filters">
              <button
                v-for="option in filterOptions"
                :key="option.value"
                type="button"
                :class="{ 'is-active': filter === option.value }"
                @click="filter = option.value"
              >
                {{ option.label }} <span>{{ option.count }}</span>
              </button>
            </div>

            <div class="item-list">
              <button
                v-for="item in filteredItems"
                :key="item.id"
                type="button"
                class="item-chip"
                :class="{ 'is-active': selectedItemId === item.id }"
                @click="selectItem(item)"
              >
                <span class="item-seq">{{ String(item.seqNo).padStart(2, '0') }}</span>
                <div class="item-copy">
                  <div class="item-topline">
                    <b>第 {{ item.seqNo }} 题</b>
                    <StatusBadge :status="item.status" plain />
                  </div>
                  <p>{{ item.stem }}</p>
                  <div class="item-meta">
                    <span>{{ QUESTION_TYPE[item.questionType].label }}</span>
                    <span v-if="item.wrongCount !== undefined">{{ item.wrongCount }} 人答错</span>
                    <span v-if="item.source === 'AI'">AI 草稿</span>
                    <span v-else-if="item.source === 'MANUAL'">手工</span>
                    <span v-else>无 AI 草稿</span>
                  </div>
                </div>
              </button>
              <AppEmpty
                v-if="!filteredItems.length"
                icon="check-circle"
                title="此筛选下没有条目"
                description="切换筛选查看其他讲评条目。"
              />
            </div>
          </aside>

          <section v-if="selectedItem" class="review-desk">
            <div class="desk-head">
              <div class="desk-title">
                <span class="desk-seq">{{ String(selectedItem.seqNo).padStart(2, '0') }}</span>
                <div>
                  <div class="desk-tags">
                    <b>第 {{ selectedItem.seqNo }} 题</b>
                    <StatusBadge :status="selectedItem.status" />
                    <span class="tag">{{ QUESTION_TYPE[selectedItem.questionType].label }}</span>
                    <span class="tag">{{ selectedItem.score }} 分</span>
                    <span class="tag tag--mono">版本 {{ selectedItem.versionCount }}</span>
                  </div>
                  <p>
                    {{
                      isPublished
                        ? '讲评已发布，内容已锁定；如需修改请先撤回。'
                        : selectedItem.status === 'READY'
                          ? '本条已就绪，仍可在发布前修改并重新确认。'
                          : 'AI 草稿仅作候选，请核对依据后确认。'
                    }}
                  </p>
                </div>
              </div>
              <div class="desk-actions">
                <button
                  v-if="selectedItem.versionCount > 0"
                  class="btn btn--ghost btn--sm"
                  type="button"
                  @click="openVersions"
                >
                  <AppIcon name="clock" :size="14" />
                  版本 {{ selectedItem.versionCount }}
                </button>
                <button
                  v-if="canEdit"
                  class="btn btn--ghost btn--sm"
                  type="button"
                  :disabled="actionBusy !== ''"
                  @click="regenerateItem"
                >
                  <AppIcon name="refresh" :size="14" />
                  重新生成
                </button>
                <button
                  v-if="canEdit && selectedItem.status !== 'EXCLUDED'"
                  class="btn btn--quiet btn--sm"
                  type="button"
                  :disabled="actionBusy !== ''"
                  @click="openExclude(selectedItem)"
                >
                  <AppIcon name="minus" :size="14" />
                  剔除
                </button>
              </div>
            </div>

            <div class="desk-grid">
              <div class="desk-primary">
                <section class="source-card">
                  <div class="source-card__head">
                    <span class="source-label">原题与依据</span>
                    <span class="source-ref">Q{{ selectedItem.questionId }}</span>
                  </div>
                  <p class="source-stem">{{ selectedItem.stem }}</p>
                  <div v-if="selectedItem.options?.length" class="source-options">
                    <span
                      v-for="option in selectedItem.options"
                      :key="option.key"
                      :class="{ 'is-correct': option.key === selectedItem.standardAnswer }"
                    >
                      <b>{{ option.key }}</b>{{ option.text }}
                    </span>
                  </div>
                  <div class="source-answer">
                    <span>标准答案</span>
                    <b>
                      {{
                        selectedItem.standardAnswer === 'TRUE'
                          ? '正确'
                          : selectedItem.standardAnswer === 'FALSE'
                            ? '错误'
                            : selectedItem.standardAnswer || '未提供'
                      }}
                    </b>
                  </div>
                  <div v-if="selectedItem.teacherAnalysis" class="source-analysis">
                    <span>教师解析</span>
                    <p>{{ selectedItem.teacherAnalysis }}</p>
                  </div>
                  <div v-else class="source-missing">
                    <AppIcon name="alert-triangle" :size="14" />
                    缺少教师解析；若同时无标准答案，AI 只能标记「依据不足」。
                  </div>
                </section>

                <section class="evidence-card">
                  <button class="evidence-toggle" type="button" @click="evidenceOpen = !evidenceOpen">
                    <span>
                      <AppIcon name="layers" :size="15" />
                      依据快照
                      <em>{{ selectedItem.evidence.length }} 条</em>
                    </span>
                    <span class="evidence-right">
                      <span v-if="selectedItem.source === 'AI'" class="ai-small">
                        <AppIcon name="sparkles" :size="11" />AI 草稿
                      </span>
                      <AppIcon :name="evidenceOpen ? 'chevron-up' : 'chevron-down'" :size="14" />
                    </span>
                  </button>
                  <Transition name="fade">
                    <div v-if="evidenceOpen" class="evidence-body">
                      <div v-if="selectedItem.evidence.length" class="evidence-list">
                        <div v-for="(evidence, index) in selectedItem.evidence" :key="index" class="evidence-item">
                          <span class="evidence-index">{{ String(index + 1).padStart(2, '0') }}</span>
                          <div>
                            <b>{{ EVIDENCE_LABEL[evidence.evidenceType] || evidence.evidenceType }}</b>
                            <p>{{ evidence.snapshotText }}</p>
                          </div>
                        </div>
                      </div>
                      <div v-else class="evidence-empty">
                        还没有可用依据。请补充标准答案/教师解析后重新生成，或手工填写讲评。
                      </div>
                      <p class="evidence-note">
                        依据快照不含学生姓名、学号等个人信息；生成后不随题目后续修改自动覆盖。
                      </p>
                    </div>
                  </Transition>
                </section>
              </div>

              <div class="desk-editor">
                <div class="proof-paper" :class="{ 'is-readonly': !canEdit }">
                  <div class="proof-margin" aria-hidden="true" />
                  <div class="proof-head">
                    <div>
                      <span class="proof-label">讲评正文</span>
                      <span class="proof-hint">面向学生展示的最终讲解</span>
                    </div>
                    <div class="proof-status">
                      <span v-if="dirty" class="dirty-badge">未保存修改</span>
                      <span v-else-if="selectedItem.status === 'READY'" class="ready-badge">
                        <AppIcon name="check" :size="11" />已确认
                      </span>
                      <span v-else class="draft-badge">待确认</span>
                    </div>
                  </div>

                  <div v-if="isPublished" class="published-content">
                    {{ selectedItem.content || '（无正文）' }}
                  </div>
                  <textarea
                    v-else
                    v-model="editorContent"
                    class="proof-textarea"
                    maxlength="2000"
                    placeholder="写下你确认后的讲评。请使用「可能 / 常见」描述错误原因，避免能力或态度评价。"
                    :disabled="!canEdit"
                  />
                  <div class="proof-foot">
                    <span>{{ editorContent.length }} / 2000</span>
                    <span v-if="dirty">修改后需重新确认</span>
                    <span v-else-if="selectedItem.confirmedAt">确认于 {{ formatRelative(selectedItem.confirmedAt) }}</span>
                    <span v-else>未确认</span>
                  </div>
                  <div v-if="isPublished" class="publish-stamp">已发布</div>
                </div>

                <div v-if="selectedItem.status === 'EXCLUDED'" class="excluded-note">
                  <AppIcon name="minus" :size="14" />
                  <div>
                    <b>本条已剔除</b>
                    <span>{{ selectedItem.excludeReason || '教师判断本次不需要讲评。' }}</span>
                  </div>
                </div>
                <div v-else-if="selectedItem.status === 'INSUFFICIENT_EVIDENCE'" class="evidence-warning">
                  <AppIcon name="alert-triangle" :size="15" />
                  <div>
                    <b>依据不足，不能自动生成确定讲解</b>
                    <span>可手工补写后确认，或剔除该条；不会阻塞其他条目的发布。</span>
                  </div>
                </div>
                <div v-else-if="selectedItem.status === 'AI_FAILED_MANUAL'" class="evidence-warning">
                  <AppIcon name="cloud-off" :size="15" />
                  <div>
                    <b>AI 调用失败，转为手工填写</b>
                    <span>可保留已有正文直接确认，或重新生成；AI 不可用不会阻塞整条业务链路。</span>
                  </div>
                </div>

                <div v-if="actionError" class="error-banner">
                  <AppIcon name="alert-circle" :size="15" />
                  {{ actionError }}
                </div>

                <div v-if="canEdit" class="editor-actions">
                  <button class="btn btn--ghost btn--sm" type="button" :disabled="actionBusy !== ''" @click="studentPreviewOpen = true">
                    <AppIcon name="eye" :size="14" />
                    预览学生视图
                  </button>
                  <button
                    class="btn btn--ghost btn--sm"
                    type="button"
                    :disabled="actionBusy !== '' || !editorContent.trim()"
                    @click="saveDraft"
                  >
                    <AppIcon v-if="actionBusy === 'save'" name="refresh" :size="14" />
                    <AppIcon v-else name="save" :size="14" />
                    保存草稿
                  </button>
                  <button
                    class="btn btn--primary"
                    type="button"
                    :disabled="actionBusy !== '' || !editorContent.trim()"
                    @click="confirmItem"
                  >
                    <AppIcon v-if="actionBusy === 'confirm'" name="refresh" :size="15" />
                    <AppIcon v-else name="check-double" :size="15" />
                    {{ selectedItem.status === 'READY' ? '更新并确认' : '确认本条' }}
                  </button>
                </div>
                <div v-else class="editor-actions">
                  <button class="btn btn--ghost btn--sm" type="button" @click="openLogs">
                    <AppIcon name="clock" :size="14" />
                    发布记录
                  </button>
                  <button class="btn btn--danger btn--sm" type="button" :disabled="actionBusy !== ''" @click="withdrawHandout">
                    <AppIcon name="rotate" :size="14" />
                    撤回后可编辑
                  </button>
                </div>
              </div>
            </div>
          </section>
          <AppEmpty
            v-else
            icon="clipboard"
            title="选择一条讲评开始审核"
            description="左侧条目覆盖草稿、待审核、就绪、剔除、依据不足与 AI 失败等状态。"
          />
        </div>
      </template>

      <section v-else class="generate-stage">
        <div class="generate-card">
          <div class="generate-copy">
            <span class="eyebrow">AI ASSISTED · TEACHER REVIEW</span>
            <h2>还没有这份作业的讲评</h2>
            <p>
              基于当前批改批次，系统会把每道题的题目、标准答案、教师解析、统计结果作为依据快照，
              生成候选草稿。没有依据的题目会被明确标记，而不是编造讲解。
            </p>
            <div class="prerequisite-list">
              <div :class="{ 'is-done': homework.status === 'PUBLISHED' }">
                <span><AppIcon :name="homework.status === 'PUBLISHED' ? 'check' : 'x'" :size="12" /></span>
                <div>
                  <b>作业已发布</b>
                  <p>{{ homework.status === 'PUBLISHED' ? '学生与批改数据归属已确定' : '请先在作业详情发布作业' }}</p>
                </div>
              </div>
              <div :class="{ 'is-done': Boolean(homework.currentGradingBatchId) }">
                <span><AppIcon :name="homework.currentGradingBatchId ? 'check' : 'x'" :size="12" /></span>
                <div>
                  <b>已导入批改批次</b>
                  <p>{{ homework.currentGradingBatchVersion ? `当前批次 V${homework.currentGradingBatchVersion}` : '尚无批改事实，无法计算错误分布' }}</p>
                </div>
              </div>
              <div :class="{ 'is-done': Boolean(statistics) }">
                <span><AppIcon :name="statistics ? 'check' : 'x'" :size="12" /></span>
                <div>
                  <b>统计已计算</b>
                  <p>{{ statistics ? `识别 ${statistics.questions.length} 道题 · ${statistics.knowledgePoints.length} 个知识点` : '导入批改后由后端自动重算' }}</p>
                </div>
              </div>
            </div>
            <div v-if="approvedTemplates.length" class="template-picker">
              <label for="handout-template">引用公共模板（可选）</label>
              <select id="handout-template" v-model="selectedTemplateId" class="select">
                <option :value="0">不引用模板</option>
                <option v-for="template in approvedTemplates" :key="template.id" :value="template.id">
                  {{ template.title }}
                </option>
              </select>
            </div>
            <div class="generate-actions">
              <button
                class="btn btn--primary btn--lg"
                type="button"
                :disabled="actionBusy !== '' || !homework.currentGradingBatchId"
                @click="generateHandout"
              >
                <AppIcon v-if="actionBusy === 'generate'" name="refresh" :size="16" />
                <AppIcon v-else name="sparkles" :size="16" />
                生成讲评草稿
              </button>
              <button class="btn btn--ghost" type="button" @click="goGrading">查看批改数据</button>
            </div>
            <div v-if="actionError" class="error-banner" style="margin-top: 12px">
              <AppIcon name="alert-circle" :size="15" />
              {{ actionError }}
            </div>
          </div>

          <div class="generate-art">
            <div class="orbit orbit--one" />
            <div class="orbit orbit--two" />
            <div class="ai-core">
              <span><AppIcon name="sparkles" :size="28" /></span>
              <b>AI</b>
              <small>DRAFT ONLY</small>
            </div>
            <div class="floating-evidence ev--one">
              <span>原题</span>
              <p>2(x−3)=x+1</p>
            </div>
            <div class="floating-evidence ev--two">
              <span>标准答案</span>
              <p>A. x = 7</p>
            </div>
            <div class="floating-evidence ev--three">
              <span>统计快照</span>
              <p>28 / 45 答错</p>
            </div>
            <div class="teacher-seal">
              <AppIcon name="shield" :size="13" />
              教师确认后才可见
            </div>
          </div>
        </div>

        <div class="generate-footnote">
          <AppIcon name="shield" :size="14" />
          AI 不修改分数、不修改批改事实、不直接发布；失败与依据不足均有手工承接路径。
        </div>
      </section>
    </template>

    <AppModal
      v-model="excludeOpen"
      title="剔除这条讲评"
      subtitle="剔除后不会展示给学生，也不会阻塞发布；原因会写入操作日志。"
      size="sm"
    >
      <div v-if="excludeTarget" class="exclude-form">
        <div class="exclude-target">
          <span class="tag">第 {{ excludeTarget.seqNo }} 题</span>
          <p>{{ excludeTarget.stem }}</p>
        </div>
        <div class="field">
          <label for="exclude-reason">剔除原因</label>
          <textarea
            id="exclude-reason"
            v-model="excludeReason"
            class="textarea"
            maxlength="200"
            placeholder="例：该题全班正确率较高，本次课堂不安排讲评。"
          />
          <span class="counter">{{ excludeReason.length }} / 200</span>
        </div>
        <div v-if="actionError" class="error-banner" style="margin-top: 10px">
          <AppIcon name="alert-circle" :size="15" />
          {{ actionError }}
        </div>
      </div>
      <template #footer>
        <button class="btn btn--ghost" type="button" @click="excludeOpen = false">取消</button>
        <button class="btn btn--danger" type="button" :disabled="actionBusy !== ''" @click="excludeItem">
          确认剔除
        </button>
      </template>
    </AppModal>

    <AppModal
      v-model="versionsOpen"
      title="讲评版本记录"
      subtitle="旧版本永久保留；载入后仍需保存草稿或确认才会生效。"
      size="md"
    >
      <div v-if="versionsLoading" class="loading-card"><span class="skeleton" style="width: 160px; height: 8px" /></div>
      <div v-else-if="versionsError" class="error-banner">
        <AppIcon name="alert-circle" :size="15" />
        {{ versionsError }}
      </div>
      <div v-else-if="versions.length" class="version-list">
        <article v-for="version in versions" :key="version.id" class="version-row">
          <header>
            <span class="tag tag--mono">V{{ version.versionNo }}</span>
            <span class="version-type">
              {{ version.changeType === 'EXCLUDE' ? '剔除' : version.changeType === 'REGENERATE' ? '重新生成' : '编辑' }}
            </span>
            <time>{{ formatDateTime(version.createdAt) }}</time>
          </header>
          <p>{{ version.content || '（无正文）' }}</p>
          <footer>
            <span>{{ version.changeNote || '无备注' }}</span>
            <button
              v-if="canEdit"
              class="btn btn--ghost btn--xs"
              type="button"
              @click="useVersion(version)"
            >
              载入此版本
            </button>
          </footer>
        </article>
      </div>
      <AppEmpty
        v-else
        icon="clock"
        title="还没有版本记录"
        description="确认、保存或重新生成后，旧版本会保留在这里。"
      />
    </AppModal>

    <AppModal
      v-model="publishOpen"
      :title="publishLabel"
      subtitle="发布对象仅限本班学生，且只展示 READY 条目"
      size="sm"
    >
      <div v-if="handout" class="publish-modal">
        <div class="publish-count">
          <span class="publish-count__num">{{ handout.readyCount }}</span>
          <div>
            <b>条讲评将对本班学生可见</b>
            <span>约 {{ homework?.studentCount ?? 0 }} 名学生 · 只含自己的错题</span>
          </div>
        </div>
        <div class="publish-gates">
          <span><AppIcon name="check" :size="12" />至少 1 条发布就绪</span>
          <span><AppIcon name="check" :size="12" />教师确认已完成</span>
          <span><AppIcon name="check" :size="12" />未处理条目不展示</span>
        </div>
        <div v-if="handout.pendingReviewCount + handout.blockedCount > 0" class="info-banner">
          <AppIcon name="alert-triangle" :size="15" />
          还有 {{ handout.pendingReviewCount + handout.blockedCount }} 条未处理，本次不会展示；发布后如需继续处理，请先撤回讲评。
        </div>
        <div class="field">
          <label for="publish-remark">发布备注（可选）</label>
          <input
            id="publish-remark"
            v-model="publishRemark"
            class="input"
            maxlength="200"
            placeholder="会写入讲评发布记录"
          />
        </div>
        <div class="info-banner">
          <AppIcon name="info" :size="15" />
          发布后学生只能看到自己的错题和已确认的讲解，看不到其他学生、AI 草稿或排名。
        </div>
      </div>
      <template #footer>
        <button class="btn btn--ghost" type="button" @click="publishOpen = false">再检查一下</button>
        <button class="btn btn--primary" type="button" :disabled="actionBusy !== ''" @click="publishHandout">
          <AppIcon name="send" :size="15" />
          确认发布
        </button>
      </template>
    </AppModal>

    <AppModal v-model="logsOpen" title="讲评发布记录" subtitle="追加写入，不可修改" size="md">
      <div v-if="logsLoading" class="loading-card"><span class="skeleton" style="width: 160px; height: 8px" /></div>
      <div v-else-if="logs.length" class="log-list">
        <div v-for="log in logs" :key="log.id" class="log-row">
          <span class="log-icon" :class="`log-icon--${log.action.toLowerCase()}`">
            <AppIcon :name="log.action === 'WITHDRAW' ? 'rotate' : 'send'" :size="14" />
          </span>
          <div class="log-copy">
            <b>{{ actionLabel(log.action) }} · {{ log.operatorName || `教师 #${log.operatorId}` }}</b>
            <span>{{ formatDateTime(log.createdAt) }} · {{ log.itemCount ?? 0 }} 条可见 · {{ log.remark || '无备注' }}</span>
          </div>
          <StatusBadge v-if="log.toStatus" :status="log.toStatus" plain />
        </div>
      </div>
      <AppEmpty v-else icon="clock" title="还没有发布记录" description="首次发布后，这里会按时间记录发布、撤回和重新发布。" />
    </AppModal>

    <AppModal
      v-model="studentPreviewOpen"
      title="学生视图预览"
      subtitle="仅预览讲评版式，不包含任何学生个人信息"
      size="md"
    >
      <div v-if="selectedItem" class="student-preview">
        <div class="preview-browser">
          <div class="preview-browser__bar">
            <span /><span /><span />
            <em>我的错题讲解</em>
          </div>
          <div class="preview-body">
            <div class="preview-kicker">老师已发布 · 第 {{ selectedItem.seqNo }} 题</div>
            <h3>{{ selectedItem.stem }}</h3>
            <div v-if="selectedItem.options?.length" class="preview-options">
              <span
                v-for="option in selectedItem.options"
                :key="option.key"
                :class="{ 'is-correct': option.key === selectedItem.standardAnswer }"
              >
                <b>{{ option.key }}</b>{{ option.text }}
              </span>
            </div>
            <div class="preview-commentary">
              <span>讲解</span>
              <p>{{ editorContent || selectedItem.content || '（暂无正文）' }}</p>
            </div>
            <p class="preview-boundary">
              学生只能看到本人错题与老师确认过的讲解；看不到 AI 草稿、依据快照、其他学生或排名。
            </p>
          </div>
        </div>
      </div>
      <template #footer>
        <button class="btn btn--primary" type="button" @click="studentPreviewOpen = false">完成预览</button>
      </template>
    </AppModal>
  </div>
</template>


<style scoped>
.review-page {
  max-width: 1320px;
}

/* 发布控制台 */
.gate-console {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) auto 220px;
  gap: 22px;
  align-items: center;
  margin-bottom: 16px;
  padding: 18px 22px;
  border: 1px solid #dcece6;
  border-radius: 18px;
  background:
    radial-gradient(circle at 100% 0, rgba(255, 255, 255, 0.9), transparent 40%),
    linear-gradient(120deg, #fbfefd, #eff9f5 68%, #f3f5fe);
  box-shadow: 0 14px 38px -38px rgba(34, 92, 74, 0.36);
}

.gate-state {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.gate-icon {
  display: grid;
  place-items: center;
  width: 39px;
  height: 39px;
  flex: none;
  border-radius: 12px;
  color: var(--mint-deep);
  background: rgba(255, 255, 255, 0.75);
  box-shadow: 0 4px 12px -8px rgba(34, 92, 74, 0.4);
}

.gate-icon.is-live {
  color: #fff;
  background: var(--mint);
}

.gate-topline {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.gate-label {
  color: var(--ink-3);
  font-size: 8.5px;
  letter-spacing: 0.04em;
}

.gate-live {
  padding: 1px 6px;
  border-radius: 99px;
  color: var(--mint-deep);
  background: var(--mint-soft);
  font-size: 8px;
}

.gate-state h2 {
  margin-top: 5px;
  font-family: var(--font-serif);
  font-size: 17px;
  font-weight: 600;
  line-height: 1.4;
}

.gate-state p {
  max-width: 66ch;
  margin-top: 3px;
  color: var(--ink-3);
  font-size: 9.5px;
  line-height: 1.65;
}

.gate-metrics {
  display: flex;
  gap: 18px;
  padding: 0 6px;
}

.gate-metrics div {
  text-align: center;
}

.gate-metrics span {
  display: block;
  color: var(--ink-3);
  font-size: 8px;
}

.gate-metrics b {
  display: block;
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 20px;
  font-weight: 600;
  line-height: 1.2;
}

.gate-metrics b.warm {
  color: var(--apricot-deep);
}

.gate-metrics b.danger {
  color: var(--coral-deep);
}

.gate-progress {
  padding-left: 18px;
  border-left: 1px dashed rgba(92, 147, 129, 0.25);
}

.gate-progress .row {
  margin-bottom: 5px;
  color: var(--ink-3);
  font-size: 8.5px;
}

.gate-progress .row b {
  color: var(--ink-2);
  font-family: var(--font-numbers);
  font-size: 12px;
  font-weight: 600;
}

.gate-actions {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 4px;
  margin-top: 9px;
}

.gate-hint {
  color: var(--ink-4);
  font-size: 7.5px;
  text-align: center;
}

/* 主布局 */
.review-layout {
  display: grid;
  grid-template-columns: 292px minmax(0, 1fr);
  gap: 14px;
  align-items: stretch;
}

.item-rail {
  position: sticky;
  top: 82px;
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 100px);
  padding: 16px;
  overflow: hidden;
}

.rail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  flex: none;
}

.rail-head .section-title {
  font-size: 13.5px;
}

.rail-head .section-desc {
  font-size: 9px;
}

.rail-count {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  color: var(--role-deep);
  background: var(--role-soft);
  font-family: var(--font-numbers);
  font-size: 12px;
}

.rail-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
  margin: 11px 0 8px;
  padding: 3px;
  border-radius: 9px;
  background: var(--surface-soft);
  flex: none;
}

.rail-filters button {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 4px 7px;
  border-radius: 6px;
  color: var(--ink-3);
  font-size: 8px;
  transition: 0.18s;
}

.rail-filters button span {
  font-family: var(--font-mono);
  font-size: 7px;
  opacity: 0.7;
}

.rail-filters button.is-active {
  color: var(--role-deep);
  background: #fff;
  box-shadow: 0 1px 3px rgba(40, 80, 66, 0.08);
}

.item-list {
  display: flex;
  flex-direction: column;
  gap: 3px;
  overflow-y: auto;
  padding-right: 2px;
}

.item-chip {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
  padding: 9px 8px;
  border: 1px solid transparent;
  border-radius: 9px;
  text-align: left;
  transition: 0.18s;
}

.item-chip:hover {
  background: #fbfdfc;
}

.item-chip.is-active {
  border-color: color-mix(in srgb, var(--role) 24%, var(--line));
  background: var(--role-wash);
}

.item-seq {
  display: grid;
  place-items: center;
  width: 23px;
  height: 23px;
  flex: none;
  border-radius: 6px;
  color: var(--ink-3);
  background: #f3f7f5;
  font-family: var(--font-numbers);
  font-size: 9px;
}

.item-chip.is-active .item-seq {
  color: var(--role-deep);
  background: var(--role-soft);
}

.item-copy {
  min-width: 0;
  flex: 1;
}

.item-topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 5px;
}

.item-topline > b {
  color: var(--ink-2);
  font-size: 9.5px;
  font-weight: 600;
}

.item-topline :deep(.badge) {
  min-height: 16px;
  padding: 0;
  font-size: 7.5px;
}

.item-topline :deep(.badge::before) {
  display: none;
}

.item-copy > p {
  display: -webkit-box;
  margin-top: 2px;
  overflow: hidden;
  color: var(--ink-3);
  font-size: 8.5px;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.item-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 7px;
  margin-top: 3px;
  color: var(--ink-4);
  font-size: 7.5px;
}

/* 审核台 */
.review-desk {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.desk-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 10px;
  padding: 0 2px;
}

.desk-title {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.desk-seq {
  color: var(--ink-4);
  font-family: var(--font-numbers);
  font-size: 28px;
  font-weight: 500;
  line-height: 1;
}

.desk-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
}

.desk-tags > b {
  color: var(--ink);
  font-size: 12.5px;
  font-weight: 700;
}

.desk-tags .tag {
  padding: 1px 5px;
  font-size: 7.5px;
}

.desk-tags :deep(.badge) {
  min-height: 18px;
  padding: 0 6px;
  font-size: 8px;
}

.desk-title p {
  margin-top: 3px;
  color: var(--ink-3);
  font-size: 9px;
}

.desk-actions {
  display: flex;
  gap: 5px;
  flex: none;
}

.desk-grid {
  display: grid;
  flex: 1;
  grid-template-columns: minmax(240px, 0.8fr) minmax(0, 1.2fr);
  gap: 12px;
  align-items: stretch;
}

.desk-primary {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.source-card {
  padding: 15px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: #fff;
  box-shadow: var(--shadow-xs);
}

.source-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--ink-3);
}

.source-label {
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.source-ref {
  color: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 7.5px;
}

.source-stem {
  margin-top: 9px;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 11px;
  font-weight: 600;
  line-height: 1.85;
  white-space: pre-wrap;
}

.source-options {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 8px;
}

.source-options span {
  padding: 4px 7px;
  border-radius: 5px;
  color: var(--ink-3);
  background: var(--surface-soft);
  font-size: 8.5px;
  line-height: 1.5;
}

.source-options span b {
  margin-right: 5px;
  font-family: var(--font-mono);
  font-size: 7.5px;
}

.source-options span.is-correct {
  color: var(--mint-deep);
  background: var(--mint-soft);
}

.source-answer {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 9px;
  padding-top: 8px;
  border-top: 1px dashed var(--line);
  font-size: 8.5px;
}

.source-answer span {
  color: var(--ink-4);
}

.source-answer b {
  color: var(--mint-deep);
  font-family: var(--font-serif);
  font-size: 10px;
}

.source-analysis {
  margin-top: 8px;
  padding: 7px 9px;
  border-left: 2px solid #c9e3d9;
  border-radius: 0 5px 5px 0;
  background: #f9fcfa;
}

.source-analysis span {
  color: var(--ink-4);
  font-size: 7.5px;
}

.source-analysis p {
  margin-top: 2px;
  color: var(--ink-2);
  font-size: 8.5px;
  line-height: 1.7;
}

.source-missing {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  margin-top: 8px;
  padding: 7px 8px;
  border-radius: 5px;
  color: #987344;
  background: #fdf9f1;
  font-size: 8px;
  line-height: 1.6;
}

.source-missing .icon {
  flex: none;
  margin-top: 1px;
}

/* 依据 */
.evidence-card {
  display: flex;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: #fff;
  box-shadow: var(--shadow-xs);
}

.evidence-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 10px 12px;
  color: var(--ink-2);
  font-size: 9px;
  font-weight: 600;
}

.evidence-toggle > span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.evidence-toggle em {
  color: var(--ink-4);
  font-family: var(--font-numbers);
  font-size: 8px;
  font-style: normal;
}

.evidence-right {
  color: var(--ink-4);
}

.ai-small {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 1px 5px;
  border-radius: 4px;
  color: var(--lav-deep);
  background: var(--lav-soft);
  font-size: 7px;
  font-weight: 500;
}

.evidence-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 0 12px 11px;
  overflow: auto;
}

.evidence-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-top: 7px;
  border-top: 1px dashed var(--line);
}

.evidence-item {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 6px 0;
}

.evidence-index {
  width: 15px;
  flex: none;
  color: var(--ink-4);
  font-family: var(--font-numbers);
  font-size: 8px;
}

.evidence-item div {
  min-width: 0;
}

.evidence-item b {
  display: block;
  color: var(--ink-2);
  font-size: 8px;
  font-weight: 600;
}

.evidence-item p {
  margin-top: 1px;
  color: var(--ink-3);
  font-size: 8px;
  line-height: 1.6;
  word-break: break-word;
}

.evidence-empty {
  margin-top: 7px;
  padding: 9px;
  border-top: 1px dashed var(--line);
  color: var(--ink-4);
  font-size: 8px;
  line-height: 1.6;
}

.evidence-note {
  margin-top: auto;
  padding-top: 4px;
  color: var(--ink-4);
  font-size: 7.5px;
  line-height: 1.55;
}

/* 编辑器 */
.desk-editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.proof-paper {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #e2eae5;
  border-radius: 3px 14px 14px 3px;
  background:
    repeating-linear-gradient(to bottom, transparent 0, transparent 28px, rgba(128, 163, 150, 0.08) 29px),
    #fffefa;
  box-shadow: 0 16px 34px -32px rgba(35, 70, 58, 0.38);
}

.proof-margin {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 28px;
  width: 1px;
  background: rgba(222, 147, 141, 0.18);
  pointer-events: none;
}

.proof-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 14px 16px 8px 42px;
}

.proof-label {
  display: block;
  color: var(--ink-2);
  font-size: 10px;
  font-weight: 700;
}

.proof-hint {
  display: block;
  margin-top: 1px;
  color: var(--ink-4);
  font-size: 7.5px;
}

.proof-status {
  flex: none;
}

.dirty-badge,
.ready-badge,
.draft-badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 6px;
  border-radius: 99px;
  font-size: 7.5px;
}

.dirty-badge {
  color: var(--apricot-deep);
  background: var(--apricot-soft);
}

.ready-badge {
  color: var(--mint-deep);
  background: var(--mint-soft);
}

.draft-badge {
  color: var(--ink-3);
  background: var(--surface-soft);
}

.proof-textarea {
  display: block;
  flex: 1;
  width: calc(100% - 58px);
  min-height: 238px;
  margin: 4px 16px 0 42px;
  padding: 0;
  border: 0;
  outline: 0;
  color: #32453e;
  background: transparent;
  font-family: var(--font-serif);
  font-size: 12.5px;
  line-height: 2.3;
  resize: vertical;
}

.template-picker {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 12px 0;
}

.template-picker label {
  color: var(--ink-3);
  font-size: 9px;
}

.template-picker .select {
  max-width: 260px;
}

.version-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 60vh;
  overflow: auto;
}

.version-row {
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
}

.version-row header,
.version-row footer {
  display: flex;
  align-items: center;
  gap: 8px;
}

.version-row header time {
  margin-left: auto;
  color: var(--ink-3);
  font-size: 8px;
}

.version-type {
  color: var(--ink-3);
  font-size: 8px;
}

.version-row p {
  margin: 8px 0;
  color: var(--ink-2);
  font-size: 11px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.version-row footer {
  justify-content: space-between;
  color: var(--ink-3);
  font-size: 8px;
}

.proof-textarea::placeholder {
  color: #acb8b1;
}

.published-content {
  flex: 1;
  min-height: 238px;
  margin: 4px 16px 0 42px;
  overflow: auto;
  color: #32453e;
  font-family: var(--font-serif);
  font-size: 12.5px;
  line-height: 2.3;
  white-space: pre-wrap;
}

.proof-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 9px 16px 11px 42px;
  color: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 7.5px;
}

.publish-stamp {
  position: absolute;
  right: 19px;
  bottom: 37px;
  padding: 4px 8px;
  border: 1px solid rgba(173, 104, 96, 0.3);
  border-radius: 5px;
  color: rgba(173, 104, 96, 0.5);
  font-family: var(--font-serif);
  font-size: 10px;
  font-weight: 700;
  transform: rotate(-6deg);
}

.excluded-note,
.evidence-warning {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin-top: 9px;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 8.5px;
}

.excluded-note {
  color: var(--ink-3);
  background: var(--surface-soft);
}

.excluded-note .icon,
.evidence-warning .icon {
  flex: none;
  margin-top: 1px;
}

.excluded-note b,
.evidence-warning b {
  display: block;
  font-size: 8.5px;
}

.excluded-note span,
.evidence-warning span {
  display: block;
  margin-top: 1px;
  font-size: 8px;
  line-height: 1.6;
}

.evidence-warning {
  border: 1px solid #f1e3ce;
  color: #997243;
  background: #fdf9f1;
}

.editor-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 7px;
  margin-top: 10px;
}

.editor-actions .btn {
  font-size: 10px;
}

.editor-actions .btn--primary {
  min-height: 38px;
  padding: 0 17px;
}

/* 日志 */
.log-list {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.log-row {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 9px 0;
  border-bottom: 1px dashed var(--line);
}

.log-row:last-child {
  border-bottom: 0;
}

.log-icon {
  display: grid;
  place-items: center;
  width: 27px;
  height: 27px;
  flex: none;
  border-radius: 8px;
  color: var(--mint-deep);
  background: var(--mint-soft);
}

.log-icon--withdraw {
  color: var(--apricot-deep);
  background: var(--apricot-soft);
}

.log-copy {
  min-width: 0;
  flex: 1;
}

.log-copy b {
  display: block;
  color: var(--ink-2);
  font-size: 10px;
}

.log-copy span {
  display: block;
  margin-top: 1px;
  color: var(--ink-4);
  font-size: 8.5px;
}

/* 生成舞台 */
.generate-stage {
  max-width: 1080px;
}

.generate-card {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(300px, 0.9fr);
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 26px;
  background: #fff;
  box-shadow: var(--shadow-md);
}

.generate-copy {
  padding: clamp(26px, 4vw, 46px);
}

.generate-copy h2 {
  margin-top: 8px;
  font-family: var(--font-display);
  font-size: clamp(28px, 3vw, 38px);
  font-weight: 400;
  line-height: 1.35;
}

.generate-copy > p {
  max-width: 58ch;
  margin-top: 9px;
  color: var(--ink-2);
  font-size: 11px;
  line-height: 1.85;
}

.prerequisite-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 20px;
}

.prerequisite-list > div {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.prerequisite-list > div > span {
  display: grid;
  place-items: center;
  width: 21px;
  height: 21px;
  flex: none;
  border-radius: 50%;
  color: var(--ink-4);
  background: var(--surface-soft);
}

.prerequisite-list > div.is-done > span {
  color: var(--mint-deep);
  background: var(--mint-soft);
}

.prerequisite-list b {
  display: block;
  color: var(--ink-2);
  font-size: 9.5px;
}

.prerequisite-list p {
  margin-top: 1px;
  color: var(--ink-4);
  font-size: 8.5px;
}

.generate-actions {
  display: flex;
  gap: 8px;
  margin-top: 24px;
  flex-wrap: wrap;
}

.generate-art {
  position: relative;
  min-height: 440px;
  overflow: hidden;
  background:
    radial-gradient(circle at 48% 46%, rgba(120, 219, 202, 0.26), transparent 32%),
    radial-gradient(circle at 70% 25%, rgba(153, 137, 228, 0.2), transparent 38%),
    linear-gradient(145deg, #f7fcfa, #eef4ff 60%, #fdf8f0);
}

.orbit {
  position: absolute;
  top: 50%;
  left: 50%;
  border: 1px solid rgba(67, 135, 115, 0.18);
  border-radius: 50%;
  transform: translate(-50%, -50%) rotate(-22deg);
}

.orbit--one {
  width: 290px;
  height: 130px;
}

.orbit--two {
  width: 360px;
  height: 210px;
  opacity: 0.6;
}

.ai-core {
  position: absolute;
  top: 50%;
  left: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 112px;
  height: 112px;
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 34px;
  background: rgba(255, 255, 255, 0.58);
  box-shadow: 0 22px 50px -26px rgba(37, 91, 76, 0.35);
  transform: translate(-50%, -50%) rotate(-4deg);
  backdrop-filter: blur(14px);
}

.ai-core > span {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 13px;
  color: var(--lav-deep);
  background: var(--lav-soft);
}

.ai-core > b {
  margin-top: 4px;
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 17px;
  font-weight: 600;
}

.ai-core small {
  color: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 6.5px;
  letter-spacing: 0.12em;
}

.floating-evidence {
  position: absolute;
  width: 138px;
  padding: 10px;
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 11px;
  background: rgba(255, 255, 255, 0.66);
  box-shadow: 0 14px 28px -22px rgba(40, 89, 73, 0.3);
  backdrop-filter: blur(10px);
}

.floating-evidence span {
  color: var(--ink-4);
  font-size: 7px;
  letter-spacing: 0.06em;
}

.floating-evidence p {
  margin-top: 2px;
  color: var(--ink-2);
  font-family: var(--font-serif);
  font-size: 9.5px;
}

.ev--one {
  top: 19%;
  left: 8%;
  transform: rotate(-4deg);
}

.ev--two {
  top: 12%;
  right: 6%;
  transform: rotate(3deg);
}

.ev--three {
  right: 11%;
  bottom: 21%;
  transform: rotate(-2deg);
}

.teacher-seal {
  position: absolute;
  bottom: 11%;
  left: 9%;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 8px;
  border: 1px dashed rgba(54, 129, 109, 0.35);
  border-radius: 5px;
  color: rgba(43, 120, 100, 0.72);
  font-size: 7.5px;
  transform: rotate(-4deg);
}

.generate-footnote {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 12px;
  padding: 0 5px;
  color: var(--ink-4);
  font-size: 9px;
  line-height: 1.65;
}

.generate-footnote .icon {
  flex: none;
  margin-top: 1px;
}

.exclude-form {
  display: flex;
  flex-direction: column;
  gap: 13px;
}

.exclude-target {
  padding: 10px 12px;
  border-radius: 9px;
  background: var(--surface-soft);
}

.exclude-target p {
  margin-top: 4px;
  color: var(--ink-2);
  font-size: 10px;
  line-height: 1.7;
}

.publish-modal {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.publish-count {
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 13px;
  border-radius: 11px;
  background: var(--mint-wash);
}

.publish-count__num {
  color: var(--mint-deep);
  font-family: var(--font-numbers);
  font-size: 37px;
  font-weight: 600;
  line-height: 1;
}

.publish-count b {
  display: block;
  color: var(--ink);
  font-size: 11px;
}

.publish-count span:not(.publish-count__num) {
  display: block;
  margin-top: 2px;
  color: var(--ink-3);
  font-size: 9px;
}

.publish-gates {
  display: flex;
  flex-wrap: wrap;
  gap: 5px 12px;
  color: var(--mint-deep);
  font-size: 8.5px;
}

.publish-gates span {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}


.student-preview {
  display: flex;
  justify-content: center;
}

.preview-browser {
  width: 100%;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: #f6faf8;
}

.preview-browser__bar {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 9px 13px;
  border-bottom: 1px solid var(--line);
  background: #fff;
}

.preview-browser__bar span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #dce8e3;
}

.preview-browser__bar em {
  margin-left: 7px;
  color: var(--ink-4);
  font-size: 8px;
  font-style: normal;
}

.preview-body {
  padding: 20px;
}

.preview-kicker {
  color: var(--peri-deep);
  font-size: 8.5px;
  letter-spacing: 0.08em;
}

.preview-body h3 {
  margin-top: 7px;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 15px;
  line-height: 1.8;
}

.preview-options {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin-top: 10px;
}

.preview-options span {
  padding: 6px 9px;
  border-radius: 7px;
  color: var(--ink-2);
  background: #fff;
  font-size: 10px;
}

.preview-options span.is-correct {
  color: var(--mint-deep);
  background: var(--mint-soft);
}

.preview-options b {
  margin-right: 6px;
  font-family: var(--font-mono);
  font-size: 8px;
}

.preview-commentary {
  margin-top: 13px;
  padding: 12px 14px;
  border-left: 2px solid var(--peri);
  border-radius: 0 9px 9px 0;
  background: #fff;
}

.preview-commentary > span {
  color: var(--peri-deep);
  font-size: 8px;
  font-weight: 600;
}

.preview-commentary p {
  margin-top: 4px;
  color: var(--ink-2);
  font-family: var(--font-serif);
  font-size: 11px;
  line-height: 1.9;
  white-space: pre-wrap;
}

.preview-boundary {
  margin-top: 12px;
  color: var(--ink-4);
  font-size: 8px;
  line-height: 1.6;
}

@media (max-width: 1180px) {
  .gate-console {
    grid-template-columns: minmax(0, 1.4fr) auto;
  }

  .gate-progress {
    grid-column: 1 / -1;
    padding-top: 10px;
    padding-left: 0;
    border-top: 1px dashed var(--line);
    border-left: 0;
  }

  .gate-actions {
    flex-direction: row;
    align-items: center;
    margin-top: 7px;
  }

  .desk-grid {
    grid-template-columns: 1fr;
  }

  .desk-primary {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 980px) {
  .review-layout {
    grid-template-columns: 1fr;
  }

  .item-rail {
    position: static;
    max-height: none;
  }

  .item-list {
    display: grid;
    grid-template-columns: 1fr 1fr;
    max-height: 270px;
  }

  .generate-card {
    grid-template-columns: 1fr;
  }

  .generate-art {
    min-height: 330px;
  }
}

@media (max-width: 720px) {
  .gate-console {
    grid-template-columns: 1fr;
    padding: 15px;
    gap: 12px;
  }

  .gate-metrics {
    justify-content: space-between;
    padding: 8px 0;
    border-top: 1px dashed var(--line);
    border-bottom: 1px dashed var(--line);
  }

  .gate-progress {
    border-top: 0;
    padding-top: 0;
  }

  .gate-actions .btn {
    flex: 1;
  }

  .review-layout {
    gap: 10px;
  }

  .item-list {
    grid-template-columns: 1fr;
    max-height: 260px;
  }

  .desk-head {
    flex-direction: column;
  }

  .desk-actions {
    width: 100%;
  }

  .desk-actions .btn {
    flex: 1;
  }

  .desk-primary {
    display: flex;
  }

  .proof-textarea,
  .published-content {
    min-height: 200px;
    font-size: 12px;
  }

  .editor-actions {
    flex-direction: column-reverse;
  }

  .editor-actions .btn {
    width: 100%;
  }

  .generate-copy {
    padding: 22px 17px;
  }

  .generate-art {
    min-height: 280px;
  }

  .floating-evidence {
    width: 118px;
  }
}
</style>
