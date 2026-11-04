<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { studentApi } from '@/api/student'
import { errorMessage } from '@/api/errors'
import type { CorrectionRecord, StudentHandoutSummary } from '@/types/models'
import { useUiStore } from '@/stores/ui'
import { formatDateTime, formatRelative } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'
import AppIcon from '@/components/AppIcon.vue'
import AppEmpty from '@/components/AppEmpty.vue'
import AppPagination from '@/components/AppPagination.vue'
import { fetchAllPages } from '@/utils/pagination'

const PAGE_SIZE = 10

const router = useRouter()
const ui = useUiStore()

const loading = ref(true)
const error = ref('')
const records = ref<CorrectionRecord[]>([])
const handouts = ref<StudentHandoutSummary[]>([])
const search = ref('')
const listPage = ref(1)
const truncated = ref(false)

const sortedRecords = computed(() =>
  [...records.value].sort((a, b) => b.submittedAt.localeCompare(a.submittedAt)),
)
const filteredRecords = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return sortedRecords.value.filter(
    (record) =>
      !keyword ||
      String(record.seqNo).includes(keyword) ||
      (record.content ?? '').toLowerCase().includes(keyword) ||
      homeworkTitle(record.homeworkId).toLowerCase().includes(keyword),
  )
})
const pagedRecords = computed(() => {
  const start = (listPage.value - 1) * PAGE_SIZE
  return filteredRecords.value.slice(start, start + PAGE_SIZE)
})
const distinctQuestions = computed(() => new Set(records.value.map((record) => record.questionId)).size)
const latestRecord = computed(() => sortedRecords.value[0] ?? null)
const homeworkMap = computed(() => {
  const map = new Map<number, StudentHandoutSummary>()
  for (const handout of handouts.value) map.set(handout.homeworkId, handout)
  return map
})

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [correctionResult, handoutList] = await Promise.all([
      fetchAllPages((page, pageSize) => studentApi.listMyCorrections({ page, pageSize })),
      studentApi.listMyHandouts(),
    ])
    records.value = correctionResult.items
    truncated.value = correctionResult.truncated
    listPage.value = 1
    handouts.value = handoutList
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

function homeworkTitle(homeworkId: number): string {
  return homeworkMap.value.get(homeworkId)?.homeworkTitle ?? `作业 #${homeworkId}`
}

function openHandout(homeworkId: number): void {
  const handout = homeworkMap.value.get(homeworkId)
  if (!handout) {
    ui.toast('info', '讲评已不在可见范围', '老师可能已撤回该讲评，已提交的订正仍会保留。')
    return
  }
  void router.push(`/student/handouts/${handout.id}`)
}

watch(search, () => {
  listPage.value = 1
})

onMounted(load)
</script>

<template>
  <div class="student-corrections-page">
    <PageHeader
      eyebrow="STUDENT · MY CORRECTIONS"
      title="我的订正"
      subtitle="订正提交即记录，是否掌握由老师结合课堂判断。同一道题可以补充多次，历史不会被覆盖。"
    >
      <template #actions>
        <RouterLink to="/student/handouts" class="btn btn--ghost btn--sm">
          <AppIcon name="book-open" :size="14" />
          回到讲评
        </RouterLink>
      </template>
    </PageHeader>

    <div v-if="loading" class="card loading-card">
      <span class="skeleton" style="width: 220px; height: 10px" />
    </div>

    <div v-else-if="error" class="card">
      <AppEmpty icon="alert-circle" title="订正记录加载失败" :description="error">
        <button class="btn btn--ghost btn--sm" @click="load">重试</button>
      </AppEmpty>
    </div>

    <template v-else>
      <div class="correction-overview">
        <div class="overview-copy">
          <span class="overview-kicker">CORRECTION JOURNAL</span>
          <h2>{{ records.length ? '每一次重做，都留下思考的痕迹' : '还没有提交过订正' }}</h2>
          <p>
            共 {{ records.length }} 次提交，覆盖 {{ distinctQuestions }} 道题。
            这里只有你的记录，没有同学对比。
          </p>
        </div>
        <div class="overview-metrics">
          <div><span>提交次数</span><b>{{ records.length }}<small> 次</small></b></div>
          <div><span>涉及错题</span><b>{{ distinctQuestions }}<small> 题</small></b></div>
          <div><span>最近提交</span><b class="date">{{ latestRecord ? formatRelative(latestRecord.submittedAt) : '—' }}</b></div>
        </div>
        <span class="journal-mark">MY<br />FIX</span>
      </div>

      <div class="correction-toolbar">
        <h3 class="section-title">订正时间线</h3>
        <label class="search-box">
          <AppIcon name="search" :size="15" />
          <input v-model="search" type="search" placeholder="搜索题目、讲评或订正说明…" />
        </label>
      </div>

      <div v-if="filteredRecords.length" class="correction-timeline">
        <div class="timeline-line" aria-hidden="true" />
        <article v-for="(record, index) in pagedRecords" :key="record.id" class="timeline-entry">
          <div class="timeline-marker">
            <span>{{ String(filteredRecords.length - ((listPage - 1) * PAGE_SIZE + index)).padStart(2, '0') }}</span>
          </div>
          <div class="correction-slip">
            <div class="slip-head">
              <div>
                <span class="slip-homework">{{ homeworkTitle(record.homeworkId) }}</span>
                <h4>第 {{ record.seqNo }} 题订正</h4>
              </div>
              <div class="slip-tags">
                <span class="badge" data-tone="peri">V{{ record.versionNo }}</span>
                <span class="slip-time">{{ formatDateTime(record.submittedAt) }}</span>
              </div>
            </div>
            <div class="slip-body">
              <div class="slip-answer">
                <span>重选答案</span>
                <b>{{ record.selectedAnswer || '未重选' }}</b>
              </div>
              <div class="slip-note">
                <span>订正说明</span>
                <p>{{ record.content || '这次只重新选择了答案，没有填写说明。' }}</p>
              </div>
            </div>
            <div class="slip-foot">
              <span><AppIcon name="check-circle" :size="12" />已记录 · 掌握由老师判断</span>
              <button type="button" @click="openHandout(record.homeworkId)">
                打开对应讲评
                <AppIcon name="arrow-right" :size="12" />
              </button>
            </div>
          </div>
        </article>
        <AppPagination v-model:page="listPage" :page-size="PAGE_SIZE" :total="filteredRecords.length" />
      </div>

      <section v-else-if="!records.length" class="card">
        <AppEmpty
          icon="pen"
          title="还没有订正记录"
          description="在老师发布的错题讲评里重新选择答案、写下这次的想法，提交后会记录在这里。"
        >
          <RouterLink to="/student/handouts" class="btn btn--primary btn--sm">去看我的讲评</RouterLink>
        </AppEmpty>
      </section>

      <AppEmpty
        v-else
        icon="search"
        title="没有匹配的订正记录"
        description="换一个搜索词试试。"
      />

      <div class="correction-footnote">
        <AppIcon name="info" :size="13" />
        “订正完成”只代表你提交了订正记录；系统不会自动给你贴“已掌握”标签，也不会把记录展示给其他同学。
        <template v-if="truncated">当前记录量超过加载上限，仅展示前 2000 条。</template>
      </div>
    </template>
  </div>
</template>

<style scoped>
.student-corrections-page {
  max-width: 1080px;
}

.correction-overview {
  position: relative;
  display: flex;
  align-items: center;
  gap: 22px;
  margin-bottom: 16px;
  padding: 21px 24px;
  overflow: hidden;
  border: 1px solid #dde6fb;
  border-radius: 20px;
  background:
    radial-gradient(circle at 88% 0, rgba(255, 255, 255, 0.95), transparent 34%),
    linear-gradient(120deg, #fdfefe, #eff4ff 65%, #f7f3fd);
}

.overview-copy {
  min-width: 0;
  flex: 1;
}

.overview-kicker {
  color: var(--peri-deep);
  font-family: var(--font-mono);
  font-size: 7.5px;
  letter-spacing: 0.13em;
}

.overview-copy h2 {
  margin-top: 5px;
  font-family: var(--font-serif);
  font-size: clamp(18px, 2vw, 23px);
  font-weight: 600;
  line-height: 1.45;
}

.overview-copy p {
  margin-top: 4px;
  color: var(--ink-3);
  font-size: 9px;
  line-height: 1.65;
}

.overview-metrics {
  display: flex;
  gap: 19px;
  flex: none;
  padding-left: 19px;
  border-left: 1px dashed rgba(101, 122, 186, 0.22);
}

.overview-metrics span {
  display: block;
  color: var(--ink-3);
  font-size: 7.5px;
}

.overview-metrics b {
  display: block;
  margin-top: 1px;
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 20px;
  font-weight: 600;
  line-height: 1.2;
}

.overview-metrics b small {
  color: var(--ink-3);
  font-family: var(--font-body);
  font-size: 7.5px;
  font-weight: 400;
}

.overview-metrics b.date {
  font-family: var(--font-body);
  font-size: 9.5px;
  font-weight: 500;
}

.journal-mark {
  flex: none;
  color: rgba(107, 140, 245, 0.25);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.12em;
  line-height: 1.5;
  text-align: center;
  transform: rotate(-6deg);
}

.correction-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 230px;
  height: 33px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: 99px;
  color: var(--ink-3);
  background: rgba(255, 255, 255, 0.8);
}

.search-box input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  font-size: 9.5px;
}

.correction-timeline {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.timeline-line {
  position: absolute;
  top: 18px;
  bottom: 18px;
  left: 15px;
  width: 1px;
  background: linear-gradient(to bottom, #d6e0fb, #e9e4f6, transparent);
}

.timeline-entry {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.timeline-marker {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 31px;
  height: 31px;
  flex: none;
  border: 3px solid var(--bg);
  border-radius: 50%;
  color: var(--peri-deep);
  background: var(--peri-soft);
}

.timeline-marker span {
  font-family: var(--font-numbers);
  font-size: 8px;
}

.correction-slip {
  flex: 1;
  min-width: 0;
  padding: 14px 16px;
  border: 1px solid var(--line);
  border-radius: 13px;
  background: #fff;
  box-shadow: var(--shadow-xs);
  transition: 0.18s;
}

.correction-slip:hover {
  border-color: color-mix(in srgb, var(--peri) 20%, var(--line));
  box-shadow: var(--shadow-sm);
}

.slip-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.slip-homework {
  color: var(--ink-4);
  font-size: 7.5px;
}

.slip-head h4 {
  margin-top: 2px;
  font-family: var(--font-serif);
  font-size: 12px;
  font-weight: 600;
}

.slip-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: none;
}

.slip-tags :deep(.badge) {
  min-height: 18px;
  padding: 0 6px;
  font-size: 7.5px;
}

.slip-time {
  color: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 7px;
}

.slip-body {
  display: grid;
  grid-template-columns: 100px minmax(0, 1fr);
  gap: 12px;
  margin-top: 10px;
  padding-top: 9px;
  border-top: 1px dashed var(--line);
}

.slip-answer span,
.slip-note span {
  display: block;
  color: var(--ink-4);
  font-size: 7.5px;
}

.slip-answer b {
  display: inline-block;
  margin-top: 3px;
  padding: 2px 8px;
  border-radius: 5px;
  color: var(--peri-deep);
  background: var(--peri-soft);
  font-family: var(--font-serif);
  font-size: 10px;
  font-weight: 600;
}

.slip-note p {
  margin-top: 2px;
  color: var(--ink-2);
  font-size: 9px;
  line-height: 1.7;
}

.slip-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 9px;
  padding-top: 7px;
  border-top: 1px dashed var(--line);
}

.slip-foot > span {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--mint-deep);
  font-size: 7.5px;
}

.slip-foot button {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--role-deep);
  font-size: 8px;
  font-weight: 600;
}

.correction-footnote {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  margin-top: 12px;
  padding: 0 4px;
  color: var(--ink-4);
  font-size: 8.5px;
  line-height: 1.6;
}

.correction-footnote .icon {
  flex: none;
  margin-top: 1px;
}

@media (max-width: 760px) {
  .correction-overview {
    align-items: flex-start;
    flex-direction: column;
    gap: 13px;
    padding: 17px;
  }

  .overview-metrics {
    width: 100%;
    justify-content: space-between;
    padding-top: 10px;
    padding-left: 0;
    border-top: 1px dashed rgba(101, 122, 186, 0.22);
    border-left: 0;
  }

  .journal-mark {
    display: none;
  }

  .search-box {
    width: 100%;
  }

  .slip-body {
    grid-template-columns: 1fr;
    gap: 7px;
  }

  .timeline-marker {
    width: 27px;
    height: 27px;
  }

  .timeline-line {
    left: 13px;
  }
}
</style>
