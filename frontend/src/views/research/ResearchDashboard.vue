<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { researchApi } from '@/api/research'
import { errorMessage } from '@/api/errors'
import type { KnowledgePoint, OperationLog, PublicTemplate } from '@/types/models'
import { formatRelative } from '@/utils/format'
import { actionLabel, actionTone, actorLabel, targetLabel, isResearchVisibleLog, researchLogSummary } from '@/utils/audit'
import AppIcon from '@/components/AppIcon.vue'
import StatCard from '@/components/StatCard.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import AppEmpty from '@/components/AppEmpty.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const greeting = computed(() => {
  const hour = new Date().getHours()
  const word = hour < 6 ? '夜深了' : hour < 11 ? '早上好' : hour < 14 ? '中午好' : hour < 19 ? '下午好' : '晚上好'
  return `${word}，${auth.displayName}`
})

const loading = ref(true)
const error = ref('')
const knowledgePoints = ref<KnowledgePoint[]>([])
const templates = ref<PublicTemplate[]>([])
const logs = ref<OperationLog[]>([])

const leaves = computed(() => knowledgePoints.value.flatMap((chapter) => chapter.children ?? []))
const activeLeaves = computed(() => leaves.value.filter((point) => point.status === 'ACTIVE'))
const disabledLeaves = computed(() => leaves.value.filter((point) => point.status === 'DISABLED'))
const pendingTemplates = computed(() =>
  templates.value
    .filter((template) => template.status === 'PENDING')
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
)
const approvedCount = computed(
  () => templates.value.filter((template) => template.status === 'APPROVED').length,
)
const reviewQueue = computed(() => pendingTemplates.value.slice(0, 2))
const chapters = computed(() =>
  knowledgePoints.value.map((chapter) => ({
    ...chapter,
    activeChildren: (chapter.children ?? []).filter((point) => point.status === 'ACTIVE'),
    disabledChildren: (chapter.children ?? []).filter((point) => point.status === 'DISABLED'),
  })),
)
const governanceLogs = computed(() =>
  logs.value.filter((log) => isResearchVisibleLog(log)).slice(0, 5),
)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [points, templatePage, logPage] = await Promise.all([
      researchApi.listKnowledgePoints(true),
      researchApi.listTemplates({ page: 1, pageSize: 100 }),
      researchApi.listOperationLogs({ page: 1, pageSize: 20 }),
    ])
    knowledgePoints.value = points
    templates.value = templatePage.items
    logs.value = logPage.items
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

function sectionsOf(template: PublicTemplate): string[] {
  const raw = template.structure?.sections
  return Array.isArray(raw) ? raw.filter((item): item is string => typeof item === 'string') : []
}

onMounted(load)
</script>

<template>
  <div class="research-dashboard">
    <PageHeader
      :eyebrow="`RESEARCH OVERVIEW · ${new Date().getFullYear()}`"
      :title="greeting"
      subtitle="先看哪份模板？知识点口径与复核队列都已就绪，教学表达由你统一把关。"
    >
      <template #actions>
        <button class="btn btn--ghost btn--sm" :disabled="loading" @click="load">
          <AppIcon name="refresh" :size="15" />
          刷新
        </button>
      </template>
    </PageHeader>

    <section class="research-hero">
      <div class="hero-copy">
        <span class="eyebrow">RESEARCH · CURRICULUM DESK</span>
        <h1 class="page-title">把“怎么讲”<br /><em>沉淀成共识</em></h1>
        <p class="hero-lead">
          维护知识点口径与公共讲评模板，让每一份讲评都有依据可循。
          这里只处理教学表达，不接触班级成绩、批改记录或学生订正。
        </p>
        <div class="hero-actions">
          <RouterLink to="/research/templates" class="btn btn--primary">
            审阅公共模板
            <AppIcon name="arrow-right" :size="15" />
          </RouterLink>
          <RouterLink to="/research/knowledge-points" class="btn btn--ghost">
            查看知识体系
          </RouterLink>
        </div>
        <div class="hero-sign">
          <span class="sign-line" />
          <span>描述错误，而不是描述学生。</span>
          <span class="sign-ref">口径摘录 · R16</span>
        </div>
      </div>

      <div class="hero-art" aria-hidden="true">
        <span class="art-halo" />
        <div class="art-card art-card--back">
          <span>CHAPTER / 01</span>
          <b>一元一次方程</b>
          <i /><i /><i />
        </div>
        <div class="art-card art-card--mid">
          <span>TEMPLATE / 07</span>
          <b>去括号 · 讲评骨架</b>
          <i /><i />
          <div class="mini-structure">
            <span>考查点</span><span>常见错误</span><span>变式</span>
          </div>
        </div>
        <div class="art-card art-card--front">
          <span>REVIEW / PASS</span>
          <div class="art-stamp">口径通过</div>
          <b>移项 · 讲评骨架</b>
          <i /><i />
          <div class="art-signature">教研组 · 复核</div>
        </div>
        <span class="art-clip" />
        <span class="art-leaf art-leaf--one" />
        <span class="art-leaf art-leaf--two" />
      </div>
    </section>

    <div v-if="loading" class="card loading-card">
      <span class="skeleton" style="width: 220px; height: 10px" />
    </div>

    <div v-else-if="error" class="card">
      <AppEmpty icon="alert-circle" title="教研数据加载失败" :description="error">
        <button class="btn btn--ghost btn--sm" @click="load">重试</button>
      </AppEmpty>
    </div>

    <template v-else>
      <section class="stat-row">
        <StatCard
          label="章节 / 一级知识点"
          :value="knowledgePoints.length"
          icon="layers"
          tone="apricot"
          :note="`共 ${leaves.length} 个二级知识点`"
        />
        <StatCard
          label="启用的二级知识点"
          :value="activeLeaves.length"
          icon="target"
          tone="mint"
          :note="disabledLeaves.length ? `${disabledLeaves.length} 个已停用，保留历史口径` : '全部处于启用状态'"
        />
        <StatCard
          label="待审核模板"
          :value="pendingTemplates.length"
          icon="file"
          tone="coral"
          note="审核通过后才成为公共口径"
        />
        <StatCard
          label="已通过模板"
          :value="approvedCount"
          icon="check-double"
          tone="lav"
          :note="`累计提交 ${templates.length} 份`"
        />
      </section>

      <div class="grid grid--main-side dashboard-grid">
        <div class="stack">
          <section class="card review-card">
            <div class="section-head">
              <div>
                <h2 class="section-title">待审阅的公共模板</h2>
                <p class="section-desc">先看结构是否完整，再看措辞是否指向“错误”而非“学生”。</p>
              </div>
              <RouterLink to="/research/templates" class="text-link tiny">
                全部模板
                <AppIcon name="arrow-right" :size="12" />
              </RouterLink>
            </div>

            <div v-if="reviewQueue.length" class="review-queue">
              <article v-for="template in reviewQueue" :key="template.id" class="review-ticket">
                <div class="ticket-rail">
                  <span class="ticket-index">{{ String(template.id).padStart(2, '0') }}</span>
                  <span class="ticket-line" />
                </div>
                <div class="ticket-body">
                  <div class="ticket-top">
                    <span v-if="template.category" class="tag">{{ template.category }}</span>
                    <StatusBadge status="PENDING" />
                    <span class="ticket-time">{{ formatRelative(template.createdAt) }}提交</span>
                  </div>
                  <h3>{{ template.title }}</h3>
                  <p>{{ template.content || '模板正文待补充。' }}</p>
                  <div v-if="sectionsOf(template).length" class="ticket-sections">
                    <span v-for="section in sectionsOf(template)" :key="section">{{ section }}</span>
                  </div>
                  <div class="ticket-foot">
                    <span class="ticket-author">
                      <AppIcon name="user" :size="12" />
                      {{ template.createdByName || '教师投稿' }}
                    </span>
                    <RouterLink
                      :to="{ path: '/research/templates', query: { focus: template.id } }"
                      class="btn btn--soft btn--sm"
                    >
                      进入审阅
                      <AppIcon name="chevron-right" :size="12" />
                    </RouterLink>
                  </div>
                </div>
              </article>
            </div>
            <AppEmpty
              v-else
              icon="check-circle"
              title="待审模板已清空"
              description="当前没有等待审阅的公共模板，新的教师投稿会出现在这里。"
            />
          </section>

          <section class="card knowledge-card">
            <div class="section-head">
              <div>
                <h2 class="section-title">知识体系一览</h2>
                <p class="section-desc">二级知识点用于统计归因与讲评组织，停用后历史数据仍保留原口径。</p>
              </div>
              <RouterLink to="/research/knowledge-points" class="text-link tiny">
                维护体系
                <AppIcon name="arrow-right" :size="12" />
              </RouterLink>
            </div>

            <div v-if="chapters.length" class="chapter-list">
              <article v-for="(chapter, index) in chapters" :key="chapter.id" class="chapter-row">
                <span class="chapter-no">{{ String(index + 1).padStart(2, '0') }}</span>
                <div class="chapter-main">
                  <div class="chapter-title">
                    <b>{{ chapter.name }}</b>
                    <span class="mono">{{ chapter.code }}</span>
                  </div>
                  <div class="kp-chips">
                    <span
                      v-for="point in chapter.children"
                      :key="point.id"
                      class="kp-chip"
                      :class="{ 'is-disabled': point.status === 'DISABLED' }"
                    >
                      {{ point.name }}
                    </span>
                    <span v-if="!(chapter.children ?? []).length" class="kp-empty">尚未配置二级知识点</span>
                  </div>
                </div>
                <div class="chapter-meta">
                  <b>{{ chapter.activeChildren.length }}</b>
                  <span>/ {{ (chapter.children ?? []).length }} 启用</span>
                </div>
              </article>
            </div>
            <AppEmpty
              v-else
              icon="layers"
              title="还没有知识点"
              description="先建立章节，再为章节添加二级知识点。"
            >
              <RouterLink to="/research/knowledge-points" class="btn btn--primary btn--sm">
                建立知识体系
              </RouterLink>
            </AppEmpty>
          </section>

          <section class="card logs-card">
            <div class="section-head">
              <div>
                <h2 class="section-title">最近的口径操作</h2>
                <p class="section-desc">只保留操作元数据，不包含成绩、答案或学生个人学习内容。</p>
              </div>
              <RouterLink to="/research/logs" class="text-link tiny">
                查看日志
                <AppIcon name="arrow-right" :size="12" />
              </RouterLink>
            </div>

            <div v-if="governanceLogs.length" class="log-list">
              <article v-for="log in governanceLogs" :key="log.id" class="log-row">
                <span class="log-dot" :data-tone="actionTone(log.action)" />
                <div class="log-main">
                  <div class="log-head">
                    <b>{{ actionLabel(log.action) }}</b>
                    <StatusBadge
                      :status="log.action"
                      :label="targetLabel(log.targetType)"
                      :tone="actionTone(log.action)"
                      plain
                    />
                  </div>
                  <p>{{ researchLogSummary(log) }}</p>
                </div>
                <div class="log-side">
                  <span>{{ actorLabel(log) }}</span>
                  <time>{{ formatRelative(log.createdAt) }}</time>
                </div>
              </article>
            </div>
            <AppEmpty v-else icon="list" title="暂无操作记录" description="知识点与模板操作会自动写入审计日志。" />
          </section>
        </div>

        <aside class="stack">
          <section class="card boundary-card">
            <div class="boundary-head">
              <span class="boundary-icon"><AppIcon name="shield" :size="19" /></span>
              <div>
                <h2>教研的数据边界</h2>
                <p>维护口径，不进入班级现场。</p>
              </div>
            </div>
            <div class="boundary-block boundary-block--yes">
              <span class="boundary-label">可以维护</span>
              <ul>
                <li><AppIcon name="check" :size="12" />知识点层级、编码与名称</li>
                <li><AppIcon name="check" :size="12" />公共讲评模板的审核结论</li>
                <li><AppIcon name="check" :size="12" />口径相关的操作审计记录</li>
              </ul>
            </div>
            <div class="boundary-block boundary-block--no">
              <span class="boundary-label">不会看到</span>
              <ul>
                <li><AppIcon name="x" :size="12" />班级成绩、排名与逐题批改</li>
                <li><AppIcon name="x" :size="12" />学生订正内容与个人疑问</li>
                <li><AppIcon name="x" :size="12" />AI 草稿与教师审核过程</li>
              </ul>
            </div>
            <p class="boundary-foot">
              <AppIcon name="lock" :size="12" />
              学生个体的学习操作在教研日志中默认不展示。
            </p>
          </section>

          <section class="card principle-card">
            <span class="eyebrow">REVIEW PRINCIPLES</span>
            <h2>一份模板过审前</h2>
            <ol class="principle-list">
              <li>
                <span>01</span>
                <div>
                  <b>指向错误，不指向学生</b>
                  <p>写“去括号漏变号”，不写“粗心、不认真”。</p>
                </div>
              </li>
              <li>
                <span>02</span>
                <div>
                  <b>结构可复用，结论可追溯</b>
                  <p>保留考查点与典型错误，讲评时仍以原题证据为准。</p>
                </div>
              </li>
              <li>
                <span>03</span>
                <div>
                  <b>模板只管表达，不改数据</b>
                  <p>模板不会影响分数、批改事实或学生画像。</p>
                </div>
              </li>
            </ol>
            <div class="principle-quote">
              <AppIcon name="pen" :size="14" />
              <span>审核意见要写清“为什么退回”，方便教师修改后再提交。</span>
            </div>
          </section>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.research-dashboard {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.research-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(300px, 0.85fr);
  min-height: 310px;
  overflow: hidden;
  border: 1px solid #efe4d1;
  border-radius: 28px;
  background:
    radial-gradient(circle at 88% 12%, rgba(255, 255, 255, 0.95), transparent 32%),
    linear-gradient(118deg, #fffdf9 0%, #fbf4e7 52%, #f5eee2 100%);
  box-shadow: var(--shadow-sm);
}

.research-hero::before {
  content: '';
  position: absolute;
  inset: 12px;
  border: 1px solid rgba(169, 134, 84, 0.12);
  border-radius: 21px;
  pointer-events: none;
}

.hero-copy {
  position: relative;
  z-index: 1;
  padding: clamp(27px, 3.4vw, 43px);
}

.hero-copy .eyebrow {
  color: var(--apricot-deep);
}

.hero-copy h1 {
  max-width: 13ch;
  margin-top: 2px;
  color: #3d3429;
  font-size: clamp(36px, 4.2vw, 55px);
  line-height: 1.18;
}

.hero-copy h1 em {
  position: relative;
  color: var(--apricot-deep);
  font-style: normal;
}

.hero-copy h1 em::after {
  content: '';
  position: absolute;
  right: 2px;
  bottom: 4px;
  left: 2px;
  height: 8px;
  border-radius: 40% 60% 45% 55%;
  background: rgba(214, 160, 95, 0.17);
  transform: rotate(-1deg);
}

.hero-lead {
  max-width: 48ch;
  margin-top: 15px;
  color: #75695a;
  font-size: 13.5px;
  line-height: 1.9;
}

.hero-actions {
  display: flex;
  gap: 9px;
  margin-top: 22px;
  flex-wrap: wrap;
}

.hero-actions .btn--primary {
  background: var(--apricot);
  box-shadow: 0 12px 26px -14px rgba(156, 107, 44, 0.7);
}

.hero-actions .btn--ghost {
  border-color: rgba(156, 107, 44, 0.2);
  background: rgba(255, 255, 255, 0.55);
}

.hero-sign {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-top: 26px;
  color: #9a8a73;
  font-family: var(--font-hand);
  font-size: 12.5px;
}

.sign-line {
  width: 20px;
  height: 1px;
  background: currentColor;
}

.sign-ref {
  padding-left: 9px;
  border-left: 1px solid #e2d6c4;
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.06em;
}

.hero-art {
  position: relative;
  min-height: 310px;
}

.art-halo {
  position: absolute;
  top: 2%;
  right: -12%;
  width: 340px;
  height: 340px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.95), rgba(250, 241, 225, 0.1) 70%);
}

.art-card {
  position: absolute;
  display: flex;
  flex-direction: column;
  width: 238px;
  min-height: 166px;
  padding: 17px 18px;
  border: 1px solid rgba(174, 146, 105, 0.22);
  border-radius: 4px 14px 14px 4px;
  background:
    repeating-linear-gradient(0deg, transparent 0 24px, rgba(174, 144, 99, 0.08) 24px 25px),
    #fffdf8;
  box-shadow: 0 24px 46px -30px rgba(93, 68, 33, 0.5);
}

.art-card > span {
  color: #ae8e5d;
  font-family: var(--font-mono);
  font-size: 7.5px;
  letter-spacing: 0.14em;
}

.art-card > b {
  margin-top: 7px;
  color: #4d4030;
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.art-card > i {
  width: 76%;
  height: 5px;
  margin-top: 9px;
  border-radius: 4px;
  background: rgba(147, 121, 81, 0.11);
}

.art-card > i + i {
  width: 54%;
  margin-top: 6px;
}

.art-card--back {
  top: 26px;
  right: 52px;
  transform: rotate(7deg);
  opacity: 0.64;
}

.art-card--mid {
  top: 76px;
  right: 108px;
  transform: rotate(-4deg);
  background-color: #fdfaf3;
}

.art-card--front {
  top: 128px;
  right: 36px;
  z-index: 2;
  transform: rotate(2.5deg);
  box-shadow: 0 30px 60px -34px rgba(93, 68, 33, 0.6);
}

.art-card--front::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 5px;
  border-radius: 4px 0 0 4px;
  background: rgba(214, 160, 95, 0.55);
}

.mini-structure {
  display: flex;
  gap: 4px;
  margin-top: 13px;
  flex-wrap: wrap;
}

.mini-structure span {
  padding: 2px 6px;
  border-radius: 4px;
  color: #987d54;
  background: #f7efdf;
  font-size: 7px;
}

.art-stamp {
  position: absolute;
  top: 42px;
  right: 16px;
  display: grid;
  width: 50px;
  height: 50px;
  place-items: center;
  border: 1.5px solid rgba(180, 95, 91, 0.5);
  border-radius: 50%;
  color: rgba(180, 95, 91, 0.72);
  font-family: var(--font-serif);
  font-size: 10px;
  letter-spacing: 0.06em;
  line-height: 1.2;
  text-align: center;
  transform: rotate(-14deg);
}

.art-signature {
  margin-top: auto;
  padding-top: 8px;
  color: #b59c78;
  font-family: var(--font-hand);
  font-size: 9.5px;
  text-align: right;
}

.art-clip {
  position: absolute;
  top: 20px;
  right: 265px;
  z-index: 3;
  width: 13px;
  height: 54px;
  border: 2px solid #c8b394;
  border-radius: 8px;
  transform: rotate(12deg);
}

.art-leaf {
  position: absolute;
  width: 45px;
  height: 78px;
  border-radius: 80% 0 80% 0;
  background: linear-gradient(135deg, rgba(160, 183, 148, 0.45), rgba(121, 150, 111, 0.13));
  filter: blur(0.2px);
}

.art-leaf--one {
  right: 300px;
  bottom: 30px;
  transform: rotate(-24deg);
}

.art-leaf--two {
  right: 278px;
  bottom: 8px;
  width: 30px;
  height: 54px;
  transform: rotate(19deg);
  opacity: 0.7;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.dashboard-grid {
  align-items: start;
}

.review-card,
.knowledge-card,
.logs-card,
.boundary-card,
.principle-card {
  padding: 22px;
}

.review-queue {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.review-ticket {
  display: flex;
  gap: 12px;
  padding: 14px;
  border: 1px solid #efe8dc;
  border-radius: 15px;
  background: linear-gradient(120deg, #fffdfa, #fdf8ef);
}

.ticket-rail {
  display: flex;
  align-items: center;
  flex-direction: column;
  width: 28px;
  flex: none;
}

.ticket-index {
  color: #b69b73;
  font-family: var(--font-numbers);
  font-size: 15px;
}

.ticket-line {
  width: 1px;
  flex: 1;
  margin-top: 6px;
  background: repeating-linear-gradient(180deg, #e4d7c3 0 4px, transparent 4px 8px);
}

.ticket-body {
  min-width: 0;
  flex: 1;
}

.ticket-top {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.ticket-time {
  margin-left: auto;
  color: var(--ink-4);
  font-size: 9.5px;
}

.ticket-body h3 {
  margin-top: 7px;
  color: #40372c;
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 700;
}

.ticket-body > p {
  display: -webkit-box;
  margin-top: 4px;
  overflow: hidden;
  color: #817462;
  font-size: 11px;
  line-height: 1.7;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.ticket-sections {
  display: flex;
  gap: 5px;
  margin-top: 8px;
  flex-wrap: wrap;
}

.ticket-sections span {
  padding: 2px 7px;
  border-radius: 5px;
  color: #9b7c4f;
  background: #f8f0e1;
  font-size: 8.5px;
}

.ticket-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 10px;
  padding-top: 9px;
  border-top: 1px dashed #eadfce;
}

.ticket-author {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--ink-3);
  font-size: 9.5px;
}

.review-card :deep(.empty-state) {
  padding: 26px 14px 16px;
}

.chapter-list {
  display: flex;
  flex-direction: column;
}

.chapter-row {
  display: flex;
  align-items: flex-start;
  gap: 13px;
  padding: 14px 4px;
  border-bottom: 1px dashed var(--line);
}

.chapter-row:last-child {
  border-bottom: 0;
  padding-bottom: 3px;
}

.chapter-no {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  flex: none;
  border-radius: 9px;
  color: var(--apricot-deep);
  background: var(--apricot-soft);
  font-family: var(--font-numbers);
  font-size: 12px;
}

.chapter-main {
  min-width: 0;
  flex: 1;
}

.chapter-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}

.chapter-title b {
  color: var(--ink);
  font-size: 12.5px;
}

.chapter-title span {
  color: var(--ink-4);
  font-size: 9px;
}

.kp-chips {
  display: flex;
  gap: 5px;
  margin-top: 6px;
  flex-wrap: wrap;
}

.kp-chip {
  padding: 2px 8px;
  border: 1px solid #e8f1ed;
  border-radius: 999px;
  color: #657a73;
  background: #f8fbf9;
  font-size: 9.5px;
}

.kp-chip.is-disabled {
  color: #a6b3ae;
  background: #f3f6f5;
  text-decoration: line-through;
  text-decoration-color: #c9d4d0;
}

.kp-empty {
  color: var(--ink-4);
  font-size: 9.5px;
}

.chapter-meta {
  display: flex;
  align-items: baseline;
  gap: 2px;
  flex: none;
  color: var(--ink-4);
  font-size: 8.5px;
}

.chapter-meta b {
  color: var(--apricot-deep);
  font-family: var(--font-numbers);
  font-size: 16px;
  font-weight: 600;
}

.log-list {
  display: flex;
  flex-direction: column;
}

.log-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 3px;
  border-bottom: 1px dashed var(--line);
}

.log-row:last-child {
  border-bottom: 0;
}

.log-dot {
  width: 7px;
  height: 7px;
  flex: none;
  margin-top: 5px;
  border-radius: 50%;
  background: var(--slate-soft);
  box-shadow: 0 0 0 4px #f5f8f6;
}

.log-dot[data-tone='apricot'] {
  background: var(--apricot);
  box-shadow: 0 0 0 4px var(--apricot-soft);
}

.log-dot[data-tone='lav'] {
  background: var(--lav);
  box-shadow: 0 0 0 4px var(--lav-soft);
}

.log-dot[data-tone='mint'] {
  background: var(--mint);
  box-shadow: 0 0 0 4px var(--mint-soft);
}

.log-dot[data-tone='peri'] {
  background: var(--peri);
  box-shadow: 0 0 0 4px var(--peri-soft);
}

.log-dot[data-tone='coral'] {
  background: var(--coral);
  box-shadow: 0 0 0 4px var(--coral-soft);
}

.log-main {
  min-width: 0;
  flex: 1;
}

.log-head {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.log-head b {
  color: var(--ink-2);
  font-size: 10.5px;
}

.log-head :deep(.badge) {
  min-height: 17px;
  padding: 0 6px;
  font-size: 8px;
}

.log-main p {
  margin-top: 2px;
  overflow: hidden;
  color: var(--ink-3);
  font-size: 9.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.log-side {
  flex: none;
  color: var(--ink-4);
  font-size: 8px;
  text-align: right;
}

.log-side span,
.log-side time {
  display: block;
}

.log-side span {
  color: var(--ink-3);
}

.boundary-card {
  background:
    radial-gradient(circle at 100% 0, rgba(214, 160, 95, 0.08), transparent 40%),
    #fff;
}

.boundary-head {
  display: flex;
  align-items: flex-start;
  gap: 11px;
}

.boundary-icon {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  flex: none;
  border-radius: 11px;
  color: var(--apricot-deep);
  background: var(--apricot-soft);
}

.boundary-head h2 {
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 700;
}

.boundary-head p {
  margin-top: 2px;
  color: var(--ink-3);
  font-size: 9.5px;
}

.boundary-block {
  margin-top: 15px;
  padding-top: 13px;
  border-top: 1px dashed #eee4d6;
}

.boundary-label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--ink-3);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.11em;
}

.boundary-block--yes .boundary-label {
  color: var(--mint-deep);
}

.boundary-block--no .boundary-label {
  color: var(--coral-deep);
}

.boundary-block ul {
  display: flex;
  flex-direction: column;
  gap: 7px;
  margin-top: 8px;
}

.boundary-block li {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  color: var(--ink-2);
  font-size: 10px;
  line-height: 1.55;
}

.boundary-block--yes li .icon {
  color: var(--mint);
}

.boundary-block--no li .icon {
  color: var(--coral);
}

.boundary-foot {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  margin-top: 15px;
  padding: 9px 10px;
  border-radius: 9px;
  color: var(--ink-3);
  background: var(--surface-soft);
  font-size: 9px;
  line-height: 1.6;
}

.boundary-foot .icon {
  flex: none;
  margin-top: 1px;
}

.principle-card {
  background:
    linear-gradient(180deg, #fffdf9, #fff 46%),
    #fff;
}

.principle-card .eyebrow {
  margin-bottom: 7px;
  font-size: 9px;
}

.principle-card h2 {
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 400;
  letter-spacing: 0.02em;
}

.principle-list {
  display: flex;
  flex-direction: column;
  gap: 13px;
  margin-top: 16px;
}

.principle-list li {
  display: flex;
  gap: 10px;
}

.principle-list li > span {
  padding-top: 1px;
  color: #c4a97f;
  font-family: var(--font-numbers);
  font-size: 12px;
}

.principle-list b {
  display: block;
  color: var(--ink-2);
  font-size: 10.5px;
}

.principle-list p {
  margin-top: 2px;
  color: var(--ink-3);
  font-size: 9.5px;
  line-height: 1.65;
}

.principle-quote {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin-top: 16px;
  padding: 11px;
  border-left: 2px solid var(--apricot);
  border-radius: 0 8px 8px 0;
  color: #8f795c;
  background: #fcf7ed;
  font-family: var(--font-hand);
  font-size: 10.5px;
  line-height: 1.65;
}

.principle-quote .icon {
  flex: none;
  margin-top: 1px;
  color: var(--apricot-deep);
}

@media (max-width: 1180px) {
  .research-hero {
    grid-template-columns: minmax(0, 1.25fr) minmax(260px, 0.75fr);
  }

  .art-card {
    width: 214px;
  }

  .art-card--back {
    right: 34px;
  }

  .art-card--mid {
    right: 74px;
  }

  .art-card--front {
    right: 18px;
  }

  .art-clip {
    right: 220px;
  }

  .art-leaf--one {
    right: 250px;
  }

  .art-leaf--two {
    right: 230px;
  }

  .stat-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 860px) {
  .research-hero {
    grid-template-columns: 1fr;
  }

  .hero-copy h1 {
    max-width: none;
  }

  .hero-art {
    display: none;
  }
}

@media (max-width: 720px) {
  .research-hero {
    border-radius: 22px;
  }

  .hero-copy {
    padding: 24px 20px;
  }

  .hero-copy h1 {
    font-size: 35px;
  }

  .hero-lead {
    font-size: 12.5px;
  }

  .hero-sign {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }

  .sign-line {
    display: none;
  }

  .sign-ref {
    padding-left: 0;
    border-left: 0;
  }

  .stat-row {
    grid-template-columns: 1fr;
  }

  .review-ticket {
    padding: 11px;
  }

  .ticket-rail {
    display: none;
  }

  .ticket-foot {
    align-items: flex-start;
    flex-direction: column;
  }

  .chapter-row {
    gap: 9px;
  }

  .chapter-meta {
    display: none;
  }

  .log-side {
    display: none;
  }
}
</style>
