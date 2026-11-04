<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { teacherApi } from '@/api/teacher'
import { errorMessage } from '@/api/errors'
import type { PublicTemplate } from '@/types/models'
import { useUiStore } from '@/stores/ui'
import { formatDateTime } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'
import AppIcon from '@/components/AppIcon.vue'
import AppEmpty from '@/components/AppEmpty.vue'
import StatusBadge from '@/components/StatusBadge.vue'

const ui = useUiStore()
const loading = ref(true)
const error = ref('')
const approved = ref<PublicTemplate[]>([])
const mine = ref<PublicTemplate[]>([])
const submitting = ref(false)
const formError = ref('')
const form = reactive({
  title: '',
  content: '',
  sections: '',
})

const sectionList = computed(() =>
  form.sections
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean),
)
const pendingCount = computed(() => mine.value.filter((item) => item.status === 'PENDING').length)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [approvedList, mineList] = await Promise.all([
      teacherApi.listApprovedTemplates(),
      teacherApi.listMyTemplates(),
    ])
    approved.value = approvedList
    mine.value = mineList
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

async function submit(): Promise<void> {
  formError.value = ''
  if (!form.title.trim() || !form.content.trim()) {
    formError.value = '请填写模板标题和说明。'
    return
  }
  if (!sectionList.value.length) {
    formError.value = '至少填写一个结构槽位。'
    return
  }
  submitting.value = true
  try {
    await teacherApi.createTemplate({
      title: form.title.trim(),
      content: form.content.trim(),
      sections: sectionList.value,
    })
    ui.toast('success', '模板已提交', '模板进入教研审核，通过后可在生成讲评时引用。')
    form.title = ''
    form.content = ''
    form.sections = ''
    await load()
  } catch (cause) {
    formError.value = errorMessage(cause)
  } finally {
    submitting.value = false
  }
}

function sectionsOf(template: PublicTemplate): string[] {
  const raw = template.structure?.sections
  return Array.isArray(raw) ? raw.filter((item): item is string => typeof item === 'string') : []
}

onMounted(load)
</script>

<template>
  <div class="teacher-templates-page">
    <PageHeader
      eyebrow="TEACHER · PUBLIC TEMPLATES"
      title="讲评模板"
      subtitle="把可复用的讲评结构提交给教研审核；只有审核通过的模板才能用于生成讲评草稿。"
    >
      <template #actions>
        <span class="template-count">已通过 {{ approved.length }} · 待审 {{ pendingCount }}</span>
      </template>
    </PageHeader>

    <div v-if="loading" class="card loading-card">
      <span class="skeleton" style="width: 220px; height: 10px" />
    </div>
    <div v-else-if="error" class="card">
      <AppEmpty icon="alert-circle" title="模板加载失败" :description="error">
        <button class="btn btn--ghost btn--sm" type="button" @click="load">重试</button>
      </AppEmpty>
    </div>
    <template v-else>
      <div class="template-layout">
        <section class="card submit-card">
          <div class="section-head">
            <div>
              <h3 class="section-title">提交新模板</h3>
              <p class="section-desc">结构槽位每行一个，例如：错误定位、原题依据、正确思路、订正建议。</p>
            </div>
            <AppIcon name="sparkles" :size="18" />
          </div>
          <div class="stack-form">
            <label class="field">
              <span>模板标题</span>
              <input v-model="form.title" class="input" maxlength="64" placeholder="例：去括号错误讲评模板" />
            </label>
            <label class="field field--grow">
              <span>模板说明</span>
              <textarea
                v-model="form.content"
                class="textarea"
                maxlength="2000"
                placeholder="说明适用题型、讲评顺序和措辞边界；不得评价学生能力或态度。"
              />
            </label>
            <label class="field field--grow">
              <span>结构槽位（每行一个）</span>
              <textarea
                v-model="form.sections"
                class="textarea"
                placeholder="错误定位&#10;原题依据&#10;正确思路&#10;订正建议"
              />
            </label>
            <div v-if="formError" class="error-banner">{{ formError }}</div>
            <button class="btn btn--primary btn--sm" type="button" :disabled="submitting" @click="submit">
              <AppIcon name="send" :size="14" />
              {{ submitting ? '提交中…' : '提交审核' }}
            </button>
          </div>
        </section>

        <section class="card mine-card">
          <div class="section-head">
            <div>
              <h3 class="section-title">我的投稿</h3>
              <p class="section-desc">审核状态由教研端更新；被退回的模板可修改后重新提交。</p>
            </div>
          </div>
          <div v-if="mine.length" class="template-list">
            <article v-for="template in mine" :key="template.id" class="template-row">
              <div class="template-row__head">
                <b>{{ template.title }}</b>
                <StatusBadge :status="template.status" />
              </div>
              <p>{{ template.content || '未填写模板说明' }}</p>
              <div class="template-row__meta">
                <span>{{ sectionsOf(template).length }} 个槽位</span>
                <span>{{ formatDateTime(template.createdAt) }}</span>
              </div>
              <div v-if="template.reviewNote" class="review-note">{{ template.reviewNote }}</div>
            </article>
          </div>
          <AppEmpty v-else icon="file" title="还没有投稿" description="提交模板后，这里会显示审核状态。" />
        </section>

        <section class="card approved-card">
          <div class="section-head">
            <div>
              <h3 class="section-title">可用模板</h3>
              <p class="section-desc">审核通过的模板会出现在讲评生成页，可被引用为依据。</p>
            </div>
          </div>
          <div v-if="approved.length" class="template-list">
            <article v-for="template in approved" :key="template.id" class="template-row">
              <div class="template-row__head">
                <b>{{ template.title }}</b>
                <StatusBadge :status="template.status" />
              </div>
              <p>{{ template.content || '未填写模板说明' }}</p>
              <div class="template-row__meta">
                <span>{{ sectionsOf(template).length }} 个槽位</span>
                <span>审核通过</span>
              </div>
            </article>
          </div>
          <AppEmpty v-else icon="file" title="暂无可用模板" description="等待教研审核通过后即可引用。" />
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.teacher-templates-page {
  max-width: 1220px;
}

.template-count {
  color: var(--ink-3);
  font-size: 8px;
}

.template-layout {
  display: grid;
  grid-template-columns: minmax(300px, 0.9fr) minmax(0, 1.1fr);
  gap: 14px;
  align-items: stretch;
}

.submit-card,
.mine-card,
.approved-card {
  padding: 18px;
}

.submit-card {
  display: flex;
  flex-direction: column;
  grid-row: 1 / span 2;
}

.mine-card,
.approved-card {
  grid-column: 2;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.stack-form {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 11px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 5px;
  color: var(--ink-3);
  font-size: 8px;
}

.field--grow {
  flex: 1;
}

.field--grow textarea {
  flex: 1;
  min-height: 84px;
}

.template-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.template-row {
  padding: 11px 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
}

.template-row__head,
.template-row__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.template-row__head b {
  color: var(--ink);
  font-size: 10px;
}

.template-row p {
  margin: 6px 0;
  color: var(--ink-3);
  font-size: 9px;
  line-height: 1.65;
}

.template-row__meta {
  color: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 7px;
}

.review-note {
  margin-top: 7px;
  padding: 7px 9px;
  border-radius: 8px;
  color: var(--apricot-deep);
  background: var(--surface-apricot);
  font-size: 8px;
  line-height: 1.6;
}

@media (max-width: 900px) {
  .template-layout {
    grid-template-columns: 1fr;
  }

  .submit-card {
    grid-row: auto;
  }

  .mine-card,
  .approved-card {
    grid-column: 1;
  }
}
</style>
