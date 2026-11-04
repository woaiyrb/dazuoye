# Mark Vue 前端

使用 Vue 3、HTML 模板、CSS 和 JavaScript，不使用 React/TypeScript。

## 页面

- `/login`：中文登录与角色入口。
- `/dashboard`：教师工作台。
- `/author/:assignmentId`：作业和选择题、判断题概览。
- `/grading/:assignmentId`：确定性判分与批改统计。
- `/review/:assignmentId`：AI 讲评草稿、教师审核和发布。
- `/corrections`：学生订正跟踪。
- `/students`：班级学生。
- `/knowledge-points`：知识点。
- `/settings`：Java、DeepTutor 与 AI 状态。

## 启动

在项目根目录运行：

```text
start-vue-java.bat
```

或分别运行：

```powershell
npm install --workspaces=false --legacy-peer-deps --ignore-scripts
npm run dev
```

开发服务器：<http://localhost:3020/>

`/api` 与 `/java-api` 会代理到 `http://127.0.0.1:8080` 的 Java 服务。
