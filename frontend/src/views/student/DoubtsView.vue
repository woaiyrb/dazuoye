<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { studentApi } from '@/api/student'
import { errorMessage } from '@/api/errors'
import type { Doubt, StudentHandoutSummary } from '@/types/models'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime, formatRelative } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import AppIcon from '@/components/AppIcon.vue'
import AppEmpty from '@/components/AppEmpty.vue'
import AppPagination from '@/components/AppPagination.vue'
import { fetchAllPages } from '@/utils/pagination'

const PAGE_SIZE = 10

const auth = useAuthStore()
const router = useRouter()

const loading = ref(true)
const error = ref('')
const doubts = ref<Doubt[]>([])
const handouts = ref<StudentHandoutSummary[]>([])
const filter = ref<'ALL' | 'PENDING' | 'REPLIED'>('ALL')
const listPage = ref(1)
const truncated = ref(false)

const counts = computed(() => ({
  ALL: doubts.value.length,
  PENDING: doubts.value.filter((doubt) => doubt.status === 'PENDING').length,
  REPLIED: doubts.value.filter((doubt) => doubt.status !== 'PENDING').length,
}))
const filteredDoubts = computed(() =>
  [...doubts.value]
    .filter((doubt) => filter.value === 'ALL' || doubt.status === filter.value)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
)
const pagedDoubts = computed(() => {
  const start = (listPage.value - 1) * PAGE_SIZE
  return filteredDoubts.value.slice(start, start + PAGE_SIZE)
})
const homeworkMap = computed(() => {
  const map = new Map<number, StudentHandoutSummary>()
  for (const handout of handouts.value) map.set(handout.homeworkId, handout)
  return map
})

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [doubtResult, handoutList] = await Promise.all([
      fetchAllPages((page, pageSize) => studentApi.listMyDoubts({ page, pageSize })),
      studentApi.listMyHandouts(),
    ])
    doubts.value = doubtResult.items
    truncated.value = doubtResult.truncated
    listPage.value = 1
    handouts.value = handoutList
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

function homeworkTitle(doubt: Doubt): string {
  if (doubt.homeworkTitle) return doubt.homeworkTitle
  if (doubt.homeworkId) {
    return homeworkMap.value.get(doubt.homeworkId)?.homeworkTitle ?? `作业 #${doubt.homeworkId}`
  }
  return '已发布讲评'
}

function openHandout(doubt: Doubt): void {
  const homeworkId = doubt.homeworkId
  if (!homeworkId) {
    void router.push('/student/handouts')
    return
  }
  const handout = homeworkMap.value.get(homeworkId)
  if (handout) void router.push(`/student/handouts/${handout.id}`)
  else void router.push('/student/handouts')
}

watch(filter, () => {
  listPage.value = 1
})

onMounted(load)
</script>

<template>
  <div class="student-doubts-page">
    <PageHeader
      eyebrow="STUDENT · MY QUESTIONS"
      title="我的疑问"
      subtitle="把卡住的那一步问清楚。疑问只有你和本班老师可见，老师的回复会出现在这里。"
    >
      <template #actions>
        <RouterLink to="/student/handouts" class="btn btn--primary btn--sm">
          <AppIcon name="plus" :size="14" />
          去讲评里提问
        </RouterLink>
      </template>
    </PageHeader>

    <div v-if="loading" class="card loading-card">
      <span class="skeleton" style="width: 220px; height: 10px" />
    </div>

    <div v-else-if="error" class="card">
      <AppEmpty icon="alert-circle" title="疑问加载失败" :description="error">
        <button class="btn btn--ghost btn--sm" @click="load">重试</button>
      </AppEmpty>
    </div>

    <template v-else>
      <div class="question-overview">
        <div class="overview-copy">
          <span class="overview-kicker">ASK · CLARIFY · UNDERSTAND</span>
          <h2>{{ counts.PENDING ? '有疑问在等老师回复' : '把不明白的地方问出来' }}</h2>
          <p>问题越具体，老师越容易回复。可以指出“哪一步”不理解，而不是只说“不会”。</p>
        </div>
        <div class="overview-counts">
          <div><span>全部疑问</span><b>{{ counts.ALL }}</b></div>
          <div><span>等待回复</span><b class="warm">{{ counts.PENDING }}</b></div>
          <div><span>已有回复</span><b>{{ counts.REPLIED }}</b></div>
        </div>
        <span class="question-mark" aria-hidden="true">?</span>
      </div>

      <div class="filter-row">
        <div class="segmented">
          <button type="button" :class="{ 'is-active': filter === 'ALL' }" @click="filter = 'ALL'">
            全部
          </button>
          <button type="button" :class="{ 'is-active': filter === 'PENDING' }" @click="filter = 'PENDING'">
            等老师回复
          </button>
          <button type="button" :class="{ 'is-active': filter === 'REPLIED' }" @click="filter = 'REPLIED'">
            已有回复
          </button>
        </div>
        <span class="filter-hint">仅本人提问 · 回复由老师确认</span>
      </div>

      <div v-if="filteredDoubts.length" class="doubt-list">
        <article v-for="doubt in pagedDoubts" :key="doubt.id" class="doubt-thread">
          <div class="thread-head">
            <span class="question-badge">Q{{ doubt.seqNo }}</span>
            <div class="thread-title">
              <b>关于第 {{ doubt.seqNo }} 题的疑问</b>
              <span>{{ homeworkTitle(doubt) }}</span>
            </div>
            <StatusBadge :status="doubt.status" />
            <button class="open-link" type="button" @click="openHandout(doubt)">
              打开讲评
              <AppIcon name="external" :size="12" />
            </button>
          </div>

          <div class="my-message">
            <span class="avatar avatar--sm">{{ auth.displayName.slice(0, 1) }}</span>
            <div>
              <div class="message-meta">
                <b>我</b>
                <span>{{ formatDateTime(doubt.createdAt) }}</span>
              </div>
              <p>{{ doubt.content }}</p>
            </div>
          </div>

          <div v-if="doubt.replies.length" class="reply-list">
            <div v-for="reply in doubt.replies" :key="reply.id" class="teacher-message">
              <span class="teacher-avatar">{{ (reply.teacherName || '王').slice(0, 1) }}</span>
              <div>
                <div class="message-meta">
                  <b>{{ reply.teacherName || '王老师' }}</b>
                  <span>老师 · {{ formatDateTime(reply.createdAt) }}</span>
                </div>
                <p>{{ reply.content }}</p>
              </div>
            </div>
          </div>
          <div v-else class="waiting-reply">
            <AppIcon name="clock" :size="13" />
            <span>已于 {{ formatRelative(doubt.createdAt) }}提交，等待老师回复。</span>
          </div>

          <div class="thread-foot">
            <span><AppIcon name="shield" :size="11" />你的疑问只有本人和本班老师可见</span>
            <button type="button" @click="openHandout(doubt)">
              还有问题？回到讲评继续问
              <AppIcon name="arrow-right" :size="11" />
            </button>
          </div>
        </article>
        <AppPagination v-model:page="listPage" :page-size="PAGE_SIZE" :total="filteredDoubts.length" />
      </div>

      <section v-else-if="!doubts.length" class="card">
        <AppEmpty
          icon="message"
          title="还没有提问"
          description="在老师发布的错题讲解里，点击“我还不懂，问老师”，把卡住的步骤写下来。"
        >
          <RouterLink to="/student/handouts" class="btn btn--primary btn--sm">去看我的讲评</RouterLink>
        </AppEmpty>
      </section>

      <AppEmpty
        v-else
        icon="message"
        title="当前筛选下没有疑问"
        description="切换筛选，查看全部提问。"
      />

      <div class="doubt-footnote">
        <AppIcon name="info" :size="13" />
        首版不开放学生 AI 自由问答。老师的回复是正式答复，AI 草稿不会直接展示给你。
        <template v-if="truncated">当前疑问量超过加载上限，仅展示前 2000 条。</template>
      </div>
    </template>
  </div>
</template>

<style scoped>
.student-doubts-page {
  max-width: 1020px;
}

.question-overview {
  position: relative;
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 14px;
  padding: 20px 23px;
  overflow: hidden;
  border: 1px solid #e7e1f6;
  border-radius: 20px;
  background:
    radial-gradient(circle at 90% 10%, rgba(255, 255, 255, 0.94), transparent 32%),
    linear-gradient(120deg, #fdfefe, #f1f5ff 60%, #f8f4fd);
}

.overview-copy {
  min-width: 0;
  flex: 1;
}

.overview-kicker {
  color: var(--lav-deep);
  font-family: var(--font-mono);
  font-size: 7px;
  letter-spacing: 0.13em;
}

.overview-copy h2 {
  margin-top: 4px;
  font-family: var(--font-serif);
  font-size: clamp(18px, 2vw, 22px);
  font-weight: 600;
}

.overview-copy p {
  margin-top: 3px;
  color: var(--ink-3);
  font-size: 9px;
  line-height: 1.65;
}

.overview-counts {
  display: flex;
  gap: 18px;
  flex: none;
  padding-left: 18px;
  border-left: 1px dashed rgba(101, 122, 186, 0.22);
}

.overview-counts span {
  display: block;
  color: var(--ink-3);
  font-size: 7.5px;
}

.overview-counts b {
  display: block;
  margin-top: 1px;
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 20px;
  font-weight: 600;
  line-height: 1.2;
}

.overview-counts b.warm {
  color: var(--coral-deep);
}

.question-mark {
  position: absolute;
  right: 15px;
  bottom: -28px;
  color: rgba(107, 140, 245, 0.06);
  font-family: var(--font-numbers);
  font-size: 130px;
  line-height: 1;
}

.filter-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 11px;
  flex-wrap: wrap;
}

.filter-hint {
  color: var(--ink-4);
  font-size: 8px;
}

.doubt-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.doubt-thread {
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 15px;
  background: #fff;
  box-shadow: var(--shadow-xs);
}

.thread-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 10px;
  border-bottom: 1px dashed var(--line);
}

.question-badge {
  display: grid;
  place-items: center;
  width: 29px;
  height: 29px;
  flex: none;
  border-radius: 8px;
  color: var(--peri-deep);
  background: var(--peri-soft);
  font-family: var(--font-numbers);
  font-size: 9px;
}

.thread-title {
  min-width: 0;
  flex: 1;
}

.thread-title b {
  display: block;
  color: var(--ink-2);
  font-size: 9.5px;
}

.thread-title span {
  display: block;
  margin-top: 1px;
  overflow: hidden;
  color: var(--ink-4);
  font-size: 7.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.thread-head :deep(.badge) {
  min-height: 19px;
  padding: 0 6px;
  font-size: 7.5px;
}

.open-link {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  flex: none;
  color: var(--role-deep);
  font-size: 8px;
  font-weight: 600;
}

.my-message,
.teacher-message {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 10px;
}

.my-message > div,
.teacher-message > div {
  min-width: 0;
  flex: 1;
}

.message-meta {
  display: flex;
  align-items: baseline;
  gap: 5px;
}

.message-meta b {
  color: var(--ink-2);
  font-size: 8.5px;
}

.message-meta span {
  color: var(--ink-4);
  font-size: 7px;
}

.my-message p {
  display: inline-block;
  margin-top: 3px;
  padding: 8px 11px;
  border-radius: 2px 10px 10px 10px;
  color: var(--ink-2);
  background: var(--surface-soft);
  font-size: 9.5px;
  line-height: 1.75;
}

.reply-list {
  margin-top: 5px;
  padding: 8px 10px;
  border-radius: 10px;
  background: #f7fcf9;
}

.teacher-avatar {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  flex: none;
  border-radius: 50%;
  color: var(--mint-deep);
  background: var(--mint-soft);
  font-family: var(--font-serif);
  font-size: 10px;
}

.teacher-message {
  margin-top: 3px;
}

.teacher-message p {
  margin-top: 2px;
  color: #426356;
  font-size: 9.5px;
  line-height: 1.8;
}

.waiting-reply {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  padding: 7px 9px;
  border-radius: 7px;
  color: var(--ink-4);
  background: var(--surface-soft);
  font-size: 8px;
}

.thread-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 9px;
  padding-top: 8px;
  border-top: 1px dashed var(--line);
}

.thread-foot > span {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--ink-4);
  font-size: 7.5px;
}

.thread-foot button {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--role-deep);
  font-size: 7.5px;
}

.doubt-footnote {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  margin-top: 12px;
  padding: 0 4px;
  color: var(--ink-4);
  font-size: 8.5px;
  line-height: 1.6;
}

.doubt-footnote .icon {
  flex: none;
  margin-top: 1px;
}

@media (max-width: 700px) {
  .question-overview {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
    padding: 17px;
  }

  .overview-counts {
    width: 100%;
    justify-content: space-between;
    padding-top: 10px;
    padding-left: 0;
    border-top: 1px dashed rgba(101, 122, 186, 0.22);
    border-left: 0;
  }

  .filter-hint {
    display: none;
  }

  .thread-head {
    flex-wrap: wrap;
  }

  .thread-title {
    flex: 1 0 60%;
  }

  .open-link {
    margin-left: auto;
  }

  .thread-foot {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }
}
</style>
