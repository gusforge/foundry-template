package io.github.gusforge.foundry.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gusforge.foundry.entity.DemoEntity;
import io.github.gusforge.foundry.mapper.DemoMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/** 真实 MySQL：逻辑删除、审计填充、唯一索引都是方言相关行为，H2 测过不代表 MySQL 上对。 没有 Docker 时自动跳过。 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class DemoRepositoryIT {

    @Container @ServiceConnection
    static final MySQLContainer MYSQL =
            new MySQLContainer("mysql:8.4").withInitScript("db/migration/V1__init.sql");

    @Autowired DemoRepository repository;
    @Autowired DemoMapper mapper;

    /** 时区用例会改 JVM 默认时区，必须还原，否则会污染同进程里的其他测试。 */
    private final TimeZone originalZone = TimeZone.getDefault();

    @AfterEach
    void restoreDefaultZone() {
        TimeZone.setDefault(originalZone);
    }

    @Test
    void should_fill_audit_fields_on_insert() {
        DemoEntity saved = repository.save(entity("audit"));

        DemoEntity db = repository.findById(saved.getId()).orElseThrow();
        assertThat(db.getCreatedAt()).isNotNull();
        assertThat(db.getUpdatedAt()).isNotNull();
    }

    @Test
    void should_hide_logically_deleted_rows() {
        DemoEntity saved = repository.save(entity("soft"));

        mapper.deleteById(saved.getId());

        assertThat(repository.findById(saved.getId())).isEmpty();
    }

    // ---- 唯一索引 uk_name_deleted(name, deleted) 与逻辑删除的交互 ----
    // 每个用例用各自的 name，容器是共享的，避免互相污染。

    @Test
    void should_reject_duplicate_name_among_active_rows() {
        repository.save(entity("dup-active"));

        assertThatThrownBy(() -> repository.save(entity("dup-active")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_allow_recreating_a_name_after_soft_delete() {
        DemoEntity first = repository.save(entity("recreate"));
        mapper.deleteById(first.getId());

        DemoEntity second = repository.save(entity("recreate"));

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(repository.findByName("recreate"))
                .map(DemoEntity::getId)
                .contains(second.getId());
    }

    /**
     * 已知限制，这里锁定当前行为：(name, deleted) 只能容纳一条同名软删记录， 同名记录第二次软删会撞唯一键。
     *
     * <p>如果改成"删除时把 deleted 写成时间戳/主键"，这个用例应当失败，届时把它改成断言软删成功。
     */
    @Test
    void should_fail_on_second_soft_delete_of_the_same_name_known_limitation() {
        DemoEntity first = repository.save(entity("twice"));
        mapper.deleteById(first.getId());
        DemoEntity second = repository.save(entity("twice"));

        assertThatThrownBy(() -> mapper.deleteById(second.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 软删失败后状态不应改变：这条记录依然可见
        assertThat(repository.findById(second.getId())).isPresent();
    }

    // ---- DATETIME(3) 精度 ----
    // Java 的 LocalDateTime.now() 有微秒/纳秒，列只存毫秒。

    @Test
    void should_roundtrip_millisecond_precision_exactly() {
        LocalDateTime t = LocalDateTime.of(2026, 1, 2, 3, 4, 5, 123_000_000);
        DemoEntity e = entity("ms-exact");
        e.setCreatedAt(t);

        DemoEntity saved = repository.save(e);

        assertThat(repository.findById(saved.getId()).orElseThrow().getCreatedAt()).isEqualTo(t);
    }

    @Test
    void should_drop_sub_millisecond_digits_within_one_millisecond() {
        LocalDateTime t = LocalDateTime.of(2026, 1, 2, 3, 4, 5, 123_456_789);
        DemoEntity e = entity("ms-sub");
        e.setCreatedAt(t);

        DemoEntity saved = repository.save(e);

        LocalDateTime stored = repository.findById(saved.getId()).orElseThrow().getCreatedAt();
        // 只断言性质，不断言是四舍五入还是截断：这取决于驱动与 sql_mode，不该被测试绑死
        assertThat(stored.getNano() % 1_000_000).isZero();
        assertThat(Duration.between(stored, t).abs()).isLessThanOrEqualTo(Duration.ofMillis(1));
    }

    @Test
    void should_store_audit_fields_at_millisecond_precision() {
        DemoEntity saved = repository.save(entity("audit-ms"));

        DemoEntity db = repository.findById(saved.getId()).orElseThrow();
        assertThat(db.getCreatedAt().getNano() % 1_000_000).isZero();
        assertThat(db.getUpdatedAt().getNano() % 1_000_000).isZero();
    }

    // ---- 时区 ----
    // 列是 DATETIME、字段是 LocalDateTime，都不带时区：驱动不应平移；
    // 真正的时区依赖在于审计字段由 JVM 默认时区的 now() 生成。

    @ParameterizedTest
    @ValueSource(strings = {"UTC", "Asia/Shanghai", "America/New_York"})
    void should_not_shift_local_date_time_whatever_the_jvm_zone(String zone) {
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        // 2026-03-08 02:30 在纽约不存在（夏令时空档），2026-11-01 01:30 在纽约出现两次，
        // 任何经过 java.sql.Timestamp / 时区换算的路径都会在这两个点上出错。
        LocalDateTime gap = LocalDateTime.of(2026, 3, 8, 2, 30, 0);
        LocalDateTime overlap = LocalDateTime.of(2026, 11, 1, 1, 30, 0);

        for (LocalDateTime t : new LocalDateTime[] {gap, overlap}) {
            DemoEntity e = entity("tz-" + zone + "-" + t);
            e.setCreatedAt(t);
            DemoEntity saved = repository.save(e);

            assertThat(repository.findById(saved.getId()).orElseThrow().getCreatedAt())
                    .as("zone=%s value=%s", zone, t)
                    .isEqualTo(t);
        }
    }

    @Test
    void should_fill_audit_fields_in_utc_when_jvm_zone_is_utc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        DemoEntity saved = repository.save(entity("audit-utc"));

        LocalDateTime created = repository.findById(saved.getId()).orElseThrow().getCreatedAt();
        assertThat(Duration.between(created, LocalDateTime.now(ZoneOffset.UTC)).abs())
                .isLessThan(Duration.ofSeconds(30));
    }

    /**
     * 审计字段跟随 JVM 默认时区，而不是数据库或 UTC。这正是 Application.main 必须在 Spring 启动前 设置 UTC
     * 的原因：不设的话，部署在不同时区机器上的实例会写入不同的墙上时间。
     */
    @Test
    void should_fill_audit_fields_in_jvm_zone_not_utc() {
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        TimeZone.setDefault(TimeZone.getTimeZone(shanghai));

        DemoEntity saved = repository.save(entity("audit-shanghai"));

        LocalDateTime created = repository.findById(saved.getId()).orElseThrow().getCreatedAt();
        assertThat(Duration.between(created, LocalDateTime.now(shanghai)).abs())
                .isLessThan(Duration.ofSeconds(30));
        assertThat(Duration.between(created, LocalDateTime.now(ZoneOffset.UTC)).abs())
                .as("应与 UTC 相差约 8 小时")
                .isGreaterThan(Duration.ofHours(7));
    }

    private static DemoEntity entity(String name) {
        DemoEntity e = new DemoEntity();
        e.setName(name);
        e.setStatus(0);
        return e;
    }
}
