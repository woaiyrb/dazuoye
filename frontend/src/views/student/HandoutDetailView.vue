<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { studentApi } from '@/api/student'
import { ApiError, errorMessage } from '@/api/errors'
import type { StudentHandoutDetail, StudentHandoutItem } from '@/types/models'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { formatDateTime, formatRelative } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import AppIcon from '@/components/AppIcon.vue'
import AppEmpty from '@/components/AppEmpty.vue'
import AppModal from '@/components/AppModal.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()

const handoutId = computed(() => Number(route.params.id))
const handout = ref<StudentHandoutDetail | null>(null)
const loading = ref(true)
const pageError = ref('')

const selectedItemId = ref<number | null>(null)
const correctionOpen = ref(false)
const doubtOpen = ref(false)
const correctionAnswer = ref('')
const correctionNote = ref('')
const doubtContent = ref('')
const correctionError = ref('')
const doubtError = ref('')
const submittingCorrection = ref(false)
const submittingDoubt = ref(false)
const historyOpen = ref<number | null>(null)

const selectedItem = computed(
  () => handout.value?.items.find((item) => item.itemId === selectedItemId.value) ?? null,
)
const correctedCount = computed(
  () => handout.value?.items.filter((item) => item.corrections.length > 0).length ?? 0,
)
const progressRate = computed(() => {
  if (!handout.value?.items.length) return 0
  return Math.round((correctedCount.value / handout.value.items.length) * 100)
})
const questionDoubtCount = computed(
  () => handout.value?.items.reduce((sum, item) => sum + item.doubts.length, 0) ?? 0,
)

async function load(): Promise<void> {
  loading.value = true
  pageError.value = ''
  try {
    handout.value = await studentApi.getHandout(handoutId.value)
  } catch (cause) {
    if (cause instanceof ApiError && cause.status === 404) {
      pageError.value = '讲评尚未发布或已被老师撤回。'
    } else {
      pageError.value = errorMessage(cause)
    }
  } finally {
    loading.value = false
  }
}

function answerText(answer: string | null | undefined, type?: string): string {
  if (!answer) return '未作答'
  if (type === 'TRUE_FALSE' || answer === 'TRUE' || answer === 'FALSE') {
    if (answer === 'TRUE') return '正确'
    if (answer === 'FALSE') return '错误'
  }
  return answer
}

function openCorrection(item: StudentHandoutItem): void {
  selectedItemId.value = item.itemId
  correctionAnswer.value = ''
  correctionNote.value = ''
  correctionError.value = ''
  correctionOpen.value = true
}

function openDoubt(item: StudentHandoutItem): void {
  selectedItemId.value = item.itemId
  doubtContent.value = ''
  doubtError.value = ''
  doubtOpen.value = true
}

async function submitCorrection(): Promise<void> {
  if (!selectedItem.value) return
  const answer = correctionAnswer.value
  const content = correctionNote.value.trim()
  if (!answer && !content) {
    correctionError.value = '请重新选择答案，或写下订正说明。'
    return
  }
  if (content.length > 2000) {
    correctionError.value = '订正说明最多 2000 字。'
    return
  }
  submittingCorrection.value = true
  correctionError.value = ''
  try {
    await studentApi.createCorrection({
      handoutItemId: selectedItem.value.itemId,
      selectedAnswer: answer || null,
      content: content || null,
    })
    ui.toast('success', '订正已提交', '进度已经记录；是否掌握由老师结合课堂判断。')
    correctionOpen.value = false
    await load()
  } catch (cause) {
    correctionError.value = errorMessage(cause)
  } finally {
    submittingCorrection.value = false
  }
}

async function submitDoubt(): Promise<void> {
  if (!selectedItem.value) return
  const content = doubtContent.value.trim()
  if (!content) {
    doubtError.value = '请填写你想问的问题。'
    return
  }
  if (content.length > 1000) {
    doubtError.value = '疑问最多 1000 字。'
    return
  }
  submittingDoubt.value = true
  doubtError.value = ''
  try {
    await studentApi.createDoubt({
      handoutItemId: selectedItem.value.itemId,
      content,
    })
    ui.toast('success', '疑问已提交', '老师只会在本班疑问箱中看到它，回复后你会在这里收到。')
    doubtOpen.value = false
    await load()
  } catch (cause) {
    doubtError.value = errorMessage(cause)
  } finally {
    submittingDoubt.value = false
  }
}

function toggleHistory(itemId: number): void {
  historyOpen.value = historyOpen.value === itemId ? null : itemId
}

function scrollToItem(itemId: number): void {
  document.getElementById(`student-item-${itemId}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function backToList(): void {
  void router.push('/student/handouts')
}

onMounted(load)
</script>

<template>
  <div class="handout-detail-page">
    <div v-if="loading" class="card loading-card">
      <span class="skeleton" style="width: 230px; height: 10px" />
    </div>

    <div v-else-if="pageError" class="card">
      <AppEmpty icon="book" title="无法打开这份讲评" :description="pageError">
        <button class="btn btn--ghost btn--sm" @click="backToList">返回讲评列表</button>
      </AppEmpty>
    </div>

    <template v-else-if="handout">
      <button class="back-link" type="button" @click="backToList">
        <AppIcon name="arrow-left" :size="14" />
        返回我的讲评
      </button>

      <PageHeader
        eyebrow="MY COMMENTARY · TEACHER REVIEWED"
        title="错题讲解"
        :subtitle="handout.homeworkTitle"
      >
        <template #meta>
          <div class="handout-meta">
            <span class="meta-pill"><AppIcon name="check-circle" :size="12" />老师已确认</span>
            <span>发布于 {{ formatDateTime(handout.publishedAt) }}</span>
            <span>{{ handout.items.length }} 道你的错题</span>
          </div>
        </template>
        <template #actions>
          <span class="private-label"><AppIcon name="lock" :size="12" />仅本人可见</span>
        </template>
      </PageHeader>

      <div class="student-progress-band">
        <div class="progress-band__copy">
          <span class="band-kicker">THIS ROUND</span>
          <h2>{{ correctedCount === handout.items.length ? '这份讲评的错题都已提交订正' : '按自己的节奏，一题一题弄懂' }}</h2>
          <div class="band-stats">
            <span><b>{{ handout.items.length }}</b> 道错题</span>
            <span><b>{{ correctedCount }}</b> 题已订正</span>
            <span><b>{{ questionDoubtCount }}</b> 条疑问</span>
          </div>
        </div>
        <div class="band-progress">
          <div class="band-ring">
            <svg viewBox="0 0 84 84">
              <circle cx="42" cy="42" r="33" fill="none" stroke="rgba(255,255,255,.6)" stroke-width="6" />
              <circle
                cx="42"
                cy="42"
                r="33"
                fill="none"
                stroke="var(--role)"
                stroke-width="6"
                stroke-linecap="round"
                :stroke-dasharray="2 * Math.PI * 33"
                :stroke-dashoffset="2 * Math.PI * 33 * (1 - progressRate / 100)"
                transform="rotate(-90 42 42)"
              />
            </svg>
            <div><b>{{ progressRate }}<small>%</small></b><span>订正进度</span></div>
          </div>
          <div class="band-bars">
            <span
              v-for="item in handout.items"
              :key="item.itemId"
              :class="{ 'is-done': item.corrections.length }"
            >
              {{ item.seqNo }}
            </span>
          </div>
        </div>
      </div>

      <div class="handout-layout">
        <div class="workbook">
          <article
            v-for="item in handout.items"
            :key="item.itemId"
            :id="`student-item-${item.itemId}`"
            class="workbook-item"
            :class="{ 'is-corrected': item.corrections.length }"
          >
            <div class="item-number">
              <span class="numbers">{{ String(item.seqNo).padStart(2, '0') }}</span>
              <small>题</small>
            </div>
            <div class="item-main">
              <div class="item-head">
                <div class="item-labels">
                  <StatusBadge
                    :status="item.questionType"
                    :label="item.questionType === 'SINGLE_CHOICE' ? '单选题' : '判断题'"
                    :tone="item.questionType === 'SINGLE_CHOICE' ? 'peri' : 'lav'"
                  />
                  <span class="tag">{{ item.score }} 分</span>
                  <span class="teacher-reviewed"><AppIcon name="check" :size="10" />老师讲解</span>
                  <span v-if="item.corrections.length" class="badge" data-tone="mint">已提交订正</span>
                  <span v-else class="badge" data-tone="apricot">待订正</span>
                </div>
                <span v-if="item.doubts.length" class="doubt-count">
                  <AppIcon name="message" :size="11" />{{ item.doubts.length }} 条疑问
                </span>
              </div>

              <p class="item-stem">{{ item.stem }}</p>

              <div v-if="item.options?.length" class="item-options">
                <div
                  v-for="option in item.options"
                  :key="option.key"
                  :class="{
                    'is-correct': option.key === item.standardAnswer,
                    'is-mine': option.key === item.myRecord.studentAnswer && option.key !== item.standardAnswer,
                  }"
                >
                  <span>{{ option.key }}</span>
                  <p>{{ option.text }}</p>
                  <em v-if="option.key === item.standardAnswer">正确答案</em>
                  <em v-else-if="option.key === item.myRecord.studentAnswer" class="mine-label">我的选择</em>
                </div>
              </div>

              <div class="answer-compare">
                <div class="compare-side compare-side--mine">
                  <span>我的答案</span>
                  <b>{{ answerText(item.myRecord.studentAnswer, item.questionType) }}</b>
                </div>
                <AppIcon name="arrow-right" :size="14" class="compare-arrow" />
                <div class="compare-side compare-side--correct">
                  <span>正确答案</span>
                  <b>{{ item.standardAnswer ? answerText(item.standardAnswer, item.questionType) : '未设置' }}</b>
                </div>
                <div v-if="item.corrections.length" class="compare-state">
                  <span class="dot" />
                  已提交订正 · 掌握由老师判断
                </div>
                <div v-else class="compare-state">
                  <span class="dot dot--danger" />
                  本题待订正
                </div>
              </div>

              <div class="teacher-commentary">
                <div class="commentary-head">
                  <span class="teacher-marker">王</span>
                  <div>
                    <b>老师讲解</b>
                    <span>教师确认后发布 · 并非 AI 直接生成</span>
                  </div>
                  <span class="commentary-seal">已确认</span>
                </div>
                <p>{{ item.commentary }}</p>
              </div>

              <div v-if="item.corrections.length" class="correction-history">
                <button class="history-toggle" type="button" @click="toggleHistory(item.itemId)">
                  <span>
                    <AppIcon name="check-circle" :size="13" />
                    已提交 {{ item.corrections.length }} 次订正
                  </span>
                  <span>
                    {{ item.corrections[0].selectedAnswer ? `重选 ${item.corrections[0].selectedAnswer}` : '说明已记录' }}
                    <AppIcon :name="historyOpen === item.itemId ? 'chevron-up' : 'chevron-down'" :size="13" />
                  </span>
                </button>
                <Transition name="fade">
                  <div v-if="historyOpen === item.itemId" class="history-body">
                    <div v-for="correction in item.corrections" :key="correction.id" class="history-row">
                      <span class="history-version">V{{ correction.versionNo }}</span>
                      <div>
                        <p v-if="correction.content">{{ correction.content }}</p>
                        <p v-else class="muted">仅重新选择了答案，没有填写说明。</p>
                        <span>{{ formatDateTime(correction.submittedAt) }} · 重选答案 {{ correction.selectedAnswer || '—' }}</span>
                      </div>
                    </div>
                    <p class="history-note">订正历史保留，老师会结合课堂表现判断是否掌握。</p>
                  </div>
                </Transition>
              </div>

              <div v-if="item.doubts.length" class="doubt-thread">
                <div v-for="doubt in item.doubts" :key="doubt.id" class="doubt-entry">
                  <div class="doubt-question-row">
                    <span class="avatar avatar--sm">{{ auth.displayName.slice(0, 1) }}</span>
                    <div>
                      <b>我的疑问</b>
                      <span>{{ formatRelative(doubt.createdAt) }}</span>
                    </div>
                    <StatusBadge :status="doubt.status" plain />
                  </div>
                  <p class="doubt-content">{{ doubt.content }}</p>
                  <div v-for="reply in doubt.replies" :key="reply.id" class="reply-bubble">
                    <span class="teacher-marker teacher-marker--sm">{{ (reply.teacherName || '王').slice(0, 1) }}</span>
                    <div>
                      <b>{{ reply.teacherName || '王老师' }} 的回复</b>
                      <p>{{ reply.content }}</p>
                      <span>{{ formatDateTime(reply.createdAt) }}</span>
                    </div>
                  </div>
                </div>
              </div>

              <div class="item-actions">
                <button class="btn btn--primary btn--sm" type="button" @click="openCorrection(item)">
                  <AppIcon name="pen" :size="14" />
                  {{ item.corrections.length ? '补充一次订正' : '提交订正' }}
                </button>
                <button class="btn btn--ghost btn--sm" type="button" @click="openDoubt(item)">
                  <AppIcon name="message" :size="14" />
                  {{ item.doubts.length ? '再问一个' : '我还不懂，问老师' }}
                </button>
                <span v-if="item.corrections.length" class="action-note">
                  提交即记录，是否掌握由老师判断
                </span>
              </div>
            </div>
          </article>

          <AppEmpty
            v-if="!handout.items.length"
            icon="book"
            title="这份讲评没有你的错题"
            description="只有你的错题才会出现在这里。"
          />
        </div>

        <aside class="detail-aside">
          <section class="aside-card summary-card">
            <span class="aside-kicker">MY PROGRESS</span>
            <div class="aside-progress">
              <div class="aside-ring">
                <svg viewBox="0 0 78 78">
                  <circle cx="39" cy="39" r="31" fill="none" stroke="#edf1fb" stroke-width="6" />
                  <circle
                    cx="39"
                    cy="39"
                    r="31"
                    fill="none"
                    stroke="var(--role)"
                    stroke-width="6"
                    stroke-linecap="round"
                    :stroke-dasharray="2 * Math.PI * 31"
                    :stroke-dashoffset="2 * Math.PI * 31 * (1 - progressRate / 100)"
                    transform="rotate(-90 39 39)"
                  />
                </svg>
                <b>{{ progressRate }}<small>%</small></b>
              </div>
              <div>
                <b>{{ correctedCount }} / {{ handout.items.length }}</b>
                <span>错题已提交订正</span>
              </div>
            </div>
            <div class="question-nav">
              <button
                v-for="item in handout.items"
                :key="item.itemId"
                type="button"
                :class="{ 'is-done': item.corrections.length, 'has-doubt': item.doubts.length }"
                :title="`第 ${item.seqNo} 题`"
                @click="scrollToItem(item.itemId)"
              >
                {{ item.seqNo }}
              </button>
            </div>
          </section>

          <section class="aside-card rule-card">
            <span class="aside-icon"><AppIcon name="info" :size="16" /></span>
            <h4>订正小提示</h4>
            <p>订正不是抄答案。试着用自己的话说明“这次和上次哪里想得不一样”，老师会看到你的思考。</p>
          </section>

          <section class="aside-card private-card">
            <span class="aside-icon"><AppIcon name="shield" :size="16" /></span>
            <h4>只属于你的页面</h4>
            <p>你的答案、订正与疑问只对本人和本班老师可见。这里没有其他同学的答案或排名。</p>
          </section>

          <section class="aside-card ai-card">
            <div class="ai-head">
              <span><AppIcon name="sparkles" :size="14" /></span>
              <b>AI 追问</b>
              <em>预留</em>
            </div>
            <p>首版不开放学生自由问答。遇到不懂的步骤，直接向老师提问会更可靠。</p>
          </section>
        </aside>
      </div>

      <div class="page-footnote">
        <AppIcon name="book-open" :size="13" />
        讲评内容由老师确认后发布；AI 只参与候选草稿，不修改分数与批改事实，也不会直接与你对话。
      </div>
    </template>

    <AppModal
      v-model="correctionOpen"
      title="提交订正"
      :subtitle="`第 ${selectedItem?.seqNo ?? ''} 题 · 可以重新选择答案并写下你的思路`"
      size="md"
    >
      <div v-if="selectedItem" class="correction-form">
        <div class="correction-question">
          <span class="tag">第 {{ selectedItem.seqNo }} 题</span>
          <p>{{ selectedItem.stem }}</p>
        </div>

        <div v-if="selectedItem.options?.length" class="correction-options">
          <label
            v-for="option in selectedItem.options"
            :key="option.key"
            :class="{ 'is-selected': correctionAnswer === option.key }"
          >
            <input v-model="correctionAnswer" type="radio" :value="option.key" />
            <span class="option-key">{{ option.key }}</span>
            <span>{{ option.text }}</span>
          </label>
        </div>
        <div v-else class="truth-options">
          <label :class="{ 'is-active': correctionAnswer === 'TRUE' }">
            <input v-model="correctionAnswer" type="radio" value="TRUE" />
            正确
          </label>
          <label :class="{ 'is-active': correctionAnswer === 'FALSE' }">
            <input v-model="correctionAnswer" type="radio" value="FALSE" />
            错误
          </label>
        </div>

        <div class="field">
          <label for="correction-note">这次是怎么想的？（可选）</label>
          <textarea
            id="correction-note"
            v-model="correctionNote"
            class="textarea"
            maxlength="2000"
            placeholder="例：上次去括号时只给第一项变号，这次把括号内每一项都乘了。"
          />
          <span class="counter">{{ correctionNote.length }} / 2000</span>
        </div>

        <div class="info-banner info-banner--peri">
          <AppIcon name="info" :size="15" />
          提交订正会记录进度，但系统不会自动判定“掌握”。老师会结合课堂表现与订正过程判断。
        </div>
        <div v-if="correctionError" class="error-banner">
          <AppIcon name="alert-circle" :size="15" />
          {{ correctionError }}
        </div>
      </div>
      <template #footer>
        <button class="btn btn--ghost" type="button" @click="correctionOpen = false">再想想</button>
        <button class="btn btn--primary" type="button" :disabled="submittingCorrection" @click="submitCorrection">
          <AppIcon v-if="submittingCorrection" name="refresh" :size="15" />
          提交订正
        </button>
      </template>
    </AppModal>

    <AppModal
      v-model="doubtOpen"
      title="向老师提问"
      :subtitle="`第 ${selectedItem?.seqNo ?? ''} 题 · 老师会在本班疑问箱中看到`"
      size="md"
    >
      <div v-if="selectedItem" class="doubt-form">
        <div class="correction-question">
          <span class="tag">第 {{ selectedItem.seqNo }} 题</span>
          <p>{{ selectedItem.stem }}</p>
        </div>
        <div class="field">
          <label for="doubt-content">你的问题</label>
          <textarea
            id="doubt-content"
            v-model="doubtContent"
            class="textarea"
            maxlength="1000"
            placeholder="例：为什么移项要变号？和在等式两边同时减一个数有什么区别？"
          />
          <span class="counter">{{ doubtContent.length }} / 1000</span>
          <span class="field-hint">尽量说清卡在哪一步，老师更容易给出具体回复。</span>
        </div>
        <div class="info-banner">
          <AppIcon name="shield" :size="15" />
          疑问只有本人和本班老师可见。首版不开放学生 AI 自由问答，老师回复才是正式答复。
        </div>
        <div v-if="doubtError" class="error-banner">
          <AppIcon name="alert-circle" :size="15" />
          {{ doubtError }}
        </div>
      </div>
      <template #footer>
        <button class="btn btn--ghost" type="button" @click="doubtOpen = false">取消</button>
        <button class="btn btn--primary" type="button" :disabled="submittingDoubt" @click="submitDoubt">
          <AppIcon v-if="submittingDoubt" name="refresh" :size="15" />
          <AppIcon v-else name="send" :size="15" />
          提交疑问
        </button>
      </template>
    </AppModal>
  </div>
</template>

<style scoped>
.handout-detail-page {
  max-width: 1180px;
}

.back-link {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-bottom: 12px;
  color: var(--ink-3);
  font-size: 9.5px;
}

.back-link::after {
  content: '';
  position: absolute;
  inset: -12px -8px;
}

.back-link:hover {
  color: var(--role-deep);
}

.handout-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px 13px;
  margin-top: 10px;
  color: var(--ink-3);
  font-size: 9px;
}

.meta-pill {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 7px;
  border-radius: 99px;
  color: var(--mint-deep);
  background: var(--mint-soft);
  font-size: 8px;
}

.private-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 9px;
  border: 1px solid #dde5fb;
  border-radius: 99px;
  color: var(--peri-deep);
  background: var(--peri-soft);
  font-size: 8px;
}

/* 进度带 */
.student-progress-band {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 22px;
  margin-bottom: 16px;
  padding: 18px 22px;
  border: 1px solid #dde6fb;
  border-radius: 18px;
  background:
    radial-gradient(circle at 92% 10%, rgba(255, 255, 255, 0.94), transparent 30%),
    linear-gradient(120deg, #fdfefe, #eff4ff 68%, #f7f3fd);
}

.band-kicker {
  color: var(--peri-deep);
  font-family: var(--font-mono);
  font-size: 7.5px;
  letter-spacing: 0.12em;
}

.progress-band__copy h2 {
  margin-top: 4px;
  font-family: var(--font-serif);
  font-size: 17px;
  font-weight: 600;
}

.band-stats {
  display: flex;
  gap: 16px;
  margin-top: 7px;
  color: var(--ink-3);
  font-size: 8.5px;
}

.band-stats b {
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 13px;
  font-weight: 600;
}

.band-progress {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: none;
}

.band-ring {
  position: relative;
  width: 76px;
  height: 76px;
}

.band-ring svg {
  width: 100%;
  height: 100%;
}

.band-ring > div {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.band-ring b {
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 19px;
  font-weight: 600;
  line-height: 1;
}

.band-ring b small {
  font-family: var(--font-body);
  font-size: 7px;
}

.band-ring span {
  margin-top: 2px;
  color: var(--ink-3);
  font-size: 7px;
}

.band-bars {
  display: flex;
  gap: 3px;
}

.band-bars span {
  display: grid;
  place-items: center;
  width: 21px;
  height: 21px;
  border-radius: 5px;
  color: var(--ink-3);
  background: rgba(255, 255, 255, 0.65);
  font-family: var(--font-numbers);
  font-size: 8px;
}

.band-bars span.is-done {
  color: var(--peri-deep);
  background: var(--peri-soft);
}

/* 布局 */
.handout-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 250px;
  gap: 15px;
  align-items: start;
}

.workbook {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.workbook-item {
  display: flex;
  gap: 12px;
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: #fff;
  box-shadow: var(--shadow-xs);
  scroll-margin-top: 86px;
}

.workbook-item.is-corrected {
  border-color: color-mix(in srgb, var(--mint) 14%, var(--line));
}

.item-number {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 30px;
  flex: none;
  padding-top: 2px;
}

.item-number .numbers {
  color: var(--ink-4);
  font-family: var(--font-numbers);
  font-size: 22px;
  font-weight: 500;
  line-height: 1;
}

.item-number small {
  margin-top: 3px;
  color: var(--ink-4);
  font-size: 7px;
}

.item-main {
  min-width: 0;
  flex: 1;
}

.item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}

.item-labels {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}

.item-labels :deep(.badge) {
  min-height: 19px;
  padding: 0 6px;
  font-size: 8px;
}

.item-labels .tag {
  padding: 1px 5px;
  font-size: 7.5px;
}

.teacher-reviewed {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 6px;
  border-radius: 4px;
  color: var(--mint-deep);
  background: var(--mint-soft);
  font-size: 7.5px;
}

.doubt-count {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--lav-deep);
  font-size: 8px;
}

.item-stem {
  margin-top: 8px;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 13px;
  font-weight: 500;
  line-height: 1.85;
  white-space: pre-wrap;
}

.item-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 5px;
  margin-top: 10px;
}

.item-options > div {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 9px;
  border: 1px solid var(--line);
  border-radius: 8px;
  color: var(--ink-2);
  font-size: 9.5px;
}

.item-options > div > span {
  display: grid;
  place-items: center;
  width: 17px;
  height: 17px;
  flex: none;
  border-radius: 4px;
  color: var(--ink-3);
  background: var(--surface-soft);
  font-family: var(--font-mono);
  font-size: 7.5px;
}

.item-options p {
  flex: 1;
}

.item-options em {
  flex: none;
  color: var(--mint-deep);
  font-size: 7px;
  font-style: normal;
}

.item-options em.mine-label {
  color: var(--coral-deep);
}

.item-options > div.is-correct {
  border-color: #cae8de;
  color: #347e6e;
  background: #f4fbf7;
}

.item-options > div.is-correct > span {
  color: var(--mint-deep);
  background: var(--mint-soft);
}

.item-options > div.is-mine {
  border-color: #f0d8d4;
  background: #fdf7f5;
}

.item-options > div.is-mine > span {
  color: var(--coral-deep);
  background: var(--coral-soft);
}

.answer-compare {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-top: 11px;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--surface-soft);
}

.compare-side span {
  display: block;
  color: var(--ink-4);
  font-size: 7px;
}

.compare-side b {
  display: block;
  margin-top: 1px;
  font-family: var(--font-serif);
  font-size: 12px;
  font-weight: 600;
}

.compare-side--mine b {
  color: var(--coral-deep);
}

.compare-side--correct b {
  color: var(--mint-deep);
}

.compare-arrow {
  color: var(--ink-4);
}

.compare-state {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
  color: var(--ink-4);
  font-size: 7.5px;
}

.teacher-commentary {
  margin-top: 12px;
  padding: 12px 14px;
  border: 1px solid #e0eafb;
  border-radius: 3px 10px 10px 3px;
  background:
    linear-gradient(90deg, rgba(107, 140, 245, 0.05), transparent 26%),
    #fbfcff;
}

.commentary-head {
  display: flex;
  align-items: center;
  gap: 6px;
}

.teacher-marker {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  flex: none;
  border-radius: 50%;
  color: var(--mint-deep);
  background: var(--mint-soft);
  font-family: var(--font-serif);
  font-size: 10px;
}

.commentary-head > div {
  flex: 1;
}

.commentary-head b {
  display: block;
  color: var(--ink-2);
  font-size: 9px;
}

.commentary-head div span {
  display: block;
  color: var(--ink-4);
  font-size: 7px;
}

.commentary-seal {
  padding: 1px 5px;
  border: 1px solid rgba(53, 139, 115, 0.28);
  border-radius: 3px;
  color: rgba(53, 139, 115, 0.55);
  font-family: var(--font-hand);
  font-size: 7px;
  transform: rotate(-4deg);
}

.teacher-commentary > p {
  margin-top: 7px;
  color: #3d4f64;
  font-family: var(--font-serif);
  font-size: 11px;
  line-height: 2;
  white-space: pre-wrap;
}

/* 订正历史 */
.correction-history {
  margin-top: 9px;
  border: 1px solid #e2efe9;
  border-radius: 8px;
  background: #fbfefc;
}

.history-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 7px 9px;
  color: var(--mint-deep);
  font-size: 8.5px;
}

.history-toggle > span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.history-toggle > span:last-child {
  color: var(--ink-3);
  font-size: 8px;
}

.history-body {
  padding: 0 10px 8px;
  border-top: 1px dashed #e2efe9;
}

.history-row {
  display: flex;
  gap: 7px;
  padding: 7px 0;
  border-bottom: 1px dashed #edf5f1;
}

.history-row:last-child {
  border-bottom: 0;
}

.history-version {
  color: var(--mint-deep);
  font-family: var(--font-mono);
  font-size: 7.5px;
}

.history-row p {
  color: var(--ink-2);
  font-size: 8.5px;
  line-height: 1.65;
}

.history-row div > span {
  display: block;
  margin-top: 2px;
  color: var(--ink-4);
  font-size: 7px;
}

.history-note {
  padding-top: 5px;
  color: var(--ink-4);
  font-size: 7px;
}

/* 疑问 */
.doubt-thread {
  margin-top: 9px;
  padding: 9px 11px;
  border: 1px solid #e9e4f6;
  border-radius: 9px;
  background: #fbfaff;
}

.doubt-entry + .doubt-entry {
  margin-top: 9px;
  padding-top: 9px;
  border-top: 1px dashed #e9e4f6;
}

.doubt-question-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.doubt-question-row > div {
  flex: 1;
}

.doubt-question-row b {
  display: block;
  color: var(--ink-2);
  font-size: 8.5px;
}

.doubt-question-row div span {
  display: block;
  color: var(--ink-4);
  font-size: 7px;
}

.doubt-question-row :deep(.badge) {
  min-height: 17px;
  padding: 0 5px;
  font-size: 7px;
}

.doubt-content {
  margin-top: 5px;
  color: var(--ink-2);
  font-family: var(--font-serif);
  font-size: 9.5px;
  line-height: 1.75;
}

.reply-bubble {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 6px;
  padding: 7px 8px;
  border-radius: 7px;
  background: var(--mint-wash);
}

.teacher-marker--sm {
  width: 19px;
  height: 19px;
  font-size: 8px;
}

.reply-bubble b {
  display: block;
  color: var(--mint-deep);
  font-size: 8px;
}

.reply-bubble p {
  margin-top: 1px;
  color: #4d6b60;
  font-size: 8.5px;
  line-height: 1.7;
}

.reply-bubble div > span {
  display: block;
  margin-top: 2px;
  color: var(--ink-4);
  font-size: 7px;
}

.item-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 11px;
  flex-wrap: wrap;
}

.item-actions .btn {
  font-size: 9px;
}

.action-note {
  color: var(--ink-4);
  font-size: 7.5px;
}

/* 侧栏 */
.detail-aside {
  position: sticky;
  top: 82px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.aside-card {
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: #fff;
}

.aside-kicker {
  color: var(--peri-deep);
  font-family: var(--font-mono);
  font-size: 7px;
  letter-spacing: 0.12em;
}

.aside-progress {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 7px;
}

.aside-ring {
  position: relative;
  width: 68px;
  height: 68px;
  flex: none;
}

.aside-ring svg {
  width: 100%;
  height: 100%;
}

.aside-ring b {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: var(--peri-deep);
  font-family: var(--font-numbers);
  font-size: 16px;
  font-weight: 600;
}

.aside-ring b small {
  font-family: var(--font-body);
  font-size: 6px;
}

.aside-progress > div:last-child b {
  display: block;
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: 14px;
  font-weight: 600;
}

.aside-progress > div:last-child span {
  display: block;
  margin-top: 2px;
  color: var(--ink-4);
  font-size: 7.5px;
}

.question-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 10px;
  padding-top: 9px;
  border-top: 1px dashed var(--line);
}

.question-nav button {
  position: relative;
  display: grid;
  place-items: center;
  width: 25px;
  height: 25px;
  border: 1px solid var(--line);
  border-radius: 6px;
  color: var(--ink-3);
  font-family: var(--font-numbers);
  font-size: 9px;
  transition: 0.18s;
}

.question-nav button:hover {
  border-color: var(--role);
  color: var(--role-deep);
}

.question-nav button.is-done {
  border-color: transparent;
  color: var(--mint-deep);
  background: var(--mint-soft);
}

.question-nav button.has-doubt::after {
  content: '';
  position: absolute;
  top: 2px;
  right: 2px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--lav);
}

.aside-icon {
  display: grid;
  place-items: center;
  width: 27px;
  height: 27px;
  margin-bottom: 6px;
  border-radius: 8px;
  color: var(--peri-deep);
  background: var(--peri-soft);
}

.rule-card h4,
.private-card h4 {
  color: var(--ink-2);
  font-size: 9.5px;
}

.rule-card p,
.private-card p {
  margin-top: 4px;
  color: var(--ink-3);
  font-size: 8.5px;
  line-height: 1.7;
}

.private-card {
  background: var(--surface-soft);
  box-shadow: none;
}

.private-card .aside-icon {
  color: var(--ink-3);
  background: #fff;
}

.ai-card {
  border-color: #e8e2f5;
  background: linear-gradient(145deg, #faf8fe, #f4f9f7);
}

.ai-head {
  display: flex;
  align-items: center;
  gap: 5px;
}

.ai-head > span {
  display: grid;
  place-items: center;
  width: 23px;
  height: 23px;
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
  color: var(--lav-deep);
  font-size: 6.5px;
  font-style: normal;
}

.ai-card > p {
  margin-top: 5px;
  color: var(--ink-3);
  font-size: 8px;
  line-height: 1.65;
}

.page-footnote {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  margin-top: 12px;
  padding: 0 4px;
  color: var(--ink-4);
  font-size: 8.5px;
  line-height: 1.6;
}

.page-footnote .icon {
  flex: none;
  margin-top: 1px;
}

/* 弹窗 */
.correction-form,
.doubt-form {
  display: flex;
  flex-direction: column;
  gap: 13px;
}

.correction-question {
  padding: 10px 12px;
  border-radius: 9px;
  background: var(--surface-soft);
}

.correction-question p {
  margin-top: 4px;
  color: var(--ink-2);
  font-family: var(--font-serif);
  font-size: 10.5px;
  line-height: 1.75;
}

.correction-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}

.correction-options label {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 9px;
  border: 1px solid var(--line);
  border-radius: 8px;
  color: var(--ink-2);
  font-size: 10px;
  cursor: pointer;
}

.correction-options label.is-selected {
  border-color: var(--role);
  color: var(--role-deep);
  background: var(--role-soft);
}

.correction-options label input {
  position: absolute;
  opacity: 0;
}

.option-key {
  display: grid;
  place-items: center;
  width: 19px;
  height: 19px;
  flex: none;
  border-radius: 5px;
  color: var(--ink-3);
  background: #fff;
  font-family: var(--font-mono);
  font-size: 8px;
}

.correction-options label.is-selected .option-key {
  color: var(--role-deep);
}

.truth-options {
  display: flex;
  gap: 8px;
}

.truth-options label {
  flex: 1;
  padding: 9px;
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

@media (max-width: 980px) {
  .handout-layout {
    grid-template-columns: 1fr;
  }

  .detail-aside {
    position: static;
    display: grid;
    grid-template-columns: 1fr 1fr;
  }

  .summary-card {
    grid-column: 1 / -1;
  }

  .ai-card {
    grid-column: 1 / -1;
  }
}

@media (max-width: 680px) {
  .student-progress-band {
    align-items: flex-start;
    flex-direction: column;
    padding: 15px;
  }

  .band-progress {
    width: 100%;
    justify-content: space-between;
  }

  .workbook-item {
    gap: 7px;
    padding: 14px;
  }

  .item-number {
    width: 23px;
  }

  .item-number .numbers {
    font-size: 19px;
  }

  .item-options {
    grid-template-columns: 1fr;
  }

  .answer-compare {
    flex-wrap: wrap;
  }

  .compare-state {
    width: 100%;
    margin-left: 0;
    padding-top: 4px;
    border-top: 1px dashed var(--line);
  }

  .detail-aside {
    display: flex;
  }

  .correction-options {
    grid-template-columns: 1fr;
  }

  .item-actions .btn--primary,
  .item-actions .btn--ghost {
    flex: 1;
  }
}
</style>
