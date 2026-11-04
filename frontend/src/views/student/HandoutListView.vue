<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { studentApi } from '@/api/student'
import { errorMessage } from '@/api/errors'
import type { StudentHandoutSummary } from '@/types/models'
import { formatDateTime, formatRelative } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'
import AppIcon from '@/components/AppIcon.vue'
import AppEmpty from '@/components/AppEmpty.vue'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const handouts = ref<StudentHandoutSummary[]>([])

const totals = computed(() => {
  const items = handouts.value.reduce((sum, handout) => sum + handout.itemCount, 0)
  const corrected = handouts.value.reduce((sum, handout) => sum + handout.correctedCount, 0)
  return {
    items,
    corrected,
    pending: Math.max(0, items - corrected),
    rate: items ? Math.round((corrected / items) * 100) : 0,
  }
})

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const list = await studentApi.listMyHandouts()
    handouts.value = [...list].sort((a, b) => b.publishedAt.localeCompare(a.publishedAt))
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

function open(handout: StudentHandoutSummary): void {
  void router.push(`/student/handouts/${handout.id}`)
}

function dateParts(value: string): { day: string; month: string } {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return { day: '--', month: '--' }
  return {
    day: String(date.getDate()).padStart(2, '0'),
    month: `${date.getMonth() + 1} 月`,
  }
}

function completion(handout: StudentHandoutSummary): number {
  return handout.itemCount ? Math.round((handout.correctedCount / handout.itemCount) * 100) : 0
}

onMounted(load)
</script>

<template>
  <div class="handout-list-page">
    <PageHeader
      eyebrow="STUDENT · MY COMMENTARIES"
      title="我的错题讲评"
      subtitle="老师发布并经本人确认的讲解，才会出现在这里。你只会看到自己的错题，没有排名和同学对比。"
    >
      <template #actions>
        <span class="privacy-pill"><AppIcon name="lock" :size="13" />仅本人可见</span>
        <RouterLink to="/student/corrections" class="btn btn--ghost btn--sm">
          <AppIcon name="check-circle" :size="14" />
          我的订正
        </RouterLink>
      </template>
    </PageHeader>

    <div v-if="loading" class="card loading-card">
      <span class="skeleton" style="width: 220px; height: 10px" />
    </div>

    <div v-else-if="error" class="card">
      <AppEmpty icon="alert-circle" title="讲评列表加载失败" :description="error">
        <button class="btn btn--ghost btn--sm" @click="load">重试</button>
      </AppEmpty>
    </div>

    <template v-else>
      <div class="handout-overview">
        <div class="overview-copy">
          <span class="overview-kicker">MY CORRECTION PATH</span>
          <h2>把错题讲透，比多做十题更重要</h2>
          <p>
            目前共有 {{ handouts.length }} 份讲评、{{ totals.items }} 道属于你的错题。
            {{ totals.pending ? `仍有 ${totals.pending} 题等待订正。` : '所有错题都已提交订正。' }}
          </p>
        </div>
        <div class="overview-stats">
          <div><span>讲评</span><b>{{ handouts.length }}<small> 份</small></b></div>
          <div><span>已订正</span><b>{{ totals.corrected }}<small> / {{ totals.items }}</small></b></div>
          <div><span>完成率</span><b>{{ totals.rate }}<small>%</small></b></div>
        </div>
        <div class="overview-stamp" aria-hidden="true">
          <span>安静地</span>
          <b>弄懂一题</b>
        </div>
      </div>

      <div class="handout-content">
        <div class="handout-list">
          <article
            v-for="handout in handouts"
            :key="handout.id"
            class="handout-card"
            :class="{ 'is-complete': handout.correctedCount >= handout.itemCount }"
          >
            <div class="handout-date">
              <span>{{ dateParts(handout.publishedAt).month }}</span>
              <b>{{ dateParts(handout.publishedAt).day }}</b>
              <i>已发布</i>
            </div>
            <div class="handout-main">
              <div class="handout-top">
                <span class="handout-status"><span />老师已确认</span>
                <span v-if="handout.className" class="handout-class">{{ handout.className }}</span>
                <span class="handout-time">{{ formatRelative(handout.publishedAt) }}</span>
              </div>
              <h3>{{ handout.homeworkTitle }}</h3>
              <p>
                本篇包含 {{ handout.itemCount }} 道你的错题讲解。老师已逐条确认内容，只有这些讲解对你可见。
              </p>
              <div class="handout-meta">
                <span><AppIcon name="book-open" :size="12" />{{ handout.itemCount }} 条讲解</span>
                <span><AppIcon name="check-circle" :size="12" />{{ handout.correctedCount }} 题已订正</span>
                <span><AppIcon name="message" :size="12" />{{ handout.doubtCount }} 条疑问</span>
                <span class="handout-published">{{ formatDateTime(handout.publishedAt) }}</span>
              </div>
              <div class="handout-progress">
                <div class="progress"><span :style="{ width: `${completion(handout)}%` }" /></div>
                <b>{{ completion(handout) }}%</b>
              </div>
            </div>
            <div class="handout-actions">
              <span v-if="handout.correctedCount >= handout.itemCount" class="complete-tag">
                <AppIcon name="check" :size="12" />本轮已完成
              </span>
              <button class="btn btn--primary btn--sm" type="button" @click="open(handout)">
                查看讲解
                <AppIcon name="arrow-right" :size="13" />
              </button>
            </div>
          </article>

          <AppEmpty
            v-if="!handouts.length"
            icon="book-open"
            title="还没有已发布讲评"
            description="老师确认并发布讲解后，它会出现在这里。AI 草稿和未发布内容不会对你展示。"
          />
        </div>

        <aside class="handout-aside">
          <section class="aside-card reading-card">
            <span class="aside-icon"><AppIcon name="book-open" :size="18" /></span>
            <h4>怎么用这份讲解</h4>
            <ol class="reading-steps">
              <li><span>01</span><p><b>先看自己的答案</b>不急着翻到正确选项。</p></li>
              <li><span>02</span><p><b>顺着讲解重做一遍</b>留意老师标出的那一步。</p></li>
              <li><span>03</span><p><b>提交订正</b>写下这次是怎么想的。</p></li>
              <li><span>04</span><p><b>仍有疑问就问</b>问题会直接送到老师那里。</p></li>
            </ol>
          </section>

          <section class="aside-card boundary-card">
            <span class="aside-icon"><AppIcon name="shield" :size="17" /></span>
            <h4>你的数据边界</h4>
            <p>这里不会出现其他同学的答案、排名或订正情况，也不会展示 AI 草稿、依据快照和教师内部审核信息。</p>
          </section>

          <section class="aside-card ai-card">
            <div class="ai-head">
              <span><AppIcon name="sparkles" :size="15" /></span>
              <b>AI 追问</b>
              <em>预留</em>
            </div>
            <p>第一版不开放自由问答。未来只允许针对本人已发布讲解追问，回答仍需老师把关。</p>
          </section>
        </aside>
      </div>

      <div class="list-footer">
        <AppIcon name="info" :size="13" />
        讲评按发布时间倒序排列。老师撤回讲评后，对应内容会立即从你的空间隐藏，但已提交的订正不会被删除。
      </div>
    </template>
  </div>
</template>

<style scoped>
.handout-list-page {
  max-width: 1160px;
}

.privacy-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 9px;
  border: 1px solid #dde5fb;
  border-radius: 99px;
  color: var(--peri-deep);
  background: var(--peri-soft);
  font-size: 8.5px;
}

.handout-overview {
  position: relative;
  display: flex;
  align-items: center;
  gap: 24px;
  margin-bottom: 16px;
  padding: 22px 26px;
  overflow: hidden;
  border: 1px solid #dde6fb;
  border-radius: 21px;
  background:
    radial-gradient(circle at 90% 10%, rgba(255, 255, 255, 0.94), transparent 34%),
    linear-gradient(120deg, #fdfefe, #eff4ff 60%, #f6f2fd);
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
  font-family: var(--font-display);
  font-size: clamp(21px, 2.2vw, 27px);
  font-weight: 400;
  line-height: 1.35;
}

.overview-copy p {
  margin-top: 4px;
  color: var(--ink-3);
  font-size: 9.5px;
  line-height: 1.7;
}

.overview-stats {
  display: flex;
  gap: 22px;
  flex: none;
  padding-left: 22px;
  border-left: 1px dashed rgba(101, 122, 186, 0.22);
}

.overview-stats span {
  display: block;
  color: var(--ink-3);
  font-size: 8px;
}

.overview-stats b {
  display: block;
  margin-top: 1px;
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 23px;
  font-weight: 600;
  line-height: 1.2;
}

.overview-stats b small {
  color: var(--ink-3);
  font-family: var(--font-body);
  font-size: 8px;
  font-weight: 400;
}

.overview-stamp {
  flex: none;
  padding: 8px 12px;
  border: 1.5px solid rgba(107, 140, 245, 0.25);
  border-radius: 6px;
  color: rgba(76, 103, 196, 0.45);
  text-align: center;
  transform: rotate(-6deg);
}

.overview-stamp span {
  display: block;
  font-family: var(--font-hand);
  font-size: 9px;
}

.overview-stamp b {
  display: block;
  font-family: var(--font-serif);
  font-size: 11px;
}

.handout-content {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 15px;
  align-items: start;
}

.handout-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.handout-card {
  position: relative;
  display: flex;
  gap: 15px;
  padding: 17px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 17px;
  background: #fff;
  box-shadow: var(--shadow-xs);
  transition: 0.22s var(--ease-out);
}

.handout-card::before {
  content: '';
  position: absolute;
  top: 15px;
  bottom: 15px;
  left: 0;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: var(--peri);
  opacity: 0.55;
}

.handout-card:hover {
  transform: translateY(-1px);
  border-color: color-mix(in srgb, var(--peri) 25%, var(--line));
  box-shadow: var(--shadow-sm);
}

.handout-card.is-complete::before {
  background: var(--mint);
}

.handout-date {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 66px;
  flex: none;
  border-radius: 11px;
  color: var(--peri-deep);
  background: var(--peri-soft);
}

.handout-date span {
  font-size: 7.5px;
}

.handout-date b {
  margin-top: 1px;
  font-family: var(--font-numbers);
  font-size: 23px;
  font-weight: 600;
  line-height: 1;
}

.handout-date i {
  margin-top: 3px;
  color: #8194ca;
  font-size: 6.5px;
  font-style: normal;
}

.handout-main {
  min-width: 0;
  flex: 1;
}

.handout-top {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.handout-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--peri-deep);
  font-size: 8px;
  font-weight: 600;
}

.handout-status span {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--peri);
}

.handout-class {
  padding: 1px 5px;
  border-radius: 4px;
  color: var(--ink-3);
  background: var(--surface-soft);
  font-size: 7.5px;
}

.handout-time {
  margin-left: auto;
  color: var(--ink-4);
  font-size: 7.5px;
}

.handout-main h3 {
  margin-top: 6px;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 600;
  line-height: 1.5;
}

.handout-main > p {
  max-width: 68ch;
  margin-top: 3px;
  color: var(--ink-3);
  font-size: 9px;
  line-height: 1.65;
}

.handout-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px 11px;
  margin-top: 8px;
  color: var(--ink-3);
  font-size: 8px;
}

.handout-meta span {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.handout-published {
  color: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 7.5px;
}

.handout-progress {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 9px;
}

.handout-progress .progress {
  height: 5px;
  flex: 1;
}

.handout-progress > b {
  color: var(--peri-deep);
  font-family: var(--font-numbers);
  font-size: 9px;
  font-weight: 600;
}

.handout-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: space-between;
  gap: 8px;
  flex: none;
}

.complete-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--mint-deep);
  font-size: 8px;
}

.handout-actions .btn {
  font-size: 9px;
}

.handout-aside {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.aside-card {
  padding: 15px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: #fff;
}

.aside-icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  margin-bottom: 7px;
  border-radius: 9px;
  color: var(--peri-deep);
  background: var(--peri-soft);
}

.aside-card h4 {
  color: var(--ink-2);
  font-size: 10px;
}

.reading-steps {
  display: flex;
  flex-direction: column;
  gap: 7px;
  margin-top: 9px;
}

.reading-steps li {
  display: flex;
  align-items: flex-start;
  gap: 6px;
}

.reading-steps li span {
  color: var(--peri);
  font-family: var(--font-numbers);
  font-size: 9px;
}

.reading-steps p {
  color: var(--ink-3);
  font-size: 8.5px;
  line-height: 1.55;
}

.reading-steps b {
  color: var(--ink-2);
  font-weight: 600;
}

.boundary-card {
  background: var(--surface-soft);
  box-shadow: none;
}

.boundary-card .aside-icon {
  color: var(--ink-3);
  background: #fff;
}

.boundary-card p {
  margin-top: 4px;
  color: var(--ink-4);
  font-size: 8.5px;
  line-height: 1.7;
}

.ai-card {
  border-color: #e8e1f6;
  background: linear-gradient(145deg, #faf8fe, #f3f9f7);
}

.ai-head {
  display: flex;
  align-items: center;
  gap: 5px;
}

.ai-head > span {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 7px;
  color: var(--lav-deep);
  background: var(--lav-soft);
}

.ai-head b {
  color: var(--ink-2);
  font-size: 9px;
}

.ai-head em {
  margin-left: auto;
  padding: 1px 4px;
  border-radius: 3px;
  color: var(--lav-deep);
  background: rgba(255, 255, 255, 0.7);
  font-size: 6px;
  font-style: normal;
}

.ai-card > p {
  margin-top: 6px;
  color: var(--ink-3);
  font-size: 8.5px;
  line-height: 1.7;
}

.list-footer {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 11px;
  padding: 0 4px;
  color: var(--ink-4);
  font-size: 8.5px;
  line-height: 1.6;
}

.list-footer .icon {
  flex: none;
  margin-top: 1px;
}

@media (max-width: 980px) {
  .handout-content {
    grid-template-columns: 1fr;
  }

  .handout-aside {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }

  .reading-card {
    grid-row: span 2;
  }

  .overview-stamp {
    display: none;
  }
}

@media (max-width: 680px) {
  .handout-overview {
    align-items: flex-start;
    flex-direction: column;
    gap: 14px;
    padding: 17px;
  }

  .overview-stats {
    width: 100%;
    justify-content: space-between;
    padding-top: 10px;
    padding-left: 0;
    border-top: 1px dashed rgba(101, 122, 186, 0.22);
    border-left: 0;
  }

  .handout-card {
    flex-wrap: wrap;
    padding: 14px;
  }

  .handout-date {
    width: 44px;
    height: 54px;
  }

  .handout-date b {
    font-size: 19px;
  }

  .handout-actions {
    width: 100%;
    flex-direction: row;
    align-items: center;
    padding-left: 59px;
  }

  .handout-aside {
    display: flex;
  }

  .handout-published {
    display: none !important;
  }
}
</style>
