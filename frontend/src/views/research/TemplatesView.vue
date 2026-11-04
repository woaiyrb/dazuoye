<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { researchApi } from '@/api/research'
import { errorMessage } from '@/api/errors'
import type { PublicTemplate, TemplateStatus } from '@/types/models'
import { formatDateTime, formatRelative } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import PageHeader from '@/components/PageHeader.vue'
import AppIcon from '@/components/AppIcon.vue'
import AppModal from '@/components/AppModal.vue'
import AppEmpty from '@/components/AppEmpty.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import AppPagination from '@/components/AppPagination.vue'
import { fetchAllPages } from '@/utils/pagination'

const PAGE_SIZE = 8

type FilterKey = 'ALL' | TemplateStatus

const route = useRoute()
const auth = useAuthStore()
const ui = useUiStore()

const loading = ref(true)
const error = ref('')
const templates = ref<PublicTemplate[]>([])
const filter = ref<FilterKey>('ALL')
const query = ref('')
const listPage = ref(1)
const truncated = ref(false)
const selectedId = ref<number | null>(null)
const detailPanel = ref<HTMLElement | null>(null)

const statusOptions: { key: FilterKey; label: string }[] = [
  { key: 'ALL', label: '全部' },
  { key: 'PENDING', label: '待审核' },
  { key: 'APPROVED', label: '已通过' },
  { key: 'REJECTED', label: '已退回' },
]

const counts = computed(() => ({
  ALL: templates.value.length,
  PENDING: templates.value.filter((template) => template.status === 'PENDING').length,
  APPROVED: templates.value.filter((template) => template.status === 'APPROVED').length,
  REJECTED: templates.value.filter((template) => template.status === 'REJECTED').length,
}))

const orderedTemplates = computed(() =>
  [...templates.value].sort((a, b) => {
    if (a.status === 'PENDING' && b.status !== 'PENDING') return -1
    if (a.status !== 'PENDING' && b.status === 'PENDING') return 1
    return b.createdAt.localeCompare(a.createdAt)
  }),
)

const filteredTemplates = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  return orderedTemplates.value.filter((template) => {
    if (filter.value !== 'ALL' && template.status !== filter.value) return false
    if (!keyword) return true
    return [template.title, template.category, template.content, template.createdByName]
      .filter((value): value is string => typeof value === 'string')
      .some((value) => value.toLowerCase().includes(keyword))
  })
})

const pagedTemplates = computed(() => {
  const start = (listPage.value - 1) * PAGE_SIZE
  return filteredTemplates.value.slice(start, start + PAGE_SIZE)
})

const selected = computed(
  () => templates.value.find((template) => template.id === selectedId.value) ?? null,
)

const selectedSections = computed(() => sectionsOf(selected.value))
const reviewerLabel = computed(() => {
  if (!selected.value?.reviewedBy) return '—'
  return selected.value.reviewedBy === auth.user?.id ? auth.displayName : '教研组'
})

watch(
  filteredTemplates,
  (list) => {
    if (!list.some((template) => template.id === selectedId.value)) {
      selectedId.value = list[0]?.id ?? null
    }
  },
  { immediate: true },
)

watch([filter, query], () => {
  listPage.value = 1
})

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await fetchAllPages((page, pageSize) =>
      researchApi.listTemplates({ page, pageSize }),
    )
    templates.value = result.items
    truncated.value = result.truncated
    listPage.value = 1
    const focus = Number(route.query.focus)
    if (focus && result.items.some((template) => template.id === focus)) {
      filter.value = 'ALL'
      selectedId.value = focus
    }
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

function sectionsOf(template: PublicTemplate | null): string[] {
  const raw = template?.structure?.sections
  return Array.isArray(raw) ? raw.filter((item): item is string => typeof item === 'string') : []
}

function selectTemplate(templateId: number): void {
  selectedId.value = templateId
  if (window.innerWidth < 940) {
    void nextTick(() => {
      detailPanel.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
  }
}

const reviewOpen = ref(false)
const reviewDecision = ref<'APPROVED' | 'REJECTED'>('APPROVED')
const reviewNote = ref('')
const reviewError = ref('')
const reviewing = ref(false)

function openReview(decision: 'APPROVED' | 'REJECTED'): void {
  reviewDecision.value = decision
  reviewNote.value = ''
  reviewError.value = ''
  reviewOpen.value = true
}

async function submitReview(): Promise<void> {
  if (!selected.value) return
  reviewError.value = ''
  const note = reviewNote.value.trim()
  if (reviewDecision.value === 'REJECTED' && !note) {
    reviewError.value = '退回模板时必须写明原因，方便教师修改后再提交。'
    return
  }
  reviewing.value = true
  try {
    await researchApi.reviewTemplate(selected.value.id, {
      decision: reviewDecision.value,
      reviewNote: note || undefined,
    })
    ui.toast(
      'success',
      reviewDecision.value === 'APPROVED' ? '模板已通过' : '模板已退回',
      reviewDecision.value === 'APPROVED'
        ? `「${selected.value.title}」已进入公共模板库。`
        : `退回原因已记录，教师可修改后重新提交。`,
    )
    reviewOpen.value = false
    await load()
  } catch (cause) {
    reviewError.value = errorMessage(cause)
  } finally {
    reviewing.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="templates-page">
    <PageHeader
      eyebrow="RESEARCH · PUBLIC TEMPLATE LIBRARY"
      title="公共讲评模板"
      subtitle="审核教师投稿的讲评骨架，沉淀可复用的表达结构。模板只影响讲评怎么写，不改变成绩、批改事实，也不会自动发布。"
    >
      <template #actions>
        <button
          class="btn btn--soft btn--sm"
          type="button"
          :class="{ 'is-active-filter': filter === 'PENDING' }"
          @click="filter = 'PENDING'"
        >
          <span class="queue-dot" />
          待审 {{ counts.PENDING }}
        </button>
      </template>
    </PageHeader>

    <section class="library-strip">
      <div class="library-copy">
        <span class="library-kicker">CURATED BY RESEARCH</span>
        <p>好模板给结构，<span class="hand-accent">不给结论。</span></p>
        <span class="library-note">通过后的模板由教师在具体讲评中按需引用，AI 不能绕过审核直接采用。</span>
      </div>
      <div class="library-meter">
        <div class="meter-block">
          <span>待审核</span>
          <b class="warm">{{ counts.PENDING }}</b>
        </div>
        <div class="meter-block">
          <span>已通过</span>
          <b>{{ counts.APPROVED }}</b>
        </div>
        <div class="meter-block">
          <span>已退回</span>
          <b class="muted">{{ counts.REJECTED }}</b>
        </div>
      </div>
      <span class="library-mark" aria-hidden="true">TPL</span>
    </section>

    <div v-if="loading" class="card loading-card">
      <span class="skeleton" style="width: 230px; height: 10px" />
    </div>

    <div v-else-if="error" class="card">
      <AppEmpty icon="alert-circle" title="模板加载失败" :description="error">
        <button class="btn btn--ghost btn--sm" @click="load">重试</button>
      </AppEmpty>
    </div>

    <template v-else>
      <div class="template-toolbar">
        <div class="segmented" role="tablist" aria-label="模板状态">
          <button
            v-for="option in statusOptions"
            :key="option.key"
            type="button"
            role="tab"
            :aria-selected="filter === option.key"
            :class="{ 'is-active': filter === option.key }"
            @click="filter = option.key"
          >
            {{ option.label }}
            <span class="count">{{ counts[option.key] }}</span>
          </button>
        </div>
        <div class="input-icon toolbar-search">
          <AppIcon name="search" :size="15" />
          <input
            v-model="query"
            class="input"
            type="search"
            placeholder="搜索标题、学科分类或投稿人…"
            aria-label="搜索模板"
          />
        </div>
      </div>

      <div class="template-layout">
        <section class="docket card card-flush">
          <header class="docket-head">
            <span>模板目录</span>
            <span class="mono">{{ filteredTemplates.length }} / {{ templates.length }}</span>
          </header>
          <div v-if="filteredTemplates.length" class="docket-list">
            <button
              v-for="template in pagedTemplates"
              :key="template.id"
              type="button"
              class="docket-item"
              :class="{ 'is-selected': template.id === selectedId }"
              @click="selectTemplate(template.id)"
            >
              <div class="docket-item__top">
                <span class="docket-index">{{ String(template.id).padStart(3, '0') }}</span>
                <StatusBadge :status="template.status" plain />
                <span class="docket-time">{{ formatRelative(template.createdAt) }}</span>
              </div>
              <b class="docket-title">{{ template.title }}</b>
              <div class="docket-meta">
                <span v-if="template.category" class="tag">{{ template.category }}</span>
                <span>{{ template.createdByName || '教师投稿' }}</span>
                <span class="sub-sep" />
                <span>{{ sectionsOf(template).length }} 个结构槽位</span>
              </div>
            </button>
            <AppPagination v-model:page="listPage" :page-size="PAGE_SIZE" :total="filteredTemplates.length" />
            <p v-if="truncated" class="docket-truncated">当前模板量超过加载上限，仅展示前 2000 条。</p>
          </div>
          <AppEmpty
            v-else
            icon="file"
            :title="query ? '没有匹配的模板' : '这个状态下暂无模板'"
            :description="query ? '换一个关键词，或切换状态筛选。' : '新的教师投稿会在审核队列中出现。'"
          />
        </section>

        <section ref="detailPanel" class="detail-wrap">
          <article v-if="selected" class="paper-card" :data-status="selected.status">
            <span class="paper-index" aria-hidden="true">{{ String(selected.id).padStart(3, '0') }}</span>
            <div class="paper-stamp" :data-status="selected.status">
              {{ selected.status === 'PENDING' ? '待审' : selected.status === 'APPROVED' ? '已通过' : '已退回' }}
            </div>

            <header class="paper-head">
              <div class="paper-overline">
                <span v-if="selected.category" class="tag">{{ selected.category }}</span>
                <StatusBadge :status="selected.status" />
                <span class="paper-code mono">TPL-{{ String(selected.id).padStart(3, '0') }}</span>
              </div>
              <h2>{{ selected.title }}</h2>
              <p class="paper-byline">
                投稿人 {{ selected.createdByName || '教师投稿' }}
                <span class="sub-sep" />
                {{ formatDateTime(selected.createdAt) }} 提交
              </p>
            </header>

            <div class="paper-rule" />

            <section class="paper-section">
              <span class="paper-label">模板说明</span>
              <p class="paper-content">{{ selected.content || '投稿人暂未补充模板说明。' }}</p>
            </section>

            <section class="paper-section">
              <span class="paper-label">结构骨架</span>
              <div v-if="selectedSections.length" class="structure-list">
                <div v-for="(section, index) in selectedSections" :key="section" class="structure-slot">
                  <span class="structure-no">{{ String(index + 1).padStart(2, '0') }}</span>
                  <div class="structure-main">
                    <b>{{ section }}</b>
                    <span class="structure-line" />
                  </div>
                  <span class="structure-kind">结构槽位</span>
                </div>
              </div>
              <p v-else class="paper-empty">该模板尚未提供结构骨架，建议退回补充。</p>
            </section>

            <section class="meta-grid">
              <div>
                <span>投稿人</span>
                <b>{{ selected.createdByName || '教师投稿' }}</b>
              </div>
              <div>
                <span>提交时间</span>
                <b>{{ formatDateTime(selected.createdAt) }}</b>
              </div>
              <div>
                <span>审核人</span>
                <b>{{ reviewerLabel }}</b>
              </div>
              <div>
                <span>审核时间</span>
                <b>{{ selected.reviewedAt ? formatDateTime(selected.reviewedAt) : '—' }}</b>
              </div>
            </section>

            <div
              v-if="selected.reviewNote"
              class="review-note"
              :data-status="selected.status"
            >
              <span class="review-note__label">
                <AppIcon :name="selected.status === 'REJECTED' ? 'alert-circle' : 'check-circle'" :size="14" />
                {{ selected.status === 'REJECTED' ? '退回原因' : '审核意见' }}
              </span>
              <p>{{ selected.reviewNote }}</p>
            </div>

            <section v-if="selected.status === 'PENDING'" class="review-desk">
              <div class="review-desk__head">
                <div>
                  <span class="paper-label">审核结论</span>
                  <p>通过前请确认：结构完整、措辞指向错误而非学生、结论可追溯到原题证据。</p>
                </div>
                <span class="review-desk__mark"><AppIcon name="pen" :size="16" /></span>
              </div>
              <div class="review-actions">
                <button class="btn btn--ghost" type="button" @click="openReview('REJECTED')">
                  <AppIcon name="rotate" :size="14" />
                  退回修改
                </button>
                <button class="btn btn--primary" type="button" @click="openReview('APPROVED')">
                  <AppIcon name="check" :size="15" />
                  通过模板
                </button>
              </div>
              <p class="review-guard">
                <AppIcon name="shield" :size="12" />
                通过只代表可被教师引用，不会自动生成讲评，也不会发布给任何学生。
              </p>
            </section>

            <section v-else class="status-foot" :data-status="selected.status">
              <AppIcon :name="selected.status === 'APPROVED' ? 'check-circle' : 'inbox'" :size="16" />
              <p v-if="selected.status === 'APPROVED'">
                该模板已进入公共库。教师可在讲评准备时引用其结构，具体内容仍需结合本班证据确认。
              </p>
              <p v-else>
                该模板当前不可用。退回原因已记录，等待投稿教师修改后重新提交。
              </p>
            </section>
          </article>

          <div v-else class="card detail-empty">
            <AppEmpty
              icon="file"
              title="选择一份模板"
              description="从左侧目录中选一份，查看结构、审核记录，并给出审核结论。"
            />
          </div>

          <section v-if="selected?.status === 'PENDING'" class="review-checklist">
            <span class="checklist-title"><AppIcon name="list" :size="14" /> 审核检查单</span>
            <div class="checklist-items">
              <span><AppIcon name="check" :size="11" /> 不含“粗心”“不认真”等人格标签</span>
              <span><AppIcon name="check" :size="11" /> 结构完整，包含可复用的讲评步骤</span>
              <span><AppIcon name="check" :size="11" /> 不替代原题证据，不夹带个别学生信息</span>
            </div>
          </section>
        </section>
      </div>
    </template>

    <AppModal
      v-model="reviewOpen"
      :title="reviewDecision === 'APPROVED' ? '通过这份模板？' : '退回这份模板？'"
      :subtitle="selected?.title"
      size="sm"
      :close-on-backdrop="!reviewing"
    >
      <div v-if="reviewError" class="error-banner modal-error">
        <AppIcon name="alert-circle" :size="15" />
        {{ reviewError }}
      </div>

      <div class="decision-summary" :data-status="reviewDecision">
        <span class="decision-icon">
          <AppIcon :name="reviewDecision === 'APPROVED' ? 'check-circle' : 'rotate'" :size="19" />
        </span>
        <div>
          <b>{{ reviewDecision === 'APPROVED' ? '进入公共模板库' : '退回投稿教师修改' }}</b>
          <p>
            {{
              reviewDecision === 'APPROVED'
                ? '通过后仅作为可选结构供教师引用，不会自动发布。'
                : '模板会标记为已退回，教师可在修改后重新提交。'
            }}
          </p>
        </div>
      </div>

      <div class="field">
        <label for="review-note">
          {{ reviewDecision === 'APPROVED' ? '审核意见（可选）' : '退回原因（必填）' }}
        </label>
        <textarea
          id="review-note"
          v-model="reviewNote"
          class="textarea"
          rows="4"
          maxlength="200"
          :placeholder="
            reviewDecision === 'APPROVED'
              ? '例如：结构清晰，可作为“去括号”讲评骨架使用。'
              : '例如：模板中含“计算粗心”等标签化表述，请改为具体错误类型后再提交。'
          "
        />
        <span class="counter">{{ reviewNote.length }} / 200</span>
        <span class="field-hint">审核意见会写入模板记录与操作日志，教师可见。</span>
      </div>

      <template #footer>
        <button class="btn btn--ghost" type="button" :disabled="reviewing" @click="reviewOpen = false">
          取消
        </button>
        <button
          class="btn"
          :class="reviewDecision === 'APPROVED' ? 'btn--primary' : 'btn--danger-solid'"
          type="button"
          :disabled="reviewing"
          @click="submitReview"
        >
          {{ reviewing ? '提交中…' : reviewDecision === 'APPROVED' ? '确认通过' : '确认退回' }}
        </button>
      </template>
    </AppModal>
  </div>
</template>

<style scoped>
.templates-page {
  display: flex;
  flex-direction: column;
}

.queue-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--apricot-deep);
}

.library-strip {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 22px;
  margin-bottom: 16px;
  padding: 17px 23px;
  overflow: hidden;
  border: 1px solid #eee2d1;
  border-radius: 18px;
  background:
    radial-gradient(circle at 88% 0, rgba(255, 255, 255, 0.92), transparent 32%),
    linear-gradient(105deg, #fffdf9, #fbf4e7);
}

.library-copy {
  min-width: 0;
}

.library-kicker {
  display: block;
  color: #a98756;
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.15em;
}

.library-copy p {
  margin-top: 3px;
  color: #4b3f2e;
  font-family: var(--font-serif);
  font-size: 16px;
  font-weight: 600;
}

.hand-accent {
  color: var(--apricot-deep);
  font-family: var(--font-hand);
  font-weight: 400;
}

.library-note {
  display: block;
  margin-top: 2px;
  color: #97856e;
  font-size: 9.5px;
}

.library-meter {
  display: flex;
  gap: 24px;
  flex: none;
  padding-left: 23px;
  border-left: 1px dashed rgba(169, 135, 86, 0.28);
}

.meter-block {
  text-align: center;
}

.meter-block span {
  display: block;
  color: #9b896f;
  font-size: 8px;
}

.meter-block b {
  display: block;
  margin-top: 1px;
  color: #5c4a32;
  font-family: var(--font-numbers);
  font-size: 23px;
  font-weight: 600;
  line-height: 1.15;
}

.meter-block b.warm {
  color: var(--apricot-deep);
}

.meter-block b.muted {
  color: #ab9c88;
}

.library-mark {
  position: absolute;
  right: 14px;
  bottom: -20px;
  color: rgba(156, 107, 44, 0.045);
  font-family: var(--font-numbers);
  font-size: 78px;
  line-height: 1;
}

.template-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 13px;
  flex-wrap: wrap;
}

.toolbar-search {
  width: min(350px, 100%);
}

.template-layout {
  display: grid;
  grid-template-columns: minmax(265px, 0.78fr) minmax(0, 1.55fr);
  gap: 16px;
  align-items: start;
}

.docket {
  position: sticky;
  top: 84px;
  max-height: calc(100vh - 112px);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.docket-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 14px 16px 11px;
  border-bottom: 1px solid var(--line);
  color: var(--ink-3);
  font-family: var(--font-mono);
  font-size: 8.5px;
  letter-spacing: 0.1em;
}

.docket-head .mono {
  color: var(--ink-4);
  font-size: 8.5px;
}

.docket-list {
  overflow-y: auto;
  padding: 6px;
}

.docket-item {
  position: relative;
  display: block;
  width: 100%;
  padding: 11px 11px 11px 13px;
  border-radius: 11px;
  text-align: left;
  transition: 0.18s;
}

.docket-item + .docket-item {
  margin-top: 2px;
  border-top: 1px dashed transparent;
}

.docket-item:hover {
  background: #fcfaf6;
}

.docket-item.is-selected {
  background: linear-gradient(105deg, #fdf6ea, #fffaf2);
  box-shadow: inset 2px 0 0 var(--apricot);
}

.docket-item__top {
  display: flex;
  align-items: center;
  gap: 5px;
}

.docket-index {
  color: #b59a72;
  font-family: var(--font-numbers);
  font-size: 12px;
}

.docket-item__top :deep(.badge) {
  min-height: 17px;
  padding: 0 6px;
  font-size: 8px;
}

.docket-time {
  margin-left: auto;
  color: var(--ink-4);
  font-size: 8px;
}

.docket-title {
  display: block;
  margin-top: 6px;
  overflow: hidden;
  color: var(--ink-2);
  font-size: 11.5px;
  font-weight: 600;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.docket-item.is-selected .docket-title {
  color: #57452c;
}

.docket-meta {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 5px;
  overflow: hidden;
  color: var(--ink-4);
  font-size: 8px;
  white-space: nowrap;
}

.docket-meta .tag {
  padding: 0 5px;
  color: #927747;
  background: #f8f1e5;
  font-size: 7.5px;
}

.sub-sep {
  width: 2px;
  height: 2px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.5;
}

.docket :deep(.empty-state) {
  padding: 28px 16px;
}

.detail-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.paper-card {
  position: relative;
  overflow: hidden;
  padding: 25px 28px 24px;
  border: 1px solid #e7e1d6;
  border-left: 4px solid #e6d4b8;
  border-radius: 5px 20px 20px 5px;
  background:
    linear-gradient(90deg, rgba(174, 144, 99, 0.035) 1px, transparent 1px) 0 0 / 31px 100%,
    linear-gradient(180deg, #fffefa, #fffdf9 68%, #fefcf7);
  box-shadow: 0 20px 48px -38px rgba(74, 58, 34, 0.35);
}

.paper-card[data-status='APPROVED'] {
  border-left-color: #a9d8c9;
}

.paper-card[data-status='REJECTED'] {
  border-left-color: #ecc7c1;
}

.paper-index {
  position: absolute;
  top: 8px;
  right: 18px;
  color: rgba(156, 107, 44, 0.055);
  font-family: var(--font-numbers);
  font-size: 106px;
  line-height: 1;
  letter-spacing: -0.05em;
  pointer-events: none;
}

.paper-stamp {
  position: absolute;
  top: 23px;
  right: 28px;
  display: grid;
  width: 58px;
  height: 58px;
  place-items: center;
  border: 1.5px solid rgba(183, 137, 77, 0.5);
  border-radius: 50%;
  color: rgba(166, 118, 55, 0.72);
  font-family: var(--font-serif);
  font-size: 11px;
  letter-spacing: 0.08em;
  transform: rotate(9deg);
  pointer-events: none;
}

.paper-stamp[data-status='APPROVED'] {
  border-color: rgba(65, 150, 124, 0.4);
  color: rgba(44, 126, 101, 0.68);
}

.paper-stamp[data-status='REJECTED'] {
  border-color: rgba(180, 95, 91, 0.38);
  color: rgba(180, 95, 91, 0.66);
}

.paper-head {
  position: relative;
  padding-right: 72px;
}

.paper-overline {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.paper-overline .tag {
  color: #8d6a3d;
  background: var(--apricot-soft);
}

.paper-code {
  color: var(--ink-4);
  font-size: 8px;
}

.paper-head h2 {
  max-width: 24ch;
  margin-top: 9px;
  color: #39332a;
  font-family: var(--font-display);
  font-size: clamp(23px, 2.3vw, 31px);
  font-weight: 400;
  letter-spacing: 0.01em;
  line-height: 1.35;
}

.paper-byline {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  color: #9b8b75;
  font-size: 9.5px;
  flex-wrap: wrap;
}

.paper-rule {
  height: 1px;
  margin: 18px 0;
  background: repeating-linear-gradient(90deg, #e8dccb 0 5px, transparent 5px 10px);
}

.paper-section + .paper-section {
  margin-top: 19px;
}

.paper-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #a4865b;
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.12em;
}

.paper-label::before {
  content: '';
  width: 12px;
  height: 1px;
  background: currentColor;
  opacity: 0.6;
}

.paper-content {
  margin-top: 7px;
  color: #605647;
  font-size: 12.5px;
  line-height: 1.9;
}

.structure-list {
  display: flex;
  flex-direction: column;
  gap: 1px;
  margin-top: 8px;
  border: 1px solid #eee7db;
  border-radius: 11px;
  background: #eee7db;
  overflow: hidden;
}

.structure-slot {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: #fffdf9;
}

.structure-no {
  color: #b99c73;
  font-family: var(--font-numbers);
  font-size: 11px;
}

.structure-main {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
  flex: 1;
}

.structure-main b {
  flex: none;
  color: #544736;
  font-size: 10.5px;
  font-weight: 600;
}

.structure-line {
  height: 1px;
  flex: 1;
  background: repeating-linear-gradient(90deg, #e8e0d4 0 4px, transparent 4px 8px);
}

.structure-kind {
  flex: none;
  color: #b4a58f;
  font-size: 7.5px;
}

.paper-empty {
  margin-top: 7px;
  padding: 12px;
  border: 1px dashed #ecdcc5;
  border-radius: 9px;
  color: #a78658;
  background: #fdf9f1;
  font-size: 10.5px;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 11px;
  margin-top: 21px;
  padding-top: 15px;
  border-top: 1px solid #eee6da;
}

.meta-grid span {
  display: block;
  color: #ad9c86;
  font-size: 8px;
}

.meta-grid b {
  display: block;
  margin-top: 2px;
  overflow: hidden;
  color: #6b5d4b;
  font-size: 9.5px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.review-note {
  margin-top: 17px;
  padding: 12px 14px;
  border: 1px solid #dfeee8;
  border-radius: 10px;
  background: #f7fcfa;
}

.review-note[data-status='REJECTED'] {
  border-color: #f2dfdc;
  background: #fdf7f6;
}

.review-note__label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--mint-deep);
  font-size: 9px;
  font-weight: 700;
}

.review-note[data-status='REJECTED'] .review-note__label {
  color: var(--coral-deep);
}

.review-note p {
  margin-top: 4px;
  color: var(--ink-2);
  font-size: 10.5px;
  line-height: 1.75;
}

.review-desk {
  margin-top: 20px;
  padding: 16px;
  border: 1px solid #ecdfcb;
  border-radius: 13px;
  background: linear-gradient(110deg, #fffdf8, #fdf7ed);
}

.review-desk__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.review-desk__head p {
  max-width: 58ch;
  margin-top: 5px;
  color: #8f7c63;
  font-size: 9.5px;
  line-height: 1.7;
}

.review-desk__mark {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  flex: none;
  border-radius: 50%;
  color: var(--apricot-deep);
  background: rgba(255, 255, 255, 0.7);
}

.review-actions {
  display: flex;
  gap: 8px;
  margin-top: 13px;
  flex-wrap: wrap;
}

.review-actions .btn--ghost {
  border-color: #e7d7bf;
  background: rgba(255, 255, 255, 0.72);
}

.review-guard {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  margin-top: 10px;
  color: #ad9a80;
  font-size: 8.5px;
  line-height: 1.6;
}

.review-guard .icon {
  flex: none;
  margin-top: 1px;
}

.status-foot {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin-top: 20px;
  padding: 13px 14px;
  border-radius: 11px;
  color: #4e7367;
  background: #f4fbf8;
}

.status-foot[data-status='REJECTED'] {
  color: #986862;
  background: #fdf7f6;
}

.status-foot p {
  font-size: 10px;
  line-height: 1.75;
}

.detail-empty {
  min-height: 240px;
  display: grid;
  place-items: center;
}

.review-checklist {
  display: flex;
  align-items: flex-start;
  gap: 15px;
  padding: 13px 15px;
  border: 1px solid #eee7db;
  border-radius: 12px;
  background: #fffdf9;
  flex-wrap: wrap;
}

.checklist-title {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #9c7c4e;
  font-size: 9px;
  font-weight: 700;
  white-space: nowrap;
}

.checklist-items {
  display: flex;
  gap: 6px 14px;
  flex: 1;
  flex-wrap: wrap;
}

.checklist-items span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--ink-3);
  font-size: 8.5px;
}

.checklist-items .icon {
  color: var(--mint);
}

.decision-summary {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  margin-bottom: 16px;
  padding: 12px;
  border: 1px solid #dfeee8;
  border-radius: 11px;
  background: #f7fcfa;
}

.decision-summary[data-status='REJECTED'] {
  border-color: #f2dfdc;
  background: #fdf7f6;
}

.decision-icon {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  flex: none;
  border-radius: 50%;
  color: var(--mint-deep);
  background: #e6f6f0;
}

.decision-summary[data-status='REJECTED'] .decision-icon {
  color: var(--coral-deep);
  background: #fbeceb;
}

.decision-summary b {
  display: block;
  color: var(--ink-2);
  font-size: 11px;
}

.decision-summary p {
  margin-top: 2px;
  color: var(--ink-3);
  font-size: 9.5px;
  line-height: 1.65;
}

.modal-error {
  margin-bottom: 14px;
}

@media (max-width: 1080px) {
  .template-layout {
    grid-template-columns: minmax(240px, 0.85fr) minmax(0, 1.3fr);
  }

  .meta-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 940px) {
  .template-layout {
    grid-template-columns: 1fr;
  }

  .docket {
    position: static;
    max-height: none;
  }

  .docket-list {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 3px;
  }

  .docket-item + .docket-item {
    margin-top: 0;
  }
}

@media (max-width: 720px) {
  .library-strip {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
    padding: 16px 17px;
  }

  .library-meter {
    width: 100%;
    justify-content: space-between;
    padding-top: 11px;
    padding-left: 0;
    border-top: 1px dashed rgba(169, 135, 86, 0.28);
    border-left: 0;
  }

  .template-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-search {
    width: 100%;
  }

  .docket-list {
    grid-template-columns: 1fr;
  }

  .paper-card {
    padding: 19px 17px 19px;
    border-radius: 4px 16px 16px 4px;
  }

  .paper-head {
    padding-right: 0;
  }

  .paper-stamp {
    display: none;
  }

  .paper-index {
    font-size: 72px;
  }

  .structure-main {
    align-items: flex-start;
    flex-direction: column;
    gap: 2px;
  }

  .structure-line {
    width: 100%;
    flex: none;
  }

  .meta-grid {
    grid-template-columns: 1fr 1fr;
  }

  .review-checklist {
    align-items: flex-start;
    flex-direction: column;
    gap: 7px;
  }
}

.docket-truncated {
  margin: 8px 0 0;
  color: var(--apricot-deep);
  font-size: 8px;
  line-height: 1.6;
}
</style>
