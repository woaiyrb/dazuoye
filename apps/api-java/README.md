# Java 后端（MVP 迁移中）

## 运行

```powershell
cd D:\初中作业自动批改系统\apps\api-java
mvn -s .mvn/settings.xml test
mvn -s .mvn/settings.xml -DskipTests package
java -jar target\mark-api-java-0.1.0.jar
```

默认端口：`8080`。

Java 默认连接现有 MySQL：

```text
jdbc:mysql://127.0.0.1:6001/mark
username: mark
password: markpassword
```

可通过 `JAVA_DATABASE_URL`、`JAVA_DATABASE_USERNAME`、
`JAVA_DATABASE_PASSWORD` 覆盖。

## 已实现

- `GET /health`：Java 服务健康检查。
- `GET /health/database`：MySQL 连接和题目数量检查。
- `GET /java-api/ai/status`：Java、旧接口与 DeepTutor 状态。
- `GET /java-api/reviews/{assignmentId}`：读取讲评草稿。
- `PUT /java-api/reviews/{assignmentId}`：保存讲评草稿或教师确认版。
- `POST /java-api/reviews/{assignmentId}/generate`：调用 DeepTutor 生成讲评草稿。
- `POST /java-api/generator/generate`：按题型和数量调用 DeepTutor 批量出题。
- `POST /java-api/generator/assignments/{assignmentId}/import`：导入教师确认的题目。
- `GET /java-api/student/assignments/{id}/questions`：向学生返回不含正确答案的题目。
- `POST /java-api/student/assignments/{id}/submit`：Java 按标准答案判分。
- `POST /java-api/student/assignments/{id}/corrections`：提交错题订正。
- `PATCH /java-api/student/corrections/{id}/confirm`：教师确认订正完成。
- `GET /java-api/editor/assignments/{id}`：读取题目编辑器数据。
- `POST/PUT/DELETE /java-api/editor/.../questions`：题目新增、修改和软删除。
- `/api/**`：迁移期间代理到旧的 Node API。

讲评状态约束：

```text
DRAFT（AI 草稿）
  → REVIEWED（教师修改并确认）
  → PUBLISHED（发布给学生）
```

`DRAFT` 不能直接跳过教师确认发布。

## 尚未迁移

以下模块仍需根据需求文档确认 P0 规则后实现：

- 用户、JWT/Session 与角色权限
- 教师、班级、学生关系
- 作业、题目、知识点关系
- Excel 批改导入
- 确定性判分与统计口径
- 讲评正式持久化、版本与撤回
- 学生订正与教师确认
- 操作日志与审计

当前 `TeachingReviewController` 使用内存存储，只用于验证 Java + Vue 纵切流程；重启 Java 后数据会重置。
