package io.github.gusforge.foundry.service.impl;

import io.github.gusforge.foundry.entity.DemoEntity;
import io.github.gusforge.foundry.exception.BizException;
import io.github.gusforge.foundry.exception.ErrorCode;
import io.github.gusforge.foundry.model.DemoCreateDTO;
import io.github.gusforge.foundry.model.DemoVO;
import io.github.gusforge.foundry.repository.DemoRepository;
import io.github.gusforge.foundry.service.DemoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DemoServiceImpl implements DemoService {

    private final DemoRepository repository;

    @Override
    public DemoVO getById(Long id) {
        return repository
                .findById(id)
                .map(this::toVO)
                .orElseThrow(() -> new BizException(ErrorCode.DEMO_NOT_FOUND));
    }

    @Override
    public DemoVO create(DemoCreateDTO dto) {
        if (repository.findByName(dto.name()).isPresent()) {
            throw new BizException(ErrorCode.DEMO_NAME_DUPLICATED);
        }
        DemoEntity entity = new DemoEntity();
        entity.setName(dto.name());
        entity.setStatus(0);
        return toVO(repository.save(entity));
    }

    /** Entity 不出 service 层，转 VO 返回。 */
    private DemoVO toVO(DemoEntity e) {
        return new DemoVO(e.getId(), e.getName(), e.getStatus(), e.getCreatedAt());
    }
}
