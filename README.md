# 初中作业自动批改系统

> 新版初中数学作业 AI 平台的 MVP 边界、Java/Vue 实施状态和待确认规则见
> [docs/MVP_REQUIREMENTS_IMPLEMENTATION.md](./docs/MVP_REQUIREMENTS_IMPLEMENTATION.md)。

面向初中场景的 AI 作业批改平台：出题、答题、自动批改、讲评反馈一站式完成。
本地自包含运行包——源码、依赖、内置 MySQL 与 Redis 全部就绪，双击即可启动。

> 详细使用说明见 [使用说明.md](./使用说明.md)

## 快速开始

```bash
# Windows 下双击 start-dev.bat，或：
./start-dev.bat           # 启动 DeepTutor → MySQL → Redis → API → 网关 → 前端 → Worker
./stop-dev.bat            # 停止全部服务
```

启动后浏览器自动打开 http://localhost:3010/（本地已关闭登录，直接可用）。

新版 Vue + Java 预览：

```bash
./start-vue-java.bat
```

- Vue 前端：http://localhost:3020/
- Java 网关：http://localhost:8080/health

| 用途 | 地址 |
| --- | --- |
| 首页 / 登录 | http://localhost:3010/ |
| 教师工作台 | http://localhost:3010/dashboard |
| 出题端 | http://localhost:3010/author/1 |
| 答题端 | http://localhost:3010/learner/1 |
| 接口网关 | http://localhost:8000 |
| 后端 API | http://localhost:4222 |

## 核心功能

- **AI 自动批改** —— 全部 AI 能力统一走 DeepTutor（批改、出题、评分标准、翻译、内容审核、AI 聊天助手、语音转写）
- **班级与学生管理** —— 花名册、知识点、成绩一览
- **多语言** —— 题目与反馈翻译
- **实时进度** —— 批改任务与学生作答状态跟踪

## 技术栈

| 层 | 技术 |
| --- | --- |
| 前端 | Next.js (React) |
| 后端 | NestJS (TypeScript), Turborepo monorepo |
| 数据库 | MySQL 8.0 + Prisma ORM（内置绿色版，端口 6001） |
| 缓存/队列 | Redis（内置） |
| AI | DeepTutor（FastAPI + WebSocket 2.0） |

默认连接本机 DeepTutor：`http://127.0.0.1:8001`。项目后端启动后会在
DeepTutor 未运行且连接失败时尝试自动恢复一次。

## 目录结构

```
apps/        web(前端) / api(后端) / api-gateway(网关) / jobs(异步任务)
packages/    共享库
scripts/     本地启动与工具脚本
tests/       Playwright 端到端测试
```

## 许可证

Apache-2.0，见 [LICENSE](./LICENSE)。
