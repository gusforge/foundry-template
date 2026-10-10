package io.github.gusforge.foundry.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gusforge.foundry.entity.DemoEntity;
import io.github.gusforge.foundry.mapper.DemoMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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

    private static DemoEntity entity(String name) {
        DemoEntity e = new DemoEntity();
        e.setName(name);
        e.setStatus(0);
        return e;
    }
}
