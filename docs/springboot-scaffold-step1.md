# 企业级 Spring Boot 微服务脚手架 — 第 1 步骨架

> **本步目标**：产出一个能跑、能测、能一键起依赖的**完整服务模板仓库**。
> 所有能力（统一响应 / 全局异常 / traceId / MyBatis-Plus 约定）**先内联写在项目里**，
> 不抽 starter、不做 Maven archetype。抽象留到第 3 步，等有两个真实使用场景再做。

---

## 0. 开工前的两个决定

### 0.1 坐标（已定稿）

| 项 | 值 |
|---|---|
| GitHub 组织 | [`github.com/gusforge`](https://github.com/gusforge) |
| Maven groupId | `io.github.gusforge` |
| Java 基础包 | `io.github.gusforge.foundry` |
| 模板仓库 / 根 artifactId | `foundry-template` |
| 模块前缀 | `foundry` |

组织名 `gusforge` 不含连字符，所以 **groupId 与 Java 包名完全一致**，不存在
`io.github.xxx-yyy` 那种"坐标带连字符、包名只能去掉"的长期别扭。

发布渠道对应关系：Maven Central 用 `io.github.gusforge`（需在 Sonatype Central Portal
验证 `gusforge` 这个 GitHub 命名空间的所有权）；走 JitPack 则坐标自动是
`com.github.gusforge`，无需验证。建议先 JitPack 验证流程，稳定后迁 Central。

> **groupId 一旦发布就基本改不掉** —— 已发布坐标无法重命名，换 groupId 等于让所有
> 使用方改依赖。所以这一节的值在第一次 `mvn deploy` 之前就该锁死。

### 0.2 版本基线（Boot 4.1 兼容性已验证）

| 项 | 选择 | 回退 |
|---|---|---|
| JDK | 21 (LTS) | 17 |
| Spring Boot | **4.1.1** | 3.5.x |
| MyBatis-Plus | `mybatis-plus-spring-boot4-starter` **3.5.17** + `mybatis-plus-jsqlparser` 3.5.17 | — |
| springdoc | `springdoc-openapi-starter-webmvc-ui` **3.1.1** | — |
| Testcontainers | **2.0.5**（已在 CI 的真实 MySQL 8.4 上验证） | — |
| ArchUnit | `archunit-junit5` **1.5.1**（已验证） | — |
| Spring Cloud Alibaba | 2025.1.x（仅第 5 步引入） | — |

**已验证（2026-10-10）**：在全新本地仓库、仅 Maven Central 的条件下（510 个依赖全部来自
`central`），Boot 4.1.1 + 上述 MyBatis-Plus / springdoc 组合 6 项冒烟测试全过：
启动、审计字段自动填充、逻辑删除、分页、`BlockAttackInnerInterceptor` 拦截无 where 删除、
`LocalDateTime` 经 Jackson 3 序列化、`/v3/api-docs` 与 Swagger UI 可访问。
所以**基线定为 Boot 4.1.1，不需要回退 3.5.x**。

冒烟中踩到、已写进下文 POM 的几点：

1. MyBatis-Plus 对 Boot 4 有**独立的** `-spring-boot4-starter`，`-spring-boot3-starter` 不能用。
2. 3.5.9 之后 SQL 解析从核心包拆出，分页和 `BlockAttackInnerInterceptor` 需要显式依赖
   `mybatis-plus-jsqlparser`，否则启动即报类找不到。
3. springdoc 对应 Boot 4 的是 **3.x**，2.x 不适用。
4. Boot 4 的 web starter 改名为 `spring-boot-starter-webmvc`（旧名不再是首选）。
5. Boot 4 切到 Jackson 3（groupId `tools.jackson`），直接依赖 `com.fasterxml.jackson`
   的第三方库仍是潜在风险点。冒烟里没有引入这类库，所以以后加依赖时要留意。

**Testcontainers 2.x 已验证**：artifact 与类名与 1.x 不同（`testcontainers-mysql`、
`testcontainers-junit-jupiter`、`org.testcontainers.mysql.MySQLContainer` 且非泛型），
第 6 节的示例按 2.x 写，已在 GitHub Actions 的 ubuntu runner 上用真实 MySQL 8.4 跑通
（`DemoRepositoryIT` 2 项，`Skipped: 0`）。ArchUnit 1.5.1 与 Spotless 3.10.4 也已随
`mvn verify` 跑通。

**仍未验证**：
- 冒烟用的是 H2 的 MySQL 模式，只验证库兼容性。CI 里的真实 MySQL 覆盖了审计字段填充、
  逻辑删除可见性，以及逻辑删除与唯一索引 `uk_name_deleted` 的三种交互（见 11.1）；
  `DATETIME(3)` 精度、时区行为**目前没有测试**，仍需补。

Boot 3.5 的 OSS 支持已于 2026-06 结束，仅作为万一出现无法绕开的生态问题时的回退，
回退即技术债，需要记账。

---

## 1. 目录结构

**四模块**，不是六模块。`-api` / `-client` 等到真的出现第二个服务需要远程调用时再拆
（第 5 步）—— 现在拆出来没有消费方，纯负担。

```
foundry-template/
├── pom.xml                          # 聚合 POM + 全局版本/插件管理
├── init.sh                          # 从模板初始化新项目（改名替换）
├── docker-compose.yml               # MySQL + Redis（本地依赖）
├── Dockerfile                       # 应用镜像（分层构建）
├── .gitignore
├── .editorconfig
├── README.md
├── .github/workflows/ci.yml
│
├── foundry-start/                   # 启动模块：入口 + 配置文件
│   ├── pom.xml
│   └── src/main/
│       ├── java/io/github/gusforge/foundry/Application.java
│       └── resources/
│           ├── application.yml
│           ├── application-local.yml
│           └── logback-spring.xml
│
├── foundry-web/                     # Controller + 全局异常 + trace 过滤器
│   ├── pom.xml
│   └── src/main/java/io/github/gusforge/foundry/
│       ├── controller/DemoController.java
│       └── support/                            # 这些后续会抽成 starter-web
│           ├── DataResponse.java
│           ├── ErrorResponse.java
│           ├── GlobalExceptionHandler.java
│           └── TraceIdFilter.java
│
├── foundry-service/                 # 业务逻辑 + 异常定义
│   ├── pom.xml
│   └── src/main/java/io/github/gusforge/foundry/
│       ├── service/DemoService.java
│       ├── service/impl/DemoServiceImpl.java
│       ├── model/                              # 领域模型（非 Entity）
│       └── exception/
│           ├── BizException.java
│           ├── SysException.java
│           └── ErrorCode.java
│
└── foundry-dao/                     # Entity + Mapper + Repository
    ├── pom.xml
    └── src/main/
        ├── java/io/github/gusforge/foundry/
        │   ├── entity/DemoEntity.java
        │   ├── mapper/DemoMapper.java
        │   ├── repository/DemoRepository.java
        │   └── config/MybatisPlusConfig.java
        └── resources/
            ├── mapper/DemoMapper.xml
            └── db/migration/V1__init.sql
```

**依赖方向**（靠 Maven 模块强制，不靠口头约定）：

```
start ──> web ──> service ──> dao
```

**`Repository` 放在 dao 模块**。原文档在这点上自相矛盾（命名表列了 Repository，
但 dao 说明只写 "Entity + Mapper"），这里明确裁决：Repository 属于 dao，
它是对 Mapper 的薄封装，负责把 MyBatis-Plus 的 API 收口，不让 `LambdaQueryWrapper`
泄漏到 service 层。

**分包按技术层**（`controller` / `service` / `entity`），与原文档 `{模块}` 示例一致。
等单个模块内超过 ~15 个类时，改为层内先按领域特性分包
（`campaign/controller`、`campaign/service`），让同一特性的代码在路径上对齐。

---

## 2. 根 POM

**关键约定**：根 POM 只放 `dependencyManagement` 和 `pluginManagement`，
**不放实际 `<dependencies>`**。否则每个模块都背上全套依赖（含你根本没用的东西）。
唯一例外是 Lombok 这类全模块都要的编译期依赖。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.1</version>          <!-- 验证不通过则回退 3.5.16 -->
    <relativePath/>
  </parent>

  <groupId>io.github.gusforge</groupId>
  <artifactId>foundry-template</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <packaging>pom</packaging>

  <modules>
    <module>foundry-dao</module>
    <module>foundry-service</module>
    <module>foundry-web</module>
    <module>foundry-start</module>
  </modules>

  <properties>
    <java.version>21</java.version>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

    <mybatis-plus.version>3.5.17</mybatis-plus.version>
    <springdoc.version>3.1.1</springdoc.version>
    <testcontainers.version>2.0.5</testcontainers.version>
    <spotless.version>3.10.4</spotless.version>
    <archunit.version>1.5.1</archunit.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <!-- 本项目内部模块 -->
      <dependency>
        <groupId>io.github.gusforge</groupId>
        <artifactId>foundry-dao</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>io.github.gusforge</groupId>
        <artifactId>foundry-service</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>io.github.gusforge</groupId>
        <artifactId>foundry-web</artifactId>
        <version>${project.version}</version>
      </dependency>

      <!-- 三方 -->
      <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
        <version>${mybatis-plus.version}</version>
      </dependency>
      <dependency>
        <!-- 分页、BlockAttack 等插件依赖它；3.5.9 起从核心包拆出，必须显式引入 -->
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-jsqlparser</artifactId>
        <version>${mybatis-plus.version}</version>
      </dependency>
      <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>${springdoc.version}</version>
      </dependency>
      <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers-bom</artifactId>
        <version>${testcontainers.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <dependencies>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <pluginManagement>
      <plugins>
        <!-- 只在 start 模块打可执行 jar；其余模块产出普通 jar -->
        <plugin>
          <groupId>org.springframework.boot</groupId>
          <artifactId>spring-boot-maven-plugin</artifactId>
          <configuration>
            <skip>true</skip>
          </configuration>
        </plugin>
      </plugins>
    </pluginManagement>

    <plugins>
      <plugin>
        <groupId>com.diffplug.spotless</groupId>
        <artifactId>spotless-maven-plugin</artifactId>
        <version>${spotless.version}</version>
        <configuration>
          <java>
            <googleJavaFormat><style>AOSP</style></googleJavaFormat>
            <removeUnusedImports/>
          </java>
        </configuration>
        <executions>
          <execution>
            <goals><goal>check</goal></goals>   <!-- CI 卡住格式 -->
            <phase>validate</phase>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

**子模块 POM 要点**（只列差异，避免冗长）：

- `foundry-dao`：`mybatis-plus-spring-boot4-starter` + `mybatis-plus-jsqlparser` + `mysql-connector-j`
- `foundry-service`：依赖 `foundry-dao`
- `foundry-web`：依赖 `foundry-service` + `spring-boot-starter-webmvc` + `springdoc`
- `foundry-start`：依赖 `foundry-web` + `spring-boot-starter-actuator`，
  并**覆盖** `spring-boot-maven-plugin` 的 `<skip>false</skip>` 打出可执行 jar

---

## 3. 核心类

### 3.1 统一响应

保留原设计的"只有 data、不带 code/msg"——错误走 HTTP 状态码 + 全局处理器，
比满世界的 `{code:0,msg:"ok"}` 干净。但**补上原设计缺失的错误响应契约**：
成功格式和错误格式必须成对定义，否则协议是不完整的。

```java
// web/support/DataResponse.java
public record DataResponse<T>(T data) {
    public static <T> DataResponse<T> of(T data) {
        return new DataResponse<>(data);
    }
}
```

用 `record` 替代原来的 `@Data` + `Serializable` + 静态 build——不可变、无需
`serialVersionUID`、少一半代码。

```java
// web/support/ErrorResponse.java
public record ErrorResponse(
        int code,           // 业务错误码；系统异常为 0
        String message,
        String traceId,     // 关键：把 traceId 交回调用方，排查有抓手
        Instant timestamp) {

    public static ErrorResponse of(int code, String message) {
        return new ErrorResponse(code, message, MDC.get(TraceIdFilter.TRACE_ID), Instant.now());
    }
}
```

### 3.2 异常体系

沿用"按重试语义二分"的思路，这是原设计最好的部分。修掉三个问题：
不再遮蔽父类 `message`、补 `cause` 构造器、错误码用枚举而非裸整数。

```java
// service/exception/BizException.java
/** 业务异常：语义明确、调用方不应重试。 */
public class BizException extends RuntimeException {
    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String detail, Throwable cause) {
        super(detail, cause);            // ← 原设计缺这个，包装下层异常会丢栈
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
```

```java
// service/exception/SysException.java
/** 系统异常：基础设施/集成故障，通常可重试。 */
public class SysException extends RuntimeException {
    public SysException(String message) { super(message); }
    public SysException(String message, Throwable cause) { super(message, cause); }
}
```

```java
// service/exception/ErrorCode.java
public enum ErrorCode {
    // 1xxxxx 通用
    INVALID_PARAM(100001, "参数不合法", HttpStatus.BAD_REQUEST),
    NOT_FOUND(100002, "资源不存在", HttpStatus.NOT_FOUND),
    // 3xxxxx 业务域
    DEMO_NOT_FOUND(300001, "示例数据不存在", HttpStatus.NOT_FOUND);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;
    // 构造器 + getter 省略
}
```

错误码带上 `HttpStatus` 是关键设计：让全局处理器能自动映射状态码，
不需要在每个 handler 里写 switch。

### 3.3 全局异常处理

```java
// web/support/GlobalExceptionHandler.java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ErrorResponse> onBiz(BizException e) {
        ErrorCode ec = e.getErrorCode();
        // 业务异常是预期内的，warn 级别、不打全栈
        log.warn("biz error: code={} msg={}", ec.getCode(), e.getMessage());
        return ResponseEntity.status(ec.getHttpStatus())
                .body(ErrorResponse.of(ec.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> onInvalid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(ErrorCode.INVALID_PARAM.getCode(), detail));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> onUnexpected(Exception e) {
        // 系统异常必须打全栈
        log.error("unexpected error", e);
        // 不把内部异常信息暴露给调用方，只给 traceId
        return ResponseEntity.internalServerError()
                .body(ErrorResponse.of(0, "服务内部错误，请提供 traceId 联系支持"));
    }
}
```

最后一个 handler 的处理方式是有意的：**内部异常细节不外泄**（避免泄漏堆栈、
SQL、内网地址），调用方拿 traceId 来问。这是安全基线，原文档没提。

### 3.4 traceId 过滤器

原设计有 RequestId 追踪但没说进不进 MDC。不进 MDC 等于白做——日志里搜不到就没有价值。

```java
// web/support/TraceIdFilter.java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    public static final String HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = req.getHeader(HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(TRACE_ID, traceId);
        resp.setHeader(HEADER, traceId);      // 回带，方便调用方关联
        try {
            chain.doFilter(req, resp);
        } finally {
            MDC.remove(TRACE_ID);             // 必须清理，线程池会复用线程
        }
    }
}
```

> 用 JDK 21 虚拟线程时 MDC 依然有效（每个请求独立虚拟线程），
> 但**异步任务和 `@Async` 不会自动继承 MDC**，需要额外的 TaskDecorator。
> 这一步先不做，等真的用到异步时再加。

### 3.5 持久层

```java
// dao/entity/DemoEntity.java
@Data
@TableName("demo")
public class DemoEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
```

```java
// dao/repository/DemoRepository.java
/** 把 MyBatis-Plus API 收口在 dao 层，不让 Wrapper 泄漏到 service。 */
@Repository
@RequiredArgsConstructor
public class DemoRepository {
    private final DemoMapper mapper;

    public Optional<DemoEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public Optional<DemoEntity> findByName(String name) {
        return Optional.ofNullable(mapper.selectOne(
                new LambdaQueryWrapper<DemoEntity>().eq(DemoEntity::getName, name)));
    }
}
```

返回 `Optional` 而不是 null，让 service 层的 `orElseThrow(...)` 成为自然写法。

```java
// dao/config/MybatisPlusConfig.java
@Configuration
@MapperScan("io.github.gusforge.foundry.mapper")
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor i = new MybatisPlusInterceptor();
        i.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        i.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        i.addInnerInterceptor(new BlockAttackInnerInterceptor());  // 拦截无 where 的全表更新/删除
        return i;
    }

    /** 审计字段自动填充 */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject m) {
                strictInsertFill(m, "createdAt", LocalDateTime.class, LocalDateTime.now());
                strictInsertFill(m, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }
            @Override
            public void updateFill(MetaObject m) {
                strictUpdateFill(m, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
```

`BlockAttackInnerInterceptor` 值得单独说：它拦截不带 where 的 update/delete，
是防"手滑清库"的安全网。原文档没配，建议默认开。

**建表 SQL — 注意逻辑删除与唯一索引的冲突**：

```sql
-- dao/resources/db/migration/V1__init.sql
CREATE TABLE demo (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  name       VARCHAR(128) NOT NULL,
  status     TINYINT      NOT NULL DEFAULT 0,
  created_at DATETIME(3)  NOT NULL,
  updated_at DATETIME(3)  NOT NULL,
  deleted    TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  -- 逻辑删除必须把 deleted 并入唯一索引，否则软删后同名记录插不进来
  UNIQUE KEY uk_name_deleted (name, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

> ⚠ `uk_name_deleted` 只能容纳**一条**软删记录（第二次软删同名会撞唯一键）。
> 如果需要多次软删同名记录，改用"删除时把 deleted 写成时间戳"的方案。
> 这是逻辑删除的头号坑，原文档完全没提。

### 3.6 时区处理 —— 原设计的高危区

原设计 `utc-enabled: true` + `LocalDateTime` + MySQL 会话时区三者叠加，
是时间 bug 高发区。`LocalDateTime` 不带时区，全链路是否一致取决于 JDBC 参数。

**这一步的明确约定**：

```yaml
spring:
  jackson:
    time-zone: UTC
  datasource:
    url: jdbc:mysql://localhost:3306/demo?serverTimezone=UTC&characterEncoding=utf8mb4
```

外加 `Application` 里显式设置：

```java
public static void main(String[] args) {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));   // 必须在 Spring 启动前
    SpringApplication.run(Application.class, args);
}
```

> 更彻底的做法是对外契约统一用 `Instant`（带时区语义），`LocalDateTime` 只在
> DB 映射层出现。这一步先保持 `LocalDateTime` 与原设计一致，但记为待评估项。

---

## 4. 配置文件

```yaml
# start/resources/application.yml
spring:
  application:
    name: foundry-service
  profiles:
    active: local
  threads:
    virtual:
      enabled: true          # JDK 21 虚拟线程；压测后再决定是否保留
  jackson:
    time-zone: UTC
    default-property-inclusion: non_null

mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus     # 白名单，不要 include: "*"
  endpoint:
    health:
      show-details: when-authorized          # 不对匿名用户暴露组件细节
      probes:
        enabled: true                        # /health/liveness、/health/readiness

springdoc:
  api-docs:
    enabled: true
  swagger-ui:
    path: /swagger-ui.html

logging:
  pattern:
    # traceId 进日志格式，这是 TraceIdFilter 的价值所在
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId:-}] %logger{36} - %msg%n"
```

**actuator 收口是安全基线**：原文档只讲了健康检查，没提端点暴露范围。
actuator 裸奔是常见的信息泄露入口（`/env` 能读到配置、`/heapdump` 能下载内存镜像）。
这里用白名单 + `when-authorized`。

```yaml
# start/resources/application-local.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/demo?serverTimezone=UTC&characterEncoding=utf8mb4&useSSL=false
    username: root
    password: ${DB_PASSWORD:root}      # 本地默认值，生产必须走环境变量
  data:
    redis:
      host: localhost
      port: 6379

logging:
  level:
    io.github.gusforge.foundry: DEBUG
```

> **不要把任何真实密码提交进仓库**。`application-local.yml` 里的 `root` 只对
> compose 起的本地容器有效。生产配置一律走环境变量或配置中心（第 5 步接 Nacos）。
> `.gitignore` 里加上 `application-*-secret.yml`、`.env`。

---

## 5. 本地依赖与镜像

```yaml
# docker-compose.yml
services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: demo
    ports: ["3306:3306"]
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-proot"]
      interval: 5s
      retries: 10
    volumes:
      - ./foundry-dao/src/main/resources/db/migration:/docker-entrypoint-initdb.d:ro

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      retries: 10
```

Nacos 先不放进 compose——第 1 步是单服务，没有服务发现的需求，
多起一个容器只是拖慢本地启动。第 5 步拆多服务时再加。

```dockerfile
# Dockerfile —— 分层构建，利用 Spring Boot 的 layertools
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY foundry-dao/pom.xml foundry-dao/
COPY foundry-service/pom.xml foundry-service/
COPY foundry-web/pom.xml foundry-web/
COPY foundry-start/pom.xml foundry-start/
RUN mvn -B -q dependency:go-offline           # 依赖单独成层，代码改动不重新下载
COPY . .
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app  # 不要用 root 跑应用
USER app
COPY --from=build /src/foundry-start/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
```

两个要点：`USER app` 避免容器内 root 运行；`MaxRAMPercentage` 让 JVM 感知容器内存限制
（不设的话在小内存容器里容易 OOMKilled）。

---

## 6. 测试

第 1 步就要有测试，否则第 3 步抽 starter 时不敢改。三层各一个样板：

```java
// service 层：纯单测，Mockito，不起 Spring
@ExtendWith(MockitoExtension.class)
class DemoServiceImplTest {
    @Mock DemoRepository repository;
    @InjectMocks DemoServiceImpl service;

    @Test
    void should_throw_biz_exception_when_not_found() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(1L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEMO_NOT_FOUND);
    }
}
```

```java
// web 层：只起 MVC 切片，验证异常 → HTTP 状态码 + 响应体契约
// Boot 4 起 @WebMvcTest 在 org.springframework.boot.webmvc.test.autoconfigure 包，需依赖 spring-boot-starter-webmvc-test（已验证）
@WebMvcTest(DemoController.class)
class DemoControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean DemoService service;      // 用 @MockitoBean，旧的 @MockBean 已废弃

    @Test
    void should_return_404_with_error_code() throws Exception {
        when(service.getById(1L)).thenThrow(new BizException(ErrorCode.DEMO_NOT_FOUND));

        mvc.perform(get("/api/v1/demos/1"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value(300001))
           .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
```

```java
// dao 层：Testcontainers 起真实 MySQL，验证逻辑删除/审计字段真的生效
@SpringBootTest
@Testcontainers
abstract class AbstractDbIT {
    @Container
    @ServiceConnection                     // 需 spring-boot-testcontainers；自动注入数据源，无需 @DynamicPropertySource
    // Testcontainers 2.x：类在 org.testcontainers.mysql 包且不再带泛型（已在 CI 验证）
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withInitScript("db/migration/V1__init.sql");
}

class DemoRepositoryIT extends AbstractDbIT {
    @Autowired DemoRepository repository;

    @Test
    void should_fill_audit_fields_on_insert() { /* ... */ }

    @Test
    void should_hide_logically_deleted_rows() { /* ... */ }
}
```

**为什么 dao 层坚持用真实 MySQL 而不是 H2**：逻辑删除、审计填充、唯一索引冲突这些
恰好是方言相关的行为，H2 测过了不代表 MySQL 上对。这个抽象基类是第 4 步
`starter-test` 的雏形。

```java
// ArchUnit：把依赖方向和命名约定变成会失败的测试
@AnalyzeClasses(packages = "io.github.gusforge.foundry")
class ArchitectureTest {

    @ArchTest
    static final ArchRule layers = layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("Web").definedBy("..controller..")
            .layer("Service").definedBy("..service..")
            .layer("Dao").definedBy("..repository..", "..mapper..")
            .whereLayer("Web").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Web")
            .whereLayer("Dao").mayOnlyBeAccessedByLayers("Service");

    @ArchTest
    static final ArchRule entities_stay_in_dao = noClasses()
            .that().resideOutsideOfPackage("..dao..")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Entity");

    @ArchTest
    static final ArchRule naming = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().haveSimpleNameEndingWith("Controller");
}
```

`entities_stay_in_dao` 是这组规则里最有价值的一条：它防止 Entity 泄漏到
Controller 和（第 5 步的）api 模块，这正是契约隔离的核心。规范写在文档里是建议，
写成 failing test 才是约束。

---

## 7. CI 与初始化脚本

```yaml
# .github/workflows/ci.yml
name: CI
on:
  push: { branches: [main] }
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      - run: mvn -B verify          # spotless:check + 单测 + Testcontainers 集成测试
```

Testcontainers 在 GitHub Actions 的 ubuntu runner 上直接可用（Docker 已预装），
不需要额外配置 service 容器。

```bash
#!/usr/bin/env bash
# init.sh —— 从模板初始化新项目
set -euo pipefail

read -rp "groupId (e.g. io.github.gusforge): " GROUP_ID
read -rp "artifactId (e.g. order-service): " ARTIFACT
read -rp "service prefix (e.g. order): " SVC
read -rp "base package (e.g. io.github.gusforge.order): " PKG

OLD_PKG="io.github.gusforge.foundry"
OLD_PKG_PATH="${OLD_PKG//.//}"
NEW_PKG_PATH="${PKG//.//}"

[[ "$PKG" == "$OLD_PKG" ]] && { echo "package unchanged, nothing to do"; exit 1; }

# 1. 移动包目录
#    用 git mv 保留历史；删除旧叶子目录后自底向上清理空的父目录。
#    不要用 rm -rf "$module/${OLD_PKG_PATH%%/*}" —— 那会删掉整个 io/，
#    而新包路径通常也在 io/ 底下，等于把刚复制好的文件一起删了。
for module in */src/{main,test}/java; do
  [[ -d "$module/$OLD_PKG_PATH" ]] || continue
  mkdir -p "$module/$NEW_PKG_PATH"
  cp -r "$module/$OLD_PKG_PATH/." "$module/$NEW_PKG_PATH/"
  rm -rf "$module/$OLD_PKG_PATH"
  # 只删空目录，且不会删到 $module 自身
  find "$module" -mindepth 1 -type d -empty -delete
done

# 2. 替换文本占位（排除 .git 和 target）
#    顺序重要：foundry-template 必须在 foundry- 之前替换，否则会被吃成 $SVC-template
find . -type f \( -name '*.java' -o -name '*.xml' -o -name '*.yml' -o -name '*.yaml' \
       -o -name '*.sql' -o -name '*.md' -o -name 'Dockerfile' \) \
  -not -path './.git/*' -not -path '*/target/*' -print0 \
| xargs -0 sed -i.bak \
    -e "s|$OLD_PKG|$PKG|g" \
    -e "s|foundry-template|$ARTIFACT|g" \
    -e "s|foundry-|$SVC-|g" \
    -e "s|io\.github\.gusforge|$GROUP_ID|g"

find . -name '*.bak' -delete

# 3. 重命名模块目录
for d in foundry-*; do
  [[ -d "$d" ]] && mv "$d" "${d/foundry-/$SVC-}"
done

echo "done. next: mvn -B verify"
```

> 脚本在 Windows 上需要 Git Bash 或 WSL（`sed -i.bak` 的写法是为了兼容 macOS 的
> BSD sed）。这段是个起点，实际跑一遍后大概率要微调——**这也是为什么选
> GitHub Template 而不是 Maven archetype**：模板仓库本身能跑起来、能验证，
> archetype 的 velocity 模板调试痛苦得多。

---

## 8. 验收清单

第 1 步做完，下面每一条都应该为真：

- [ ] `mvn -B verify` 全绿（含 spotless、ArchUnit、Testcontainers）
- [ ] `docker compose up -d && mvn -pl foundry-start spring-boot:run` 能起来
- [ ] `GET /api/v1/demos/1` 返回 `{"data":{...}}`
- [ ] `GET /api/v1/demos/999` 返回 404 + `{"code":300001,"traceId":"..."}`
- [ ] 响应头带 `X-Trace-Id`，且该值能在日志里搜到
- [ ] `/actuator/health/readiness` 返回 UP，`/actuator/env` **返回 404**
- [ ] 软删一条记录后查询查不到，但 DB 里还在
- [ ] `docker build .` 出镜像且容器内非 root 运行
- [ ] 故意破坏依赖方向（在 dao 里 import Controller）→ ArchUnit 测试失败
- [ ] 把仓库 clone 到干净机器上能构建（**验证没有内网依赖残留**）

最后一条是这个项目的硬约束：**不依赖任何公司内网仓库或内网 artifact**。
上面所有依赖（Spring Boot、MyBatis-Plus、springdoc、Testcontainers、ArchUnit）
都在 Maven Central 上。

---

## 9. 明确不做的事

| 不做 | 原因 |
|---|---|
| 抽 starter | 只有一个使用场景，抽象一定是错的。等第 3 步 |
| `-api` / `-client` 模块 | 没有第二个服务来消费。等第 5 步 |
| Maven archetype | 用 GitHub Template 替代，可跑可测 |
| Nacos / Apollo | 单服务不需要注册发现；配置走 profile |
| RocketMQ / XXL-Job | 没有实际需求 |
| 代码生成器 | 生成的代码无法升级、鼓励贫血模型、会让 Entity 泄漏到 API 层 |
| `common-utils` 模块 | 必然变成垃圾场，且是循环依赖温床 |
| 自定义 `@XxxService` 注解 | 包装 Spring 已有抽象，净负债 |
| 多租户 / 审计日志 | 引入后极难摘掉，真需要时再说 |

---

## 10. 下一步

**第 2 步：拿这个模板做一个真实的小项目。**

这一步最容易被跳过，也最不该跳过——它会暴露所有设计时想不到的摩擦点。
建议停止条件：**如果第 2 步做不出一个真实项目，就不要继续做 starter。**
没有真实使用场景的脚手架没有价值。

---

## 11. 实现记录（2026-10-10）：落地后与上文的偏差

模板已按本文实现并跑通 `mvn verify`。以下是实现过程中**与上文设计不一致、以实际代码为准**的地方。

### 11.1 构建结果

- 全新本地仓库 + 仅 Maven Central，`mvn verify` 通过：service 3、web 6、ArchUnit 4、
  端到端冒烟 6，共 19 项。
- 对 `init.sh` 的产物（改名为 `com.example.shop.order`）做完整构建，同样通过。
- 本机无 Docker：`DemoRepositoryIT`（2 项）被 `disabledWithoutDocker` 跳过，本机共 19 项。
- **GitHub Actions 首次运行（ubuntu runner，有 Docker）全部通过，共 21 项**：
  `DemoRepositoryIT` 真实跑在 MySQL 8.4 容器上，`Skipped: 0`（首次 2 项，补唯一索引用例后 5 项），
  所以 Testcontainers 2.x 的写法已确认可用。它验证了 `V1__init.sql` 能在 MySQL 上执行、
  审计字段自动填充、逻辑删除后查不到，并固定了唯一索引与逻辑删除的行为：活跃行同名重复被拒、
  软删后可重建同名、**同名第二次软删会撞唯一键（已知限制，用例锁定当前行为）**；
  **没有**覆盖 `DATETIME(3)` 精度、时区。

### 11.2 偏差与踩坑

| 位置 | 上文写法 | 实际 | 原因 |
|---|---|---|---|
| 全局异常处理 | 独立类 + 兜底 `@ExceptionHandler(Exception.class)` | **继承 `ResponseEntityExceptionHandler`** | 兜底 handler 会把 Spring MVC 自己的 404/405 吞成 500（冒烟测试发现 `/actuator/env` 返回 500）。已加回归测试 |
| `ErrorCode.httpStatus` | Spring `HttpStatus` | `int` | 避免 service 模块依赖 spring-web |
| service 模块依赖 | 仅 dao | 另加 `jakarta.validation-api` | DTO 上的 `@NotBlank` 需要注解 API；只要 API，不引入 web |
| IT 测试 | 只写了测试类 | 根 POM 声明 `maven-failsafe-plugin` | `*IT` 不在 surefire 默认匹配范围，不声明则永远不会执行 |
| 端到端冒烟 | 文中没有 | 新增 `ApplicationSmokeTest`（H2 MySQL 模式） | 让没有 Docker 的机器也能验证四模块装配、traceId、错误码、actuator 收口 |
| 切片测试 | `@WebMvcTest` | web、dao 模块各需一个测试专用的 `@SpringBootApplication` | 这两个模块没有入口类，切片测试找不到配置 |
| `application.yml` | `spring.jackson.time-zone: UTC` | 去掉，仅靠 `Application.main` 里设置 JVM 默认时区 | Boot 4 切到 Jackson 3，该属性是否仍适用未验证 |
| `init.sh` | 提示 `artifactId (e.g. order-service)` | 示例改为 `order-parent`，并校验与模块名冲突 | 示例与 `${SVC}-service` 同名，会触发 Maven reactor `duplicated` |
| `init.sh` | `s|>foundry-|...|` | `s|foundry-|...|` 且排除 `docs/` | 原写法替换不到 compose、Dockerfile 里的路径 |

### 11.3 仍未验证

- MySQL 方言：`DATETIME(3)` 精度、时区行为（没有测试覆盖）。
- `docker compose up` 与 `docker build`（本机无 Docker，CI 也没有执行这两步）。
- Jackson 3 下 `spring.jackson.*` 各属性的适用性。
- 一次 `mvn verify` 曾显示 dao 模块耗时 26 分钟；单独重跑 6.5 秒，**无法复现**，
  原因不明（怀疑是机器挂起），记录在此以备再次出现时对照。

