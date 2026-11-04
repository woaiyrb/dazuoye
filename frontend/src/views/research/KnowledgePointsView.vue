<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { researchApi } from '@/api/research'
import { errorMessage } from '@/api/errors'
import type { KnowledgePoint } from '@/types/models'
import { formatDateTime } from '@/utils/format'
import { useUiStore } from '@/stores/ui'
import PageHeader from '@/components/PageHeader.vue'
import AppIcon from '@/components/AppIcon.vue'
import AppModal from '@/components/AppModal.vue'
import AppEmpty from '@/components/AppEmpty.vue'
import StatusBadge from '@/components/StatusBadge.vue'

interface KpForm {
  level: 1 | 2
  parentId: number | null
  code: string
  name: string
}

const ui = useUiStore()

const loading = ref(true)
const error = ref('')
const chapters = ref<KnowledgePoint[]>([])
const query = ref('')
const showDisabled = ref(false)

const allPoints = computed(() => chapters.value.flatMap((chapter) => chapter.children ?? []))
const activePoints = computed(() => allPoints.value.filter((point) => point.status === 'ACTIVE'))
const disabledPoints = computed(() => allPoints.value.filter((point) => point.status === 'DISABLED'))
const activeChapters = computed(() => chapters.value.filter((chapter) => chapter.status === 'ACTIVE'))

const visibleChapters = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  return chapters.value
    .filter((chapter) => showDisabled.value || chapter.status === 'ACTIVE')
    .map((chapter) => {
      const chapterMatch =
        !keyword ||
        chapter.name.toLowerCase().includes(keyword) ||
        chapter.code.toLowerCase().includes(keyword)
      const children = (chapter.children ?? []).filter((point) => {
        if (!showDisabled.value && point.status === 'DISABLED') return false
        if (!keyword || chapterMatch) return true
        return (
          point.name.toLowerCase().includes(keyword) ||
          point.code.toLowerCase().includes(keyword)
        )
      })
      return { ...chapter, visibleChildren: children, chapterMatch }
    })
    .filter((chapter) => !keyword || chapter.chapterMatch || chapter.visibleChildren.length > 0)
})

const hasVisiblePoints = computed(() =>
  visibleChapters.value.some((chapter) => chapter.visibleChildren.length > 0),
)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    chapters.value = await researchApi.listKnowledgePoints(true)
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

const formOpen = ref(false)
const formMode = ref<'create' | 'rename'>('create')
const editing = ref<KnowledgePoint | null>(null)
const form = reactive<KpForm>({ level: 1, parentId: null, code: '', name: '' })
const formError = ref('')
const saving = ref(false)

function resetForm(): void {
  form.level = 1
  form.parentId = null
  form.code = ''
  form.name = ''
  formError.value = ''
  editing.value = null
}

function openCreate(level: 1 | 2 = 1, parent?: KnowledgePoint): void {
  resetForm()
  formMode.value = 'create'
  form.level = level
  if (level === 2 && parent) form.parentId = parent.id
  formOpen.value = true
}

function openRename(point: KnowledgePoint): void {
  resetForm()
  formMode.value = 'rename'
  editing.value = point
  form.name = point.name
  formOpen.value = true
}

function handleLevelChange(): void {
  if (form.level === 1) form.parentId = null
}

async function submitForm(): Promise<void> {
  formError.value = ''
  const name = form.name.trim()
  if (!name) {
    formError.value = '请填写知识点名称。'
    return
  }
  if (formMode.value === 'rename') {
    if (!editing.value) return
    saving.value = true
    try {
      await researchApi.updateKnowledgePoint(editing.value.id, name)
      ui.toast('success', '名称已更新', `知识点已重命名为「${name}」。历史统计口径保持不变。`)
      formOpen.value = false
      await load()
    } catch (cause) {
      formError.value = errorMessage(cause)
    } finally {
      saving.value = false
    }
    return
  }

  const code = form.code.trim().toUpperCase()
  if (!/^[A-Z0-9-]+$/.test(code)) {
    formError.value = '编码仅支持大写字母、数字与连字符，例如 KP-CH2-EQ。'
    return
  }
  if (form.level === 2 && !form.parentId) {
    formError.value = '二级知识点必须选择一个所属章节。'
    return
  }
  saving.value = true
  try {
    await researchApi.createKnowledgePoint({
      parentId: form.level === 1 ? null : form.parentId,
      code,
      name,
      kpLevel: form.level,
    })
    ui.toast(
      'success',
      '知识点已新增',
      form.level === 1 ? `已建立章节「${name}」。` : `已在所属章节下新增「${name}」。`,
    )
    formOpen.value = false
    await load()
  } catch (cause) {
    formError.value = errorMessage(cause)
  } finally {
    saving.value = false
  }
}

const disableOpen = ref(false)
const disableTarget = ref<KnowledgePoint | null>(null)
const disableNote = ref('')
const disableError = ref('')
const disabling = ref(false)

function openDisable(point: KnowledgePoint): void {
  disableTarget.value = point
  disableNote.value = ''
  disableError.value = ''
  disableOpen.value = true
}

async function confirmDisable(): Promise<void> {
  disableError.value = ''
  if (!disableTarget.value) return
  const note = disableNote.value.trim()
  if (!note) {
    disableError.value = '停用必须填写历史口径影响说明。'
    return
  }
  disabling.value = true
  try {
    await researchApi.disableKnowledgePoint(disableTarget.value.id, note)
    ui.toast('success', '知识点已停用', '历史统计保留原口径，新的题目将不能再选择该知识点。')
    disableOpen.value = false
    await load()
  } catch (cause) {
    disableError.value = errorMessage(cause)
  } finally {
    disabling.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="kp-page">
    <PageHeader
      eyebrow="RESEARCH · KNOWLEDGE CATALOGUE"
      title="知识点体系"
      subtitle="用稳定的编码维护统计口径。知识点的名称会进入教师选题与统计归因，但它从不被用来给学生贴标签。"
    >
      <template #actions>
        <label class="show-disabled">
          <input v-model="showDisabled" type="checkbox" />
          显示已停用
        </label>
        <button class="btn btn--primary" type="button" @click="openCreate(1)">
          <AppIcon name="plus" :size="15" />
          新增章节
        </button>
      </template>
    </PageHeader>

    <section class="catalogue-strip">
      <div class="catalogue-copy">
        <span class="catalogue-kicker">CATALOGUE / 2026</span>
        <p>知识点是讲评与统计的共同语言。</p>
        <span class="catalogue-note">编码保持稳定，名称可以修订；停用需要留下口径说明。</span>
      </div>
      <div class="catalogue-numbers">
        <div>
          <span>章节</span>
          <b>{{ chapters.length }}</b>
        </div>
        <div>
          <span>二级知识点</span>
          <b>{{ allPoints.length }}</b>
        </div>
        <div>
          <span>启用</span>
          <b class="mint">{{ activePoints.length }}</b>
        </div>
        <div>
          <span>已停用</span>
          <b class="muted-num">{{ disabledPoints.length }}</b>
        </div>
      </div>
      <span class="catalogue-glyph" aria-hidden="true">A—Z</span>
    </section>

    <div v-if="loading" class="card loading-card">
      <span class="skeleton" style="width: 240px; height: 10px" />
    </div>

    <div v-else-if="error" class="card">
      <AppEmpty icon="alert-circle" title="知识点加载失败" :description="error">
        <button class="btn btn--ghost btn--sm" @click="load">重试</button>
      </AppEmpty>
    </div>

    <template v-else>
      <div class="toolbar">
        <div class="input-icon toolbar-search">
          <AppIcon name="search" :size="16" />
          <input
            v-model="query"
            class="input"
            type="search"
            placeholder="搜索知识点名称或编码…"
            aria-label="搜索知识点"
          />
        </div>
        <span class="toolbar-hint">
          {{ query ? `找到 ${visibleChapters.length} 个章节` : `${activeChapters.length} 个启用章节` }}
          <span class="dot-sep" />
          编码创建后不可修改
        </span>
      </div>

      <div class="grid grid--main-side kp-layout">
        <div class="stack">
          <section
            v-for="(chapter, index) in visibleChapters"
            :key="chapter.id"
            class="card chapter-card"
            :class="{ 'is-disabled': chapter.status === 'DISABLED' }"
          >
            <header class="chapter-head">
              <span class="chapter-number">{{ String(index + 1).padStart(2, '0') }}</span>
              <div class="chapter-copy">
                <div class="chapter-title">
                  <h2>{{ chapter.name }}</h2>
                  <StatusBadge v-if="chapter.status === 'DISABLED'" status="DISABLED" />
                </div>
                <div class="chapter-sub">
                  <span class="mono">{{ chapter.code }}</span>
                  <span class="sub-sep" />
                  <span>{{ (chapter.children ?? []).length }} 个二级知识点</span>
                  <span class="sub-sep" />
                  <span>更新于 {{ formatDateTime(chapter.updatedAt) }}</span>
                </div>
              </div>
              <div class="chapter-actions">
                <button class="btn btn--soft btn--xs" type="button" @click="openCreate(2, chapter)">
                  <AppIcon name="plus" :size="12" />
                  添加二级
                </button>
                <button class="btn btn--quiet btn--xs" type="button" @click="openRename(chapter)">
                  <AppIcon name="pen" :size="12" />
                  重命名
                </button>
                <button
                  v-if="chapter.status === 'ACTIVE'"
                  class="btn btn--quiet btn--xs danger-link"
                  type="button"
                  @click="openDisable(chapter)"
                >
                  停用
                </button>
              </div>
            </header>

            <div v-if="chapter.visibleChildren.length" class="point-list">
              <article
                v-for="point in chapter.visibleChildren"
                :key="point.id"
                class="point-row"
                :class="{ 'is-disabled': point.status === 'DISABLED' }"
              >
                <span class="point-branch" aria-hidden="true" />
                <div class="point-main">
                  <div class="point-name">
                    <b>{{ point.name }}</b>
                    <StatusBadge v-if="point.status === 'DISABLED'" status="DISABLED" plain />
                  </div>
                  <div class="point-meta">
                    <span class="mono">{{ point.code }}</span>
                    <span class="sub-sep" />
                    <span>{{ point.status === 'ACTIVE' ? '可用于新题目' : '历史口径保留' }}</span>
                  </div>
                  <p v-if="point.disabledImpactNote" class="impact-note">
                    <AppIcon name="info" :size="11" />
                    {{ point.disabledImpactNote }}
                  </p>
                </div>
                <div class="point-actions">
                  <button class="btn btn--quiet btn--xs" type="button" @click="openRename(point)">
                    重命名
                  </button>
                  <button
                    v-if="point.status === 'ACTIVE'"
                    class="btn btn--quiet btn--xs danger-link"
                    type="button"
                    @click="openDisable(point)"
                  >
                    停用
                  </button>
                </div>
              </article>
            </div>
            <div v-else class="chapter-empty">
              <span>{{ query ? '该章节下没有匹配的知识点' : '还没有二级知识点' }}</span>
              <button type="button" @click="openCreate(2, chapter)">
                添加一个
                <AppIcon name="arrow-right" :size="11" />
              </button>
            </div>
          </section>

          <div v-if="!visibleChapters.length" class="card">
            <AppEmpty
              icon="layers"
              :title="query ? '没有匹配的知识点' : '还没有建立知识体系'"
              :description="
                query
                  ? '换一个关键词，或显示已停用的知识点再看。'
                  : '先新增一个章节，再在章节下配置二级知识点。'
              "
            >
              <button class="btn btn--primary btn--sm" type="button" @click="query ? (query = '') : openCreate(1)">
                {{ query ? '清空搜索' : '新增章节' }}
              </button>
            </AppEmpty>
          </div>

          <p v-if="visibleChapters.length && !hasVisiblePoints" class="list-note">
            {{ query ? '章节匹配，但尚未配置二级知识点。' : '当前仅显示章节，尚未配置二级知识点。' }}
          </p>
        </div>

        <aside class="stack">
          <section class="card guide-card">
            <span class="eyebrow">CATALOGUE NOTES</span>
            <h2>维护口径的三条约定</h2>
            <ol class="guide-list">
              <li>
                <span>01</span>
                <div>
                  <b>编码稳定，名称可读</b>
                  <p>编码用于历史数据对齐，创建后不再修改；名称面向教师和学生展示。</p>
                </div>
              </li>
              <li>
                <span>02</span>
                <div>
                  <b>先确认章节，再建二级</b>
                  <p>二级知识点必须挂在章节下，避免同一错误被归到两个口径。</p>
                </div>
              </li>
              <li>
                <span>03</span>
                <div>
                  <b>停用不等于删除</b>
                  <p>已停用知识点仍保留在历史统计中，但不再出现在新的题目配置里。</p>
                </div>
              </li>
            </ol>
          </section>

          <section class="card boundary-card">
            <span class="boundary-icon"><AppIcon name="shield" :size="19" /></span>
            <h2>这里不包含班级数据</h2>
            <p>
              知识点维护只涉及目录与编码。班级成绩、逐题批改、学生订正和个人疑问均不在教研视图中出现。
            </p>
            <div class="boundary-tags">
              <span>不展示学生姓名</span>
              <span>不展示成绩</span>
              <span>不展示答案</span>
            </div>
          </section>
        </aside>
      </div>
    </template>

    <AppModal
      v-model="formOpen"
      :title="formMode === 'create' ? '新增知识点' : '重命名知识点'"
      :subtitle="
        formMode === 'create'
          ? '一级章节用于组织，二级知识点用于统计归因与讲评选择。'
          : '编码保持不变，历史统计会继续按原编码归档。'
      "
      size="sm"
    >
      <div v-if="formError" class="error-banner modal-error">
        <AppIcon name="alert-circle" :size="15" />
        {{ formError }}
      </div>

      <form v-if="formMode === 'create'" class="stack--sm form-stack" @submit.prevent="submitForm">
        <div class="field">
          <label for="kp-level">层级</label>
          <select id="kp-level" v-model.number="form.level" class="select" @change="handleLevelChange">
            <option :value="1">一级 · 章节</option>
            <option :value="2">二级 · 知识点</option>
          </select>
        </div>
        <div v-if="form.level === 2" class="field">
          <label for="kp-parent">所属章节</label>
          <select id="kp-parent" v-model="form.parentId" class="select">
            <option :value="null">请选择章节</option>
            <option v-for="chapter in activeChapters" :key="chapter.id" :value="chapter.id">
              {{ chapter.name }}
            </option>
          </select>
        </div>
        <div class="field">
          <label for="kp-code">编码</label>
          <input
            id="kp-code"
            v-model="form.code"
            class="input mono"
            type="text"
            placeholder="例如 KP-CH2-EQ"
            maxlength="32"
            autocomplete="off"
            @input="form.code = form.code.toUpperCase()"
          />
          <span class="field-hint">仅大写字母、数字与连字符；创建后不可修改。</span>
        </div>
        <div class="field">
          <label for="kp-name">名称</label>
          <input
            id="kp-name"
            v-model="form.name"
            class="input"
            type="text"
            placeholder="例如 等式的性质"
            maxlength="40"
            autocomplete="off"
          />
          <span class="field-hint">面向师生展示，建议使用教材中的标准表述。</span>
        </div>
      </form>

      <form v-else class="stack--sm form-stack" @submit.prevent="submitForm">
        <div class="rename-card">
          <span class="mono">{{ editing?.code }}</span>
          <p>{{ editing?.parentId ? '二级知识点' : '一级章节' }}</p>
        </div>
        <div class="field">
          <label for="kp-rename">新名称</label>
          <input
            id="kp-rename"
            v-model="form.name"
            class="input"
            type="text"
            maxlength="40"
            autocomplete="off"
          />
          <span class="field-hint">历史统计会继续保留原编码，仅展示名称更新。</span>
        </div>
      </form>

      <template #footer>
        <button class="btn btn--ghost" type="button" @click="formOpen = false">取消</button>
        <button class="btn btn--primary" type="button" :disabled="saving" @click="submitForm">
          {{ saving ? '保存中…' : formMode === 'create' ? '创建知识点' : '保存名称' }}
        </button>
      </template>
    </AppModal>

    <AppModal
      v-model="disableOpen"
      title="停用知识点"
      subtitle="停用后不再用于新题目，但历史统计与已发布内容保持原口径。"
      size="sm"
      :close-on-backdrop="!disabling"
    >
      <div v-if="disableError" class="error-banner modal-error">
        <AppIcon name="alert-circle" :size="15" />
        {{ disableError }}
      </div>

      <div class="disable-summary">
        <span class="mono">{{ disableTarget?.code }}</span>
        <b>{{ disableTarget?.name }}</b>
        <span v-if="disableTarget?.children?.length" class="disable-warning">
          <AppIcon name="alert-triangle" :size="12" />
          该章节下还有 {{ disableTarget.children.length }} 个二级知识点，请确认影响范围。
        </span>
      </div>

      <div class="field">
        <label for="disable-note">历史口径影响说明</label>
        <textarea
          id="disable-note"
          v-model="disableNote"
          class="textarea"
          rows="4"
          maxlength="200"
          placeholder="例如：后续教学统一使用“移项与等式性质”，本知识点自 2026-10 起停用；历史作业仍按原口径统计。"
        />
        <span class="counter">{{ disableNote.length }} / 200</span>
        <span class="field-hint">必填。该说明会写入操作日志，停用操作不可在教师端撤销。</span>
      </div>

      <div class="info-banner info-banner--warm disable-note">
        <AppIcon name="info" :size="16" />
        停用不会删除历史数据；已发布讲评中的引用仍然有效。建议先确认没有正在进行的作业依赖它。
      </div>

      <template #footer>
        <button class="btn btn--ghost" type="button" :disabled="disabling" @click="disableOpen = false">
          取消
        </button>
        <button class="btn btn--danger-solid" type="button" :disabled="disabling" @click="confirmDisable">
          {{ disabling ? '停用中…' : '确认停用' }}
        </button>
      </template>
    </AppModal>
  </div>
</template>

<style scoped>
.kp-page {
  display: flex;
  flex-direction: column;
}

.show-disabled {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 4px;
  color: var(--ink-3);
  font-size: 11.5px;
  cursor: pointer;
  user-select: none;
}

.show-disabled input {
  width: 15px;
  height: 15px;
  accent-color: var(--apricot);
}

.catalogue-strip {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 22px;
  margin-bottom: 17px;
  padding: 17px 23px;
  overflow: hidden;
  border: 1px solid #eee2d1;
  border-radius: 18px;
  background:
    linear-gradient(100deg, rgba(255, 255, 255, 0.8), transparent 60%),
    repeating-linear-gradient(90deg, transparent 0 39px, rgba(174, 144, 99, 0.06) 39px 40px),
    #fbf5e9;
}

.catalogue-kicker {
  display: block;
  color: #a98756;
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.16em;
}

.catalogue-copy p {
  margin-top: 3px;
  color: #4d4131;
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 600;
}

.catalogue-note {
  display: block;
  margin-top: 2px;
  color: #958470;
  font-size: 9.5px;
}

.catalogue-numbers {
  display: flex;
  gap: 25px;
  flex: none;
  padding-left: 22px;
  border-left: 1px dashed rgba(169, 135, 86, 0.28);
}

.catalogue-numbers div {
  text-align: center;
}

.catalogue-numbers span {
  display: block;
  color: #9b896f;
  font-size: 8px;
}

.catalogue-numbers b {
  display: block;
  margin-top: 1px;
  color: #5c4a32;
  font-family: var(--font-numbers);
  font-size: 23px;
  font-weight: 600;
  line-height: 1.15;
}

.catalogue-numbers b.mint {
  color: var(--mint-deep);
}

.catalogue-numbers b.muted-num {
  color: #a99b89;
}

.catalogue-glyph {
  position: absolute;
  right: 14px;
  bottom: -17px;
  color: rgba(156, 107, 44, 0.045);
  font-family: var(--font-numbers);
  font-size: 74px;
  line-height: 1;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 13px;
  flex-wrap: wrap;
}

.toolbar-search {
  width: min(360px, 100%);
}

.toolbar-hint {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--ink-4);
  font-size: 9.5px;
}

.dot-sep {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.6;
}

.kp-layout {
  align-items: start;
}

.chapter-card {
  padding: 0;
  overflow: hidden;
}

.chapter-card.is-disabled {
  opacity: 0.78;
}

.chapter-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 17px 19px 14px;
  background: linear-gradient(110deg, #fffdf9, #fff 70%);
}

.chapter-number {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  flex: none;
  border: 1px solid #eee0ca;
  border-radius: 10px;
  color: #a57f47;
  background: #fdf7ec;
  font-family: var(--font-numbers);
  font-size: 14px;
}

.chapter-copy {
  min-width: 0;
  flex: 1;
}

.chapter-title {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
}

.chapter-title h2 {
  color: #3a342a;
  font-family: var(--font-serif);
  font-size: 16px;
  font-weight: 700;
}

.chapter-sub {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  color: var(--ink-4);
  font-size: 8.5px;
  flex-wrap: wrap;
}

.sub-sep {
  width: 2px;
  height: 2px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.6;
}

.chapter-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: none;
}

.danger-link {
  color: #b57a72;
}

.danger-link:hover {
  color: var(--coral-deep);
  background: var(--coral-soft);
}

.point-list {
  padding: 3px 19px 9px;
}

.point-row {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 11px 10px 11px 13px;
  border-top: 1px dashed var(--line);
  border-radius: 0 0 10px 10px;
  transition: background 0.18s;
}

.point-row:hover {
  background: #fcfdfc;
}

.point-row.is-disabled {
  opacity: 0.72;
}

.point-branch {
  position: relative;
  width: 10px;
  height: 16px;
  flex: none;
  margin-top: 2px;
  border-bottom: 1px solid #dfe9e5;
  border-left: 1px solid #dfe9e5;
  border-radius: 0 0 0 5px;
}

.point-main {
  min-width: 0;
  flex: 1;
}

.point-name {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.point-name b {
  color: var(--ink-2);
  font-size: 11.5px;
  font-weight: 600;
}

.point-meta {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 2px;
  color: var(--ink-4);
  font-size: 8.5px;
}

.point-actions {
  display: flex;
  gap: 2px;
  flex: none;
  opacity: 0.55;
  transition: opacity 0.18s;
}

.point-row:hover .point-actions {
  opacity: 1;
}

.impact-note {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  margin-top: 5px;
  padding: 5px 7px;
  border-radius: 6px;
  color: #967b56;
  background: #fdf8ef;
  font-size: 8.5px;
  line-height: 1.6;
}

.impact-note .icon {
  flex: none;
  margin-top: 1px;
}

.chapter-empty {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 0 19px 14px;
  padding: 11px 12px;
  border: 1px dashed #e6ece8;
  border-radius: 10px;
  color: var(--ink-4);
  font-size: 9.5px;
  background: #fcfdfc;
}

.chapter-empty button {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--apricot-deep);
  font-size: 9.5px;
  font-weight: 600;
}

.list-note {
  padding: 0 4px;
  color: var(--ink-4);
  font-size: 9.5px;
}

.guide-card {
  padding: 21px;
}

.guide-card .eyebrow {
  margin-bottom: 7px;
  font-size: 9px;
}

.guide-card h2 {
  font-family: var(--font-display);
  font-size: 23px;
  font-weight: 400;
  letter-spacing: 0.02em;
}

.guide-list {
  display: flex;
  flex-direction: column;
  gap: 13px;
  margin-top: 15px;
}

.guide-list li {
  display: flex;
  gap: 9px;
}

.guide-list li > span {
  padding-top: 1px;
  color: #c1a275;
  font-family: var(--font-numbers);
  font-size: 12px;
}

.guide-list b {
  display: block;
  color: var(--ink-2);
  font-size: 10.5px;
}

.guide-list p {
  margin-top: 2px;
  color: var(--ink-3);
  font-size: 9.5px;
  line-height: 1.7;
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

.boundary-card p {
  margin-top: 5px;
  color: var(--ink-3);
  font-size: 9.5px;
  line-height: 1.75;
}

.boundary-tags {
  display: flex;
  gap: 4px;
  margin-top: 11px;
  flex-wrap: wrap;
}

.boundary-tags span {
  padding: 2px 7px;
  border-radius: 999px;
  color: #8c6c42;
  background: #fdf6ea;
  font-size: 8px;
}

.form-stack {
  gap: 14px;
}

.rename-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--surface-soft);
}

.rename-card span {
  color: var(--apricot-deep);
}

.rename-card p {
  color: var(--ink-3);
  font-size: 9.5px;
}

.modal-error {
  margin-bottom: 14px;
}

.disable-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 15px;
  padding: 12px;
  border: 1px solid #f0e2cd;
  border-radius: 11px;
  background: #fdf8ef;
  flex-wrap: wrap;
}

.disable-summary > span:first-child {
  color: #a88656;
}

.disable-summary > b {
  color: #4b3f2e;
  font-size: 12px;
}

.disable-warning {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  color: #a76b5f;
  font-size: 9px;
}

.disable-note {
  margin-top: 14px;
}

@media (max-width: 1100px) {
  .chapter-actions .btn--quiet {
    padding: 2px 5px;
  }
}

@media (max-width: 860px) {
  .chapter-head {
    flex-wrap: wrap;
  }

  .chapter-actions {
    width: 100%;
    padding-left: 48px;
  }

  .point-actions {
    opacity: 1;
  }
}

@media (max-width: 720px) {
  .catalogue-strip {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
    padding: 16px 17px;
  }

  .catalogue-numbers {
    width: 100%;
    justify-content: space-between;
    gap: 10px;
    padding-top: 11px;
    padding-left: 0;
    border-top: 1px dashed rgba(169, 135, 86, 0.28);
    border-left: 0;
  }

  .catalogue-numbers b {
    font-size: 19px;
  }

  .toolbar-hint {
    display: none;
  }

  .chapter-head {
    padding: 14px;
  }

  .chapter-actions {
    gap: 4px;
    padding-left: 0;
    flex-wrap: wrap;
  }

  .point-list {
    padding: 2px 11px 7px;
  }

  .point-row {
    flex-wrap: wrap;
    gap: 5px;
  }

  .point-actions {
    width: 100%;
    padding-left: 19px;
  }

  .point-branch {
    display: none;
  }
}
</style>
