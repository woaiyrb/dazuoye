<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { researchApi } from '@/api/research'
import { errorMessage } from '@/api/errors'
import type { OperationLog } from '@/types/models'
import { formatDateTime, formatRelative } from '@/utils/format'
import {
  actionLabel,
  actionTone,
  actorLabel,
  targetLabel,
  isResearchVisibleLog,
  researchLogSummary,
  AUDIT_ROLE,
} from '@/utils/audit'
import PageHeader from '@/components/PageHeader.vue'
import AppIcon from '@/components/AppIcon.vue'
import AppModal from '@/components/AppModal.vue'
import AppEmpty from '@/components/AppEmpty.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { fetchAllPages } from '@/utils/pagination'

const PAGE_SIZE = 8

const loading = ref(true)
const error = ref('')
const rawLogs = ref<OperationLog[]>([])
const actionFilter = ref('')
const targetFilter = ref('')
const actorFilter = ref<number | ''>('')
const page = ref(1)
const truncated = ref(false)

const visibleLogs = computed(() =>
  rawLogs.value.filter((log) => isResearchVisibleLog(log)).sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
)
const hiddenCount = computed(() => rawLogs.value.length - visibleLogs.value.length)
const governanceCount = computed(
  () =>
    visibleLogs.value.filter(
      (log) => log.action.includes('KNOWLEDGE_POINT') || log.action.includes('TEMPLATE'),
    ).length,
)
const authCount = computed(() => visibleLogs.value.filter((log) => log.action === 'LOGIN').length)
const teachingCount = computed(
  () => visibleLogs.value.length - governanceCount.value - authCount.value,
)

const actionOptions = computed(() =>
  [...new Set(visibleLogs.value.map((log) => log.action))]
    .map((action) => ({ value: action, label: actionLabel(action) }))
    .sort((a, b) => a.label.localeCompare(b.label, 'zh-CN')),
)
const targetOptions = computed(() =>
  [...new Set(visibleLogs.value.map((log) => log.targetType).filter((value): value is string => !!value))]
    .map((target) => ({ value: target, label: targetLabel(target) }))
    .sort((a, b) => a.label.localeCompare(b.label, 'zh-CN')),
)
const actorOptions = computed(() => {
  const map = new Map<number, string>()
  for (const log of visibleLogs.value) {
    if (log.actorId) map.set(log.actorId, actorLabel(log))
  }
  return [...map.entries()].map(([id, name]) => ({ id, name }))
})

const filteredLogs = computed(() =>
  visibleLogs.value.filter((log) => {
    if (actionFilter.value && log.action !== actionFilter.value) return false
    if (targetFilter.value && log.targetType !== targetFilter.value) return false
    if (actorFilter.value !== '' && log.actorId !== actorFilter.value) return false
    return true
  }),
)
const pageCount = computed(() => Math.max(1, Math.ceil(filteredLogs.value.length / PAGE_SIZE)))
const pageItems = computed(() =>
  filteredLogs.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE),
)
const filtersActive = computed(
  () => !!actionFilter.value || !!targetFilter.value || actorFilter.value !== '',
)

watch([actionFilter, targetFilter, actorFilter], () => {
  page.value = 1
})
watch(pageCount, (count) => {
  if (page.value > count) page.value = count
})

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await fetchAllPages((page, pageSize) =>
      researchApi.listOperationLogs({ page, pageSize }),
    )
    rawLogs.value = result.items
    truncated.value = result.truncated
    page.value = 1
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

function resetFilters(): void {
  actionFilter.value = ''
  targetFilter.value = ''
  actorFilter.value = ''
}

function roleLabel(log: OperationLog): string {
  return log.actorRole ? AUDIT_ROLE[log.actorRole] : '系统'
}

const detailOpen = ref(false)
const selectedLog = ref<OperationLog | null>(null)
const selectedEntries = computed(() => {
  if (!selectedLog.value) return []
  return detailEntries(selectedLog.value)
})

function openDetail(log: OperationLog): void {
  selectedLog.value = log
  detailOpen.value = true
}

function detailEntries(log: OperationLog): { label: string; value: string }[] {
  if (log.action === 'IMPORT_GRADING' || log.action === 'REVISE_GRADING') {
    return [{ label: '数据裁剪', value: '批改成绩与逐题明细不向教研视图展示。' }]
  }
  if (!log.detail) return []
  return Object.entries(log.detail).map(([key, value]) => ({
    label: key,
    value: typeof value === 'string' ? value : JSON.stringify(value),
  }))
}

onMounted(load)
</script>

<template>
  <div class="logs-page">
    <PageHeader
      eyebrow="RESEARCH · AUDIT LEDGER"
      title="操作日志"
      subtitle="只读记录知识点、模板与教学流程的关键操作。这里保留操作元数据，但不展示成绩、答案、订正内容或学生个人疑问。"
    >
      <template #actions>
        <button class="btn btn--ghost btn--sm" type="button" :disabled="loading" @click="load">
          <AppIcon name="refresh" :size="14" />
          刷新记录
        </button>
      </template>
    </PageHeader>

    <section class="audit-band">
      <div class="audit-seal">
        <AppIcon name="lock" :size="14" />
        <span>READ ONLY</span>
      </div>
      <div class="audit-band__copy">
        <b>审计视图已做数据裁剪</b>
        <p>
          学生个人学习操作不进入本页；批改导入只保留“发生过操作”的事实，不展示分数与逐题结果。
        </p>
      </div>
      <div class="audit-metrics">
        <div>
          <span>可见记录</span>
          <b>{{ visibleLogs.length }}</b>
        </div>
        <div>
          <span>口径治理</span>
          <b>{{ governanceCount }}</b>
        </div>
        <div>
          <span>教学操作</span>
          <b>{{ teachingCount }}</b>
        </div>
        <div>
          <span>登录</span>
          <b>{{ authCount }}</b>
        </div>
      </div>
      <span class="audit-watermark" aria-hidden="true">LOG</span>
    </section>

    <div v-if="hiddenCount" class="trim-note">
      <AppIcon name="shield" :size="13" />
      本次加载中有 {{ hiddenCount }} 条与学生个人学习相关的记录已按边界隐藏。
    </div>

    <div class="filter-card">
      <div class="filter-fields">
        <div class="filter-field">
          <label for="log-action">操作类型</label>
          <select id="log-action" v-model="actionFilter" class="select">
            <option value="">全部操作</option>
            <option v-for="option in actionOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </div>
        <div class="filter-field">
          <label for="log-target">对象类型</label>
          <select id="log-target" v-model="targetFilter" class="select">
            <option value="">全部对象</option>
            <option v-for="option in targetOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </div>
        <div class="filter-field">
          <label for="log-actor">操作人</label>
          <select id="log-actor" v-model="actorFilter" class="select">
            <option value="">全部操作人</option>
            <option v-for="actor in actorOptions" :key="actor.id" :value="actor.id">
              {{ actor.name }}
            </option>
          </select>
        </div>
      </div>
      <div class="filter-side">
        <button v-if="filtersActive" class="btn btn--quiet btn--sm" type="button" @click="resetFilters">
          <AppIcon name="x" :size="13" />
          清除筛选
        </button>
        <span class="filter-count">{{ filteredLogs.length }} 条匹配</span>
      </div>
    </div>

    <div v-if="loading" class="card loading-card">
      <span class="skeleton" style="width: 240px; height: 10px" />
    </div>

    <div v-else-if="error" class="card">
      <AppEmpty icon="alert-circle" title="日志加载失败" :description="error">
        <button class="btn btn--ghost btn--sm" @click="load">重试</button>
      </AppEmpty>
    </div>

    <div v-else class="grid grid--main-side logs-layout">
      <section class="card card-flush ledger-card">
        <header class="ledger-head">
          <div>
            <h2 class="section-title">操作台账</h2>
            <p class="section-desc">按时间倒序排列，记录不可修改或删除。</p>
          </div>
          <span class="ledger-mode">
            <span class="ledger-mode__dot" />
            只读
          </span>
        </header>

        <div v-if="pageItems.length" class="ledger">
          <div class="ledger-columns" aria-hidden="true">
            <span>时间</span>
            <span>操作与摘要</span>
            <span>对象</span>
            <span>操作人</span>
            <span />
          </div>
          <article v-for="log in pageItems" :key="log.id" class="ledger-row">
            <div class="log-time">
              <b>{{ formatDateTime(log.createdAt) }}</b>
              <span>{{ formatRelative(log.createdAt) }}</span>
            </div>
            <div class="log-action">
              <StatusBadge
                :status="log.action"
                :label="actionLabel(log.action)"
                :tone="actionTone(log.action)"
                plain
              />
              <p>{{ researchLogSummary(log) }}</p>
            </div>
            <div class="log-target">
              <span class="target-type">{{ targetLabel(log.targetType) }}</span>
              <em v-if="log.targetId" class="mono">#{{ log.targetId }}</em>
            </div>
            <div class="log-actor">
              <span>{{ actorLabel(log) }}</span>
              <small>{{ roleLabel(log) }}</small>
            </div>
            <button class="detail-button" type="button" aria-label="查看操作详情" @click="openDetail(log)">
              <AppIcon name="chevron-right" :size="14" />
            </button>
          </article>
        </div>

        <AppEmpty
          v-else
          icon="list"
          :title="filtersActive ? '没有匹配的日志' : '暂无可展示的日志'"
          :description="
            filtersActive
              ? '调整筛选条件，或清除筛选查看全部可见记录。'
              : '知识点、模板与教学流程的关键操作会自动记录在这里。'
          "
        >
          <button v-if="filtersActive" class="btn btn--ghost btn--sm" type="button" @click="resetFilters">
            清除筛选
          </button>
        </AppEmpty>

        <footer v-if="filteredLogs.length" class="ledger-foot">
          <span>
            第 {{ page }} / {{ pageCount }} 页
            <span class="foot-sep" />
            每页 {{ PAGE_SIZE }} 条
            <span v-if="truncated"> · 仅加载前 2000 条，请缩小筛选范围</span>
          </span>
          <div class="pager">
            <button type="button" :disabled="page <= 1" @click="page -= 1">
              <AppIcon name="chevron-left" :size="14" />
            </button>
            <button type="button" :disabled="page >= pageCount" @click="page += 1">
              <AppIcon name="chevron-right" :size="14" />
            </button>
          </div>
        </footer>
      </section>

      <aside class="stack">
        <section class="card boundary-card">
          <span class="boundary-icon"><AppIcon name="shield" :size="19" /></span>
          <h2>这份台账不包含</h2>
          <ul>
            <li><AppIcon name="x" :size="11" />学生姓名与个人学习轨迹</li>
            <li><AppIcon name="x" :size="11" />成绩、排名、逐题批改结果</li>
            <li><AppIcon name="x" :size="11" />订正内容与疑问正文</li>
            <li><AppIcon name="x" :size="11" />AI 草稿与教师审核细节</li>
          </ul>
          <p>如需处理学生个人数据，请由本班教师或系统管理员按权限操作。</p>
        </section>

        <section class="card trace-card">
          <span class="eyebrow">TRACEABILITY</span>
          <h2>每一条记录都能追溯</h2>
          <div class="trace-list">
            <div>
              <AppIcon name="clock" :size="14" />
              <span><b>发生时间</b>精确到操作完成时刻</span>
            </div>
            <div>
              <AppIcon name="user" :size="14" />
              <span><b>执行人</b>账号与角色</span>
            </div>
            <div>
              <AppIcon name="target" :size="14" />
              <span><b>操作对象</b>类型与对象编号</span>
            </div>
            <div>
              <AppIcon name="file" :size="14" />
              <span><b>操作摘要</b>关键动作与结果</span>
            </div>
          </div>
          <p class="trace-note">
            <AppIcon name="lock" :size="12" />
            日志由服务端写入，前端不提供修改或删除入口。
          </p>
        </section>

        <section class="card tip-card">
          <AppIcon name="info" :size="16" />
          <p>教研视图只用于口径维护与审计。班级教学数据请回到教师工作台查看。</p>
        </section>
      </aside>
    </div>

    <AppModal
      v-model="detailOpen"
      title="操作详情"
      :subtitle="selectedLog ? `日志 #${selectedLog.id}` : ''"
      size="md"
    >
      <template v-if="selectedLog">
        <div class="detail-head">
          <StatusBadge
            :status="selectedLog.action"
            :label="actionLabel(selectedLog.action)"
            :tone="actionTone(selectedLog.action)"
          />
          <span class="detail-time">{{ formatDateTime(selectedLog.createdAt) }}</span>
        </div>

        <p class="detail-summary">{{ researchLogSummary(selectedLog) }}</p>

        <dl class="detail-grid">
          <div>
            <dt>操作人</dt>
            <dd>{{ actorLabel(selectedLog) }} · {{ roleLabel(selectedLog) }}</dd>
          </div>
          <div>
            <dt>对象</dt>
            <dd>
              {{ targetLabel(selectedLog.targetType) }}
              <span v-if="selectedLog.targetId" class="mono">#{{ selectedLog.targetId }}</span>
            </dd>
          </div>
          <div>
            <dt>完整时间</dt>
            <dd>{{ formatDateTime(selectedLog.createdAt) }}</dd>
          </div>
          <div>
            <dt>来源 IP</dt>
            <dd class="mono">{{ selectedLog.ip || '—' }}</dd>
          </div>
        </dl>

        <div v-if="selectedEntries.length" class="detail-extra">
          <span class="paper-label">附加元数据</span>
          <div class="extra-list">
            <div v-for="entry in selectedEntries" :key="entry.label" class="extra-row">
              <span class="mono">{{ entry.label }}</span>
              <p>{{ entry.value }}</p>
            </div>
          </div>
        </div>

        <div class="info-banner detail-note">
          <AppIcon name="shield" :size="16" />
          只读审计记录。内容已按教研权限裁剪，不包含成绩、答案或学生个人学习正文。
        </div>
      </template>

      <template #footer>
        <button class="btn btn--ghost" type="button" @click="detailOpen = false">关闭</button>
      </template>
    </AppModal>
  </div>
</template>

<style scoped>
.logs-page {
  display: flex;
  flex-direction: column;
}

.audit-band {
  position: relative;
  display: flex;
  align-items: center;
  gap: 17px;
  margin-bottom: 11px;
  padding: 16px 22px;
  overflow: hidden;
  border: 1px solid #e8e0d3;
  border-radius: 17px;
  background:
    repeating-linear-gradient(90deg, transparent 0 39px, rgba(120, 109, 92, 0.035) 39px 40px),
    linear-gradient(105deg, #fffdf8, #f8f3e9);
}

.audit-seal {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex: none;
  padding: 5px 9px;
  border: 1px solid #ded0ba;
  border-radius: 4px;
  color: #9e7b49;
  background: rgba(255, 255, 255, 0.6);
  font-family: var(--font-mono);
  font-size: 7.5px;
  letter-spacing: 0.12em;
}

.audit-band__copy {
  min-width: 0;
  flex: 1;
}

.audit-band__copy b {
  display: block;
  color: #524634;
  font-family: var(--font-serif);
  font-size: 13px;
}

.audit-band__copy p {
  margin-top: 2px;
  color: #93826b;
  font-size: 9.5px;
  line-height: 1.65;
}

.audit-metrics {
  display: flex;
  gap: 21px;
  flex: none;
  padding-left: 20px;
  border-left: 1px dashed #dfd1bc;
}

.audit-metrics div {
  text-align: center;
}

.audit-metrics span {
  display: block;
  color: #a2917a;
  font-size: 7.5px;
}

.audit-metrics b {
  display: block;
  margin-top: 1px;
  color: #5e4d37;
  font-family: var(--font-numbers);
  font-size: 20px;
  font-weight: 600;
  line-height: 1.2;
}

.audit-watermark {
  position: absolute;
  right: 12px;
  bottom: -17px;
  color: rgba(110, 95, 72, 0.045);
  font-family: var(--font-numbers);
  font-size: 76px;
  line-height: 1;
}

.trim-note {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-bottom: 11px;
  padding: 0 5px;
  color: var(--ink-4);
  font-size: 8.5px;
}

.trim-note .icon {
  color: var(--apricot-deep);
  opacity: 0.7;
}

.filter-card {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 13px;
  padding: 13px 15px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.8);
  box-shadow: var(--shadow-xs);
}

.filter-fields {
  display: flex;
  gap: 10px;
  flex: 1;
  flex-wrap: wrap;
}

.filter-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: min(180px, 100%);
}

.filter-field label {
  color: var(--ink-3);
  font-size: 8.5px;
  font-weight: 600;
}

.filter-field .select {
  height: 34px;
  border-radius: 9px;
  font-size: 11px;
}

.filter-side {
  display: flex;
  align-items: center;
  gap: 9px;
  flex: none;
}

.filter-count {
  color: var(--ink-4);
  font-size: 9px;
  white-space: nowrap;
}

.logs-layout {
  align-items: start;
}

.ledger-card {
  overflow: hidden;
}

.ledger-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding: 19px 20px 14px;
  border-bottom: 1px solid var(--line);
}

.ledger-mode {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex: none;
  padding: 3px 8px;
  border: 1px solid #ece6dc;
  border-radius: 999px;
  color: #9d8a71;
  background: #fdfaf4;
  font-family: var(--font-mono);
  font-size: 7.5px;
  letter-spacing: 0.08em;
}

.ledger-mode__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--apricot);
}

.ledger-columns,
.ledger-row {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr) 88px 96px 24px;
  align-items: center;
  gap: 12px;
}

.ledger-columns {
  padding: 9px 20px;
  border-bottom: 1px solid var(--line);
  color: var(--ink-4);
  background: #fbfdfc;
  font-family: var(--font-mono);
  font-size: 7.5px;
  letter-spacing: 0.07em;
}

.ledger-row {
  padding: 12px 20px;
  border-bottom: 1px solid #edf3f0;
  transition: background 0.18s;
}

.ledger-row:last-of-type {
  border-bottom: 0;
}

.ledger-row:hover {
  background: #fdfcf8;
}

.log-time b {
  display: block;
  color: var(--ink-3);
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 500;
  white-space: nowrap;
}

.log-time span {
  display: block;
  margin-top: 1px;
  color: var(--ink-4);
  font-size: 7.5px;
}

.log-action {
  min-width: 0;
}

.log-action :deep(.badge) {
  min-height: 19px;
  padding: 0 7px;
  font-size: 8px;
}

.log-action p {
  margin-top: 4px;
  overflow: hidden;
  color: var(--ink-2);
  font-size: 10.5px;
  line-height: 1.55;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.target-type {
  display: inline-block;
  padding: 2px 6px;
  border-radius: 5px;
  color: #7e7869;
  background: #f4f3ed;
  font-size: 8.5px;
  white-space: nowrap;
}

.log-target em {
  display: inline-block;
  margin-left: 3px;
  color: var(--ink-4);
  font-size: 8px;
  font-style: normal;
}

.log-actor span {
  display: block;
  overflow: hidden;
  color: var(--ink-2);
  font-size: 9.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.log-actor small {
  display: block;
  margin-top: 1px;
  color: var(--ink-4);
  font-size: 7.5px;
}

.detail-button {
  display: grid;
  width: 23px;
  height: 23px;
  place-items: center;
  border-radius: 50%;
  color: var(--ink-4);
  transition: 0.18s;
}

.detail-button:hover {
  color: var(--apricot-deep);
  background: var(--apricot-soft);
}

.ledger-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 11px 20px;
  border-top: 1px solid var(--line);
  color: var(--ink-4);
  font-size: 8.5px;
}

.foot-sep {
  display: inline-block;
  width: 3px;
  height: 3px;
  margin: 0 4px;
  border-radius: 50%;
  background: currentColor;
  vertical-align: middle;
}

.pager {
  display: flex;
  gap: 3px;
}

.pager button {
  display: grid;
  width: 26px;
  height: 26px;
  place-items: center;
  border: 1px solid var(--line);
  border-radius: 8px;
  color: var(--ink-3);
  background: #fff;
  transition: 0.18s;
}

.pager button:hover:not(:disabled) {
  color: var(--apricot-deep);
  border-color: #ebd8bb;
  background: #fdf9f1;
}

.pager button:disabled {
  opacity: 0.35;
}

.ledger-card :deep(.empty-state) {
  padding: 44px 20px;
}

.boundary-card {
  padding: 19px;
  background:
    radial-gradient(circle at 100% 0, rgba(214, 160, 95, 0.08), transparent 43%),
    #fff;
}

.boundary-icon {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  margin-bottom: 9px;
  border-radius: 11px;
  color: var(--apricot-deep);
  background: var(--apricot-soft);
}

.boundary-card h2 {
  font-family: var(--font-serif);
  font-size: 14px;
  font-weight: 700;
}

.boundary-card ul {
  display: flex;
  flex-direction: column;
  gap: 7px;
  margin-top: 11px;
}

.boundary-card li {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  color: var(--ink-2);
  font-size: 9.5px;
  line-height: 1.55;
}

.boundary-card li .icon {
  flex: none;
  margin-top: 1px;
  color: var(--coral);
}

.boundary-card > p {
  margin-top: 13px;
  padding-top: 11px;
  border-top: 1px dashed var(--line);
  color: var(--ink-3);
  font-size: 8.5px;
  line-height: 1.7;
}

.trace-card {
  padding: 19px;
}

.trace-card .eyebrow {
  margin-bottom: 6px;
  font-size: 8.5px;
}

.trace-card h2 {
  font-family: var(--font-display);
  font-size: 21px;
  font-weight: 400;
  letter-spacing: 0.02em;
}

.trace-list {
  display: flex;
  flex-direction: column;
  gap: 9px;
  margin-top: 13px;
}

.trace-list div {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  color: var(--ink-3);
  font-size: 8.5px;
  line-height: 1.55;
}

.trace-list .icon {
  flex: none;
  margin-top: 1px;
  color: var(--apricot);
}

.trace-list b {
  margin-right: 4px;
  color: var(--ink-2);
  font-size: 9px;
}

.trace-note {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  margin-top: 13px;
  padding-top: 11px;
  border-top: 1px dashed var(--line);
  color: var(--ink-4);
  font-size: 8px;
  line-height: 1.6;
}

.trace-note .icon {
  flex: none;
  margin-top: 1px;
}

.tip-card {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 13px 15px;
  border-color: #ede4d5;
  background: #fbf8f1;
  box-shadow: none;
}

.tip-card .icon {
  flex: none;
  margin-top: 1px;
  color: var(--apricot-deep);
}

.tip-card p {
  color: #8c7b64;
  font-size: 8.5px;
  line-height: 1.7;
}

.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.detail-time {
  color: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 9px;
}

.detail-summary {
  margin-top: 12px;
  padding: 12px 13px;
  border-left: 2px solid var(--apricot);
  border-radius: 0 8px 8px 0;
  color: var(--ink-2);
  background: #fcfaf4;
  font-size: 11.5px;
  line-height: 1.8;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 16px;
}

.detail-grid dt {
  color: var(--ink-4);
  font-size: 8.5px;
}

.detail-grid dd {
  margin-top: 2px;
  color: var(--ink-2);
  font-size: 10.5px;
}

.detail-extra {
  margin-top: 17px;
  padding-top: 14px;
  border-top: 1px dashed var(--line);
}

.extra-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 8px;
}

.extra-row {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--surface-soft);
}

.extra-row > span {
  width: 110px;
  flex: none;
  color: var(--ink-4);
  font-size: 8.5px;
}

.extra-row p {
  color: var(--ink-2);
  font-size: 9.5px;
  line-height: 1.6;
  word-break: break-word;
}

.detail-note {
  margin-top: 16px;
}

@media (max-width: 1080px) {
  .audit-band__copy p {
    display: none;
  }

  .audit-metrics {
    gap: 15px;
  }

  .ledger-columns,
  .ledger-row {
    grid-template-columns: 78px minmax(0, 1fr) 76px 82px 22px;
    gap: 8px;
    padding-right: 14px;
    padding-left: 14px;
  }

  .ledger-columns {
    font-size: 7px;
  }
}

@media (max-width: 820px) {
  .audit-band {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .audit-metrics {
    width: 100%;
    justify-content: space-between;
    padding-top: 10px;
    padding-left: 0;
    border-top: 1px dashed #dfd1bc;
    border-left: 0;
  }

  .filter-card {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-field {
    flex: 1;
    width: auto;
    min-width: 130px;
  }

  .filter-side {
    justify-content: space-between;
  }

  .ledger-columns {
    display: none;
  }

  .ledger-row {
    grid-template-columns: 1fr auto;
    gap: 4px 10px;
    padding: 12px 15px;
  }

  .log-time {
    grid-column: 1;
    grid-row: 1;
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .log-time span {
    margin-top: 0;
  }

  .log-action {
    grid-column: 1 / -1;
    grid-row: 2;
  }

  .log-target {
    grid-column: 1;
    grid-row: 3;
  }

  .log-actor {
    grid-column: 2;
    grid-row: 3;
    text-align: right;
  }

  .detail-button {
    grid-column: 2;
    grid-row: 1;
    justify-self: end;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }

  .extra-row {
    flex-direction: column;
    gap: 2px;
  }

  .extra-row > span {
    width: auto;
  }
}

@media (max-width: 560px) {
  .audit-band {
    padding: 15px;
  }

  .audit-band__copy b {
    font-size: 12px;
  }

  .audit-metrics b {
    font-size: 18px;
  }

  .filter-field {
    min-width: 100%;
  }

  .ledger-head {
    padding: 16px;
  }

  .ledger-foot {
    padding: 10px 15px;
  }
}
</style>
