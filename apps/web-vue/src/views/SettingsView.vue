<script setup>
import { Bot, Database, Server, ShieldCheck } from "@lucide/vue";
import { onMounted, ref } from "vue";
import PageHeader from "../components/PageHeader.vue";
import SectionCard from "../components/SectionCard.vue";
import { getAiStatus } from "../services/api";

const aiStatus = ref(null);
const javaStatus = ref(null);

onMounted(async () => {
  aiStatus.value = await getAiStatus();
  javaStatus.value = await fetch("/java-api/ai/status", {
    credentials: "include",
  })
    .then((response) => (response.ok ? response.json() : null))
    .catch(() => null);
});
</script>

<template>
  <div class="page">
    <PageHeader
      eyebrow="系统状态"
      title="设置与服务"
      description="查看当前前端、Java 网关和 AI 服务的运行状态。"
    />

    <div class="settings-layout">
      <SectionCard title="AI 讲评能力" description="AI 只负责教学分析和草稿生成">
        <div class="status-grid">
          <div class="status-item">
            <span class="status-icon"><Bot :size="18" /></span>
            <div><strong>错误模式分析</strong><p>{{ aiStatus?.grading ? "已开放" : "未开放" }}</p></div>
            <span class="status-dot" :class="{ online: aiStatus?.grading }" />
          </div>
          <div class="status-item">
            <span class="status-icon"><Bot :size="18" /></span>
            <div><strong>讲评草稿生成</strong><p>{{ aiStatus?.authoring ? "已开放" : "未开放" }}</p></div>
            <span class="status-dot" :class="{ online: aiStatus?.authoring }" />
          </div>
          <div class="status-item">
            <span class="status-icon"><Bot :size="18" /></span>
            <div><strong>教师手工讲评</strong><p>始终可用</p></div>
            <span class="status-dot online" />
          </div>
        </div>
      </SectionCard>

      <SectionCard title="Java 后端" description="新架构网关与 DeepTutor 连通状态">
        <div class="status-grid">
          <div class="status-item">
            <span class="status-icon"><Server :size="18" /></span>
            <div><strong>Spring Boot 网关</strong><p>{{ javaStatus ? "运行正常" : "未启动，请运行 start-vue-java.bat" }}</p></div>
            <span class="status-dot" :class="{ online: javaStatus }" />
          </div>
          <div class="status-item">
            <span class="status-icon"><Database :size="18" /></span>
            <div><strong>DeepTutor</strong><p>{{ javaStatus?.deepTutorState || "等待检测" }}</p></div>
            <span class="status-dot" :class="{ online: javaStatus?.deepTutorReady }" />
          </div>
          <div class="status-item">
            <span class="status-icon"><ShieldCheck :size="18" /></span>
            <div><strong>旧后端兼容代理</strong><p>{{ javaStatus?.legacyApi || "已配置" }}</p></div>
            <span class="status-dot online" />
          </div>
        </div>
      </SectionCard>

      <SectionCard title="界面偏好" description="当前设计系统设置">
        <div class="preference-list">
          <div><span>界面语言</span><strong>简体中文</strong></div>
          <div><span>主题模式</span><strong>浅色</strong></div>
          <div><span>字体</span><strong>Noto Sans SC / 系统中文字体</strong></div>
          <div><span>响应式布局</span><strong>手机、平板、桌面</strong></div>
        </div>
      </SectionCard>
    </div>
  </div>
</template>

<style scoped>
.settings-layout {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

.settings-layout > :last-child {
  grid-column: 1 / -1;
}

.status-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.status-item {
  display: flex;
  align-items: center;
  gap: 11px;
  border: 1px solid var(--border);
  border-radius: 13px;
  background: var(--surface-soft);
  padding: 12px;
}

.status-icon {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  place-items: center;
  border-radius: 10px;
  background: white;
  color: var(--primary);
}

.status-item div {
  flex: 1;
}

.status-item strong {
  font-size: 12px;
}

.status-item p {
  color: var(--text-muted);
  font-size: 10px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 99px;
  background: var(--ink-4);
}

.status-dot.online {
  background: var(--success);
  box-shadow: 0 0 0 4px var(--success-soft);
}

.preference-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 24px;
}

.preference-list div {
  display: flex;
  min-height: 44px;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border);
  gap: 16px;
  font-size: 11px;
}

.preference-list span {
  color: var(--text-muted);
}

@media (max-width: 800px) {
  .settings-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .settings-layout > :last-child {
    grid-column: auto;
  }
}
</style>
