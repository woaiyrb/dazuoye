# Java 后端

Spring Boot 3 + Java 17 + MySQL 8 + Flyway。

## 启动

```powershell
mvn test
mvn spring-boot:run
```

默认端口 `8080`，健康检查：

```text
GET http://127.0.0.1:8080/health
GET http://127.0.0.1:8080/health/database
```

## 数据库

默认连接：

```text
jdbc:mysql://127.0.0.1:6001/mark
username: mark
password: markpassword
```

可通过以下环境变量覆盖：

```text
JAVA_DATABASE_URL
JAVA_DATABASE_USERNAME
JAVA_DATABASE_PASSWORD
JAVA_API_PORT
```

Flyway 迁移位于 `src/main/resources/db/migration`，启动时自动执行。主要持久化内容：

- 用户、班级、作业、题目、知识点与讲评模板。
- 手工/表格批改批次和逐题结果。
- 学生提交快照、逐题答案、总分与错题快照。
- AI 题目草稿、AI 讲评草稿和教师确认版本。
- 学生订正、疑问、回复、操作日志和后端事件日志。

## 主要接口

- `POST /api/auth/session`：三端登录。
- `GET/POST /api/teacher/classes`：班级与学生管理。
- `GET/POST /api/teacher/homeworks`：作业与题目管理。
- `POST /api/teacher/homeworks/{id}/ai-questions/generate`：DeepTutor 候选题。
- `POST /api/teacher/homeworks/{id}/handouts/generate`：AI 讲评草稿。
- `GET /api/student/homeworks`：已发布作业。
- `POST /api/student/homeworks/{id}/submit`：提交并自动判分。
- `GET /api/student/score-summary`：学生个人总分。
- `GET /api/student/wrong-book`：学生个人错题本。
- `GET /api/research/operation-logs`：脱敏审计日志。

学生题目接口不会返回 `standardAnswer` 或 `teacherAnalysis`；这些字段只在提交后的结果中按本人记录返回。

## DeepTutor

DeepTutor 默认地址为 `http://127.0.0.1:8001`。调用采用 WebSocket 协议，单次请求会自动重试最多 3 次，并使用指数退避。批量题目改为一次直接 JSON 生成；讲评草稿最多 3 路并发生成。
