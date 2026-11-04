# 初中作业自动批改系统

面向初中数学的教师、学生和教研三端平台。支持作业发布、学生作答、自动判分、错题本、讲评生成、教师确认发布、学生订正与疑问，以及 DeepTutor 候选题生成。

项目只保留三层主要结构：

```text
frontend/   Vue 3 + TypeScript + CSS
backend/    Java 17 + Spring Boot
database/   MySQL 8 表结构、迁移与演示数据
scripts/    本地启动和数据库验收脚本
```

## 快速启动

Windows 下可以直接双击根目录的 `start.bat`。脚本会启动 MySQL、Java 后端和 Vue 前端，并打开浏览器。停止应用双击 `stop.bat`；数据库容器和数据卷不会被删除。

也可以按下面步骤分别启动。

### 1. 启动 MySQL

推荐使用 Docker：

```powershell
docker compose up -d mysql redis
```

MySQL 地址为 `127.0.0.1:6001`，数据库名 `mark`，默认账号 `mark`，密码 `markpassword`。数据保存在 Docker 卷 `mark-mysql-data`，重启容器不会清空。

### 2. 启动 Java 后端

```powershell
cd backend
mvn spring-boot:run
```

默认地址：`http://127.0.0.1:8080`

健康检查：`http://127.0.0.1:8080/health`

Flyway 会在启动时自动执行 `backend/src/main/resources/db/migration` 中尚未执行的迁移。

### 3. 启动 Vue 前端

另开一个终端：

```powershell
cd frontend
npm install
npm run dev:http
```

打开 `http://127.0.0.1:3020`。

如果后端使用其他端口，先设置：

```powershell
$env:VITE_PROXY_TARGET="http://127.0.0.1:8082"
npm run dev:http
```

### 4. DeepTutor（AI 出题与讲评）

DeepTutor 默认运行在 `http://127.0.0.1:8001`。不启动 DeepTutor 时，作业、答题、自动判分、总分和错题本仍可正常使用；AI 出题和 AI 讲评会提示服务不可用。

如需覆盖地址：

```powershell
$env:DEEPTUTOR_BASE_URL="http://127.0.0.1:8001"
```

## 演示账号

统一密码：`123456`

| 角色 | 账号 | 入口 |
| --- | --- | --- |
| 教师 | `teacher01` | 教师工作台 |
| 学生 | `S2024013` | 学生空间 |
| 教研 | `research01` | 教研控制台 |

以上均为虚构演示数据。

## 已实现功能

- 教师端：班级与学生、作业与题目、人工/表格批改、统计、知识点归因、讲评审核与发布、订正跟踪、学生疑问。
- 学生端：作业作答、自动判分、个人总分、错题本、已发布错题讲评、订正与提问。
- 教研端：知识点维护、公共讲评模板审核、脱敏操作日志。
- AI：DeepTutor 候选题生成、单题讲评、按审核模板批量讲评；AI 内容必须经教师确认，不能直接发布或改分。
- 安全边界：学生题目视图不返回标准答案和教师解析；学生只能查看本人提交、总分、错题和讲评。
- 数据库：Flyway 迁移、提交快照、逐题答案、错题快照、AI 草稿、版本、审计日志和 Java 启停日志均持久化。

## 验证命令

后端测试：

```powershell
cd backend
mvn test
```

前端类型检查与构建：

```powershell
cd frontend
npm run typecheck
npm run build
```

浏览器测试：

```powershell
$env:PLAYWRIGHT_BASE_URL="http://127.0.0.1:3020"
$env:PLAYWRIGHT_EXTERNAL_SERVER="1"
npm run test:e2e
```

数据库五步验收：

```powershell
pwsh -File scripts\verify-database-flow.ps1
```

该脚本会真实执行：新增学生、新增作业与题目、发布、学生提交、自动判分、查询总分、查询错题本，并直接连接 MySQL 校验数据行。

## 环境变量

后端常用变量：

```text
JAVA_API_PORT              默认 8080
JAVA_DATABASE_URL          默认 jdbc:mysql://127.0.0.1:6001/mark
JAVA_DATABASE_USERNAME     默认 mark
JAVA_DATABASE_PASSWORD     默认 markpassword
DEEPTUTOR_BASE_URL         默认 http://127.0.0.1:8001
DEEPTUTOR_API_KEY          本地 DeepTutor 通常留空
```

前端常用变量：

```text
VITE_API_MODE=http
VITE_API_BASE_URL=/api
VITE_PROXY_TARGET=http://127.0.0.1:8080
```

生产环境不要提交真实数据库密码、DeepTutor API Key 或会话密钥。
