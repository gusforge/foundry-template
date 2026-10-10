# foundry-template

Spring Boot 4.1 / JDK 21 微服务模板。四模块分层，规范靠编译期依赖和 ArchUnit 约束，而不是靠文档。

```
start ──> web ──> service ──> dao
```

| 模块 | 职责 |
|---|---|
| `foundry-start` | 启动入口、配置文件 |
| `foundry-web` | Controller、全局异常、traceId 过滤器、统一响应 |
| `foundry-service` | 业务逻辑、VO/DTO、异常与错误码 |
| `foundry-dao` | Entity、Mapper、Repository、MyBatis-Plus 配置 |

## 快速开始

```bash
./init.sh                 # 改 groupId / 模块前缀 / 基础包（Windows 用 Git Bash）
mvn -B verify             # 格式检查 + 单测 + ArchUnit + 端到端冒烟
docker compose up -d      # 本地 MySQL + Redis
mvn -pl foundry-start spring-boot:run
```

`DemoRepositoryIT` 用 Testcontainers 起真实 MySQL，没有 Docker 时自动跳过。

## 约定

- 成功响应只有 `data`；错误走 HTTP 状态码，响应体为 `{code, message, traceId, timestamp}`。
- `BizException` 是业务异常，不重试；`SysException` 是系统异常，可重试。
- 未预期的异常不向调用方暴露细节，只返回 `traceId`。
- Entity 不出 service 层（`ArchitectureTest` 强制）。
- actuator 只暴露 `health`、`info`、`prometheus`。

设计说明见 [docs/springboot-scaffold-step1.md](docs/springboot-scaffold-step1.md)。
