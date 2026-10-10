package io.github.gusforge.foundry.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.gusforge.foundry.entity.DemoEntity;
import io.github.gusforge.foundry.mapper.DemoMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 把 MyBatis-Plus 的 API 收口在 dao 层，Wrapper 不泄漏到 service。 */
@Repository
@RequiredArgsConstructor
public class DemoRepository {

    private final DemoMapper mapper;

    public Optional<DemoEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public Optional<DemoEntity> findByName(String name) {
        return Optional.ofNullable(
                mapper.selectOne(
                        new LambdaQueryWrapper<DemoEntity>().eq(DemoEntity::getName, name)));
    }

    public DemoEntity save(DemoEntity entity) {
        mapper.insert(entity);
        return entity;
    }
}
