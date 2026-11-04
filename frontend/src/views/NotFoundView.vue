<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ROLE_META } from '@/types/labels'
import BrandMark from '@/components/BrandMark.vue'
import AppIcon from '@/components/AppIcon.vue'

const auth = useAuthStore()
const router = useRouter()

const homePath = computed(() => (auth.isAuthenticated ? auth.homePath : '/login'))
const homeLabel = computed(() => {
  if (!auth.isAuthenticated || !auth.role) return '回到登录'
  return `回到${ROLE_META[auth.role].portal}`
})
</script>

<template>
  <div class="notfound-page">
    <header class="notfound-top">
      <RouterLink to="/" class="brand-link" aria-label="错题星球首页">
        <BrandMark :size="38" />
        <span>
          <b>错题星球</b>
          <small>让每道错题都有回音</small>
        </span>
      </RouterLink>
      <span class="top-code mono">ERR · 404 / LOST PAGE</span>
    </header>

    <main class="notfound-main">
      <section class="notfound-copy">
        <span class="eyebrow">A PAGE OUT OF ORBIT</span>
        <h1>这道题<br /><em>不在卷面上。</em></h1>
        <p>
          你访问的页面可能已被移走，或链接本身少了一步。
          别担心，学习记录没有丢失——回到工作台，继续看你的错题。
        </p>
        <div class="notfound-actions">
          <RouterLink :to="homePath" class="btn btn--primary">
            {{ homeLabel }}
            <AppIcon name="arrow-right" :size="15" />
          </RouterLink>
          <button class="btn btn--ghost" type="button" @click="router.back()">
            <AppIcon name="arrow-left" :size="15" />
            返回上一页
          </button>
        </div>
        <div class="notfound-note">
          <AppIcon name="info" :size="13" />
          <span>如果是从讲评链接进入，请联系本班老师重新获取入口。</span>
        </div>
      </section>

      <div class="notfound-art" aria-hidden="true">
        <div class="orbit orbit--outer" />
        <div class="orbit orbit--inner" />
        <span class="planet planet--one" />
        <span class="planet planet--two" />
        <span class="planet planet--three" />
        <div class="number-404">
          <span>4</span><span class="zero">0</span><span>4</span>
        </div>
        <div class="lost-note">
          <span class="lost-note__pin" />
          <span class="lost-note__kicker">LOST &amp; FOUND</span>
          <b>页面未找到</b>
          <p>这道题被风吹走了，<br />但你的订正还在。</p>
        </div>
        <span class="paper-plane">
          <AppIcon name="send" :size="24" :stroke-width="1.4" />
        </span>
        <span class="art-caption mono">x + ? = 404</span>
      </div>
    </main>

    <footer class="notfound-foot">
      <span>错题星球 · 数学讲评与错题跟踪助手</span>
      <span class="foot-dot" />
      <span>仅使用脱敏虚构数据</span>
    </footer>
  </div>
</template>

<style scoped>
.notfound-page {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  overflow: hidden;
  background:
    radial-gradient(circle at 78% 34%, rgba(216, 231, 255, 0.55), transparent 28%),
    radial-gradient(circle at 18% 82%, rgba(214, 160, 95, 0.11), transparent 24%),
    linear-gradient(145deg, #f8fcfa 0%, #f0f7f4 52%, #edf4fb 100%);
}

.notfound-page::before {
  content: '';
  position: absolute;
  inset: 0;
  opacity: 0.35;
  background-image:
    linear-gradient(rgba(126, 158, 147, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(126, 158, 147, 0.08) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(circle at 50% 45%, #000 20%, transparent 78%);
  pointer-events: none;
}

.notfound-top {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 24px clamp(22px, 5vw, 64px);
}

.brand-link {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-link span b {
  display: block;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 400;
  letter-spacing: 0.06em;
}

.brand-link span small {
  display: block;
  margin-top: 2px;
  color: var(--ink-3);
  font-size: 8.5px;
  letter-spacing: 0.06em;
}

.top-code {
  color: rgba(97, 122, 113, 0.45);
  font-size: 8px;
  letter-spacing: 0.16em;
}

.notfound-main {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 0.95fr) minmax(360px, 1.05fr);
  align-items: center;
  gap: clamp(30px, 5vw, 80px);
  width: min(1180px, 100%);
  margin: auto;
  padding: 30px clamp(22px, 5vw, 64px) 42px;
  flex: 1;
}

.notfound-copy {
  max-width: 510px;
}

.notfound-copy .eyebrow {
  color: var(--mint-deep);
  font-size: 9.5px;
}

.notfound-copy h1 {
  margin-top: 6px;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: clamp(47px, 6.6vw, 84px);
  font-weight: 400;
  letter-spacing: 0.005em;
  line-height: 1.16;
}

.notfound-copy h1 em {
  position: relative;
  color: var(--mint-deep);
  font-style: normal;
}

.notfound-copy h1 em::after {
  content: '';
  position: absolute;
  right: 0;
  bottom: 7px;
  left: 2px;
  height: 10px;
  border-radius: 50%;
  background: rgba(47, 184, 173, 0.14);
  transform: rotate(-1.2deg);
  z-index: -1;
}

.notfound-copy > p {
  max-width: 43ch;
  margin-top: 19px;
  color: var(--ink-2);
  font-size: 13.5px;
  line-height: 1.95;
}

.notfound-actions {
  display: flex;
  gap: 9px;
  margin-top: 25px;
  flex-wrap: wrap;
}

.notfound-note {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  margin-top: 19px;
  color: var(--ink-4);
  font-size: 9px;
  line-height: 1.6;
}

.notfound-note .icon {
  flex: none;
  margin-top: 1px;
}

.notfound-art {
  position: relative;
  min-height: 440px;
  transform: translateY(-8px);
}

.orbit {
  position: absolute;
  border: 1px dashed rgba(100, 127, 117, 0.2);
  border-radius: 50%;
}

.orbit--outer {
  top: 50%;
  left: 50%;
  width: min(450px, 100%);
  aspect-ratio: 1;
  transform: translate(-50%, -50%) rotate(-18deg);
  animation: breathe 9s ease-in-out infinite;
}

.orbit--inner {
  top: 50%;
  left: 50%;
  width: min(320px, 72%);
  aspect-ratio: 1;
  transform: translate(-50%, -50%) rotate(13deg);
  border-style: solid;
  border-color: rgba(107, 140, 245, 0.12);
}

.number-404 {
  position: absolute;
  top: 50%;
  left: 50%;
  display: flex;
  align-items: baseline;
  gap: 3px;
  color: var(--ink);
  font-family: var(--font-numbers);
  font-size: clamp(120px, 15vw, 184px);
  font-weight: 500;
  letter-spacing: -0.075em;
  line-height: 0.8;
  transform: translate(-50%, -53%);
  user-select: none;
}

.number-404 span {
  display: block;
  color: rgba(62, 91, 80, 0.9);
  text-shadow: 0 18px 38px rgba(50, 82, 70, 0.1);
}

.number-404 .zero {
  position: relative;
  color: transparent;
  -webkit-text-stroke: 1.5px rgba(47, 184, 173, 0.55);
}

.number-404 .zero::after {
  content: '';
  position: absolute;
  top: 54%;
  left: 51%;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--apricot);
  box-shadow: 0 0 0 7px rgba(214, 160, 95, 0.12);
  transform: translate(-50%, -50%);
}

.planet {
  position: absolute;
  border-radius: 50%;
}

.planet--one {
  top: 45px;
  right: 67px;
  width: 19px;
  height: 19px;
  background: linear-gradient(140deg, #b9c9fb, var(--peri));
  box-shadow: 0 0 0 8px rgba(107, 140, 245, 0.07);
}

.planet--two {
  bottom: 77px;
  left: 38px;
  width: 11px;
  height: 11px;
  background: var(--apricot);
  box-shadow: 0 0 0 6px rgba(214, 160, 95, 0.1);
}

.planet--three {
  top: 112px;
  left: 41px;
  width: 7px;
  height: 7px;
  background: var(--lav);
  opacity: 0.6;
}

.lost-note {
  position: absolute;
  right: 4px;
  bottom: 38px;
  width: 196px;
  padding: 16px 16px 15px;
  border: 1px solid rgba(174, 146, 105, 0.25);
  border-radius: 3px;
  background:
    repeating-linear-gradient(0deg, transparent 0 22px, rgba(174, 144, 99, 0.07) 22px 23px),
    #fffdf8;
  box-shadow: 0 24px 44px -30px rgba(68, 57, 36, 0.45);
  transform: rotate(-4deg);
}

.lost-note__pin {
  position: absolute;
  top: -7px;
  left: 50%;
  width: 13px;
  height: 13px;
  border: 3px solid #f4c9a1;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 2px 5px rgba(90, 68, 40, 0.15);
  transform: translateX(-50%);
}

.lost-note__kicker {
  color: #b39162;
  font-family: var(--font-mono);
  font-size: 6.5px;
  letter-spacing: 0.14em;
}

.lost-note b {
  display: block;
  margin-top: 5px;
  color: #4b3e2d;
  font-family: var(--font-serif);
  font-size: 14px;
}

.lost-note p {
  margin-top: 4px;
  color: #93826c;
  font-family: var(--font-hand);
  font-size: 10.5px;
  line-height: 1.7;
}

.paper-plane {
  position: absolute;
  top: 88px;
  right: 167px;
  display: grid;
  width: 45px;
  height: 45px;
  place-items: center;
  color: var(--peri-deep);
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(107, 140, 245, 0.16);
  border-radius: 50%;
  box-shadow: 0 12px 24px -16px rgba(75, 101, 170, 0.5);
  transform: rotate(18deg);
  animation: float 5s ease-in-out infinite;
}

.art-caption {
  position: absolute;
  bottom: 0;
  left: 14px;
  color: rgba(104, 127, 119, 0.42);
  font-size: 8px;
  letter-spacing: 0.12em;
}

.notfound-foot {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 clamp(22px, 5vw, 64px) 23px;
  color: var(--ink-4);
  font-size: 8.5px;
}

.foot-dot {
  width: 2px;
  height: 2px;
  border-radius: 50%;
  background: currentColor;
}

@keyframes float {
  0%,
  100% {
    transform: translateY(0) rotate(18deg);
  }
  50% {
    transform: translateY(-9px) rotate(12deg);
  }
}

@keyframes breathe {
  0%,
  100% {
    transform: translate(-50%, -50%) rotate(-18deg) scale(1);
  }
  50% {
    transform: translate(-50%, -50%) rotate(-15deg) scale(1.02);
  }
}

@media (max-width: 900px) {
  .notfound-main {
    grid-template-columns: 1fr;
    align-items: start;
    gap: 10px;
  }

  .notfound-copy {
    max-width: 580px;
  }

  .notfound-art {
    width: min(470px, 100%);
    min-height: 360px;
    margin: 0 auto;
    transform: none;
  }

  .number-404 {
    font-size: clamp(110px, 24vw, 160px);
  }

  .lost-note {
    right: 10px;
    bottom: 20px;
  }
}

@media (max-width: 600px) {
  .notfound-top {
    padding-top: 18px;
  }

  .top-code {
    display: none;
  }

  .notfound-main {
    padding-top: 18px;
  }

  .notfound-copy h1 {
    font-size: 48px;
  }

  .notfound-copy > p {
    font-size: 12.5px;
  }

  .notfound-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .notfound-actions .btn {
    width: 100%;
  }

  .notfound-art {
    min-height: 300px;
  }

  .number-404 {
    font-size: 110px;
  }

  .lost-note {
    right: 0;
    bottom: 0;
    width: 166px;
    padding: 13px;
  }

  .paper-plane {
    top: 50px;
    right: 58px;
    width: 38px;
    height: 38px;
  }

  .planet--one {
    top: 23px;
    right: 20px;
  }

  .planet--two {
    bottom: 42px;
    left: 12px;
  }

  .art-caption {
    display: none;
  }

  .notfound-foot {
    flex-wrap: wrap;
  }
}
</style>
