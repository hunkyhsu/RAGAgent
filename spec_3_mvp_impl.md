# SPEC-4-MVP 实施清单（含验收项）

> 目标：最小可交付闭环（MVP）。仅覆盖必需功能与最小运维规范。

---

## T1 工程骨架与配置

- 内容
  - 建立 `backend/`、`frontend/`、`infra/` 目录结构
  - 配置 `application.yml` 与 `application-dev.yml`
  - Flyway 迁移脚本
- 验收
  - 后端可启动，迁移自动执行
  - 空库可访问，无初始化错误

---

## T2 认证与用户体系

- 内容
  - `AuthController` + `AuthService`
  - BCrypt 密码存储
  - JWT 发行与校验（仅 access token）
- 验收
  - 注册成功后可登录
  - 错误密码返回 401
    改动说明
- 改动
  - 新增认证 API 与服务层，包含注册/登录、密码校验、JWT 生成与解析：src/main/java/com/hunkyhsu/ragagent/auth/
    AuthController.java、src/main/java/com/hunkyhsu/ragagent/auth/AuthService.java、src/main/java/com/hunkyhsu/ragagent/
    auth/JwtService.java
  - 增加 JWT 鉴权过滤器与安全配置，保护除 /api/auth/** 之外的接口：src/main/java/com/hunkyhsu/ragagent/auth/
    JwtAuthenticationFilter.java、src/main/java/com/hunkyhsu/ragagent/config/SecurityConfig.java
  - 使用 JDBC 访问 users 表，落库 BCrypt 密码：src/main/java/com/hunkyhsu/ragagent/auth/UserRepository.java、src/main/
    java/com/hunkyhsu/ragagent/auth/UserAuth.java
  - JWT 依赖与配置项：pom.xml、src/main/resources/application.yml
  具体验收方法（T2）
  前置：MySQL 已启动，库 agent 存在且 Flyway 迁移执行成功。
  1. 启动后端
  mvn spring-boot:run
  2. 注册成功后可登录
  curl -s -X POST http://localhost:8080/api/auth/register ^
  -H "Content-Type: application/json" ^
  期望：返回包含 accessToken、tokenType、expiresInSeconds 的 JSON。
  3. 登录成功
  curl -s -X POST http://localhost:8080/api/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"test@example.com\",\"password\":\"Passw0rd!\"}"
  期望：返回 accessToken。
  4. 错误密码返回 401
  curl -i -X POST http://localhost:8080/api/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"test@example.com\",\"password\":\"wrong\"}"
  期望：HTTP 401。
  注意
  - 请替换 src/main/resources/application.yml 中的 security.jwt.secret 为至少 32 字节的安全密钥。
  如果你希望我继续加上“受保护接口 + JWT 校验效果演示”，告诉我需要保护的具体路径即可。
---

## T3 会话 REST

- 内容
  - 创建会话、会话列表、会话历史
- 验收
  - 登录后可创建会话
  - 刷新页面可拉回历史消息

---

## T4 WebSocket 主链路

- 内容
  - WS 握手鉴权
  - `chat.send` -> `chat.stream` -> `chat.done`
- 验收
  - 发送消息后 2s 内开始流式输出
  - 完成后收到 `chat.done`

---

## T5 LLM Provider（单一 OpenAI）

- 内容
  - `AiProvider` 接口
  - `OpenAiProvider` 流式输出
- 验收
  - 可连续流式输出完整回答
  - 异常时返回 `error`

---

## T6 持久化

- 内容
  - 用户消息与助手完整消息入库
- 验收
  - 刷新页面后历史一致

---

## T7 基础限流（IP 级）

- 内容
  - Redis 令牌桶
  - 超限返回 `RLIMIT`
- 验收
  - 高频请求触发限流
  - 等待窗口后恢复

---

## T8 前端 MVP

- 内容
  - 登录页 + 聊天页
  - 侧边会话列表 + 主聊天窗口 + 输入框
  - 流式渲染与自动滚动
- 验收
  - 可登录、创建会话、发送消息
  - 实时流式渲染正常

---

## T9 观察与错误规范（最小版本）

- 内容
  - 基础日志
  - 统一错误码：`AUTH` / `VALIDATION` / `RLIMIT` / `UPSTREAM`
- 验收
  - 日志包含 `userId/convId/messageId`
  - 错误响应结构统一

---

## MVP 统一验收

- 注册登录成功，能创建会话并对话
- 发送消息 2s 内开始流式渲染
- 刷新页面后历史一致
- 触发限流返回 `RLIMIT`，等待后恢复

