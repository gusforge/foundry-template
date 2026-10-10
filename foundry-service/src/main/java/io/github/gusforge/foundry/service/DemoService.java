package io.github.gusforge.foundry.service;

import io.github.gusforge.foundry.model.DemoCreateDTO;
import io.github.gusforge.foundry.model.DemoVO;

public interface DemoService {

    DemoVO getById(Long id);

    DemoVO create(DemoCreateDTO dto);
}
