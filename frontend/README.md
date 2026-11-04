# Vue 前端

Vue 3 + TypeScript + Vite + Pinia + Vue Router。

## 启动

```powershell
npm install
npm run dev:http
```

默认打开 `http://127.0.0.1:3020`，`/api` 代理到 `http://127.0.0.1:8080`。

后端端口不同时：

```powershell
$env:VITE_PROXY_TARGET="http://127.0.0.1:8082"
npm run dev:http
```

## 页面

- 教师端：总览、班级与学生、讲评模板、作业、题目、批改、统计、讲评审核、订正和疑问。
- 学生端：主页、作业与成绩、作业作答、错题本、错题讲评、订正和疑问。
- 教研端：知识点、公共模板审核和操作日志。

## 检查

```powershell
npm run typecheck
npm run build
```

Playwright 测试默认启动自己的 Vite 服务；联调已有服务时：

```powershell
$env:PLAYWRIGHT_BASE_URL="http://127.0.0.1:3020"
$env:PLAYWRIGHT_EXTERNAL_SERVER="1"
npm run test:e2e
```

截图输出到仓库 `docs/screenshots`。
