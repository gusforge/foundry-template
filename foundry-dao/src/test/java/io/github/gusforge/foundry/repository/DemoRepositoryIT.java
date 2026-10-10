package io.github.gusforge.foundry.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gusforge.foundry.entity.DemoEntity;
import io.github.gusforge.foundry.mapper.DemoMapper;
import org.junit.jupiter.api.Test;
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

    private static DemoEntity entity(String name) {
        DemoEntity e = new DemoEntity();
        e.setName(name);
        e.setStatus(0);
        return e;
    }
}
