package io.github.gusforge.foundry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.gusforge.foundry.entity.DemoEntity;
import io.github.gusforge.foundry.exception.BizException;
import io.github.gusforge.foundry.exception.ErrorCode;
import io.github.gusforge.foundry.model.DemoCreateDTO;
import io.github.gusforge.foundry.model.DemoVO;
import io.github.gusforge.foundry.repository.DemoRepository;
import io.github.gusforge.foundry.service.impl.DemoServiceImpl;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DemoServiceImplTest {

    @Mock DemoRepository repository;
    @InjectMocks DemoServiceImpl service;

    @Test
    void should_throw_not_found_when_missing() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(1L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEMO_NOT_FOUND);
    }

    @Test
    void should_reject_duplicated_name_without_inserting() {
        when(repository.findByName("a")).thenReturn(Optional.of(new DemoEntity()));

        assertThatThrownBy(() -> service.create(new DemoCreateDTO("a")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEMO_NAME_DUPLICATED);
        verify(repository, never()).save(any());
    }

    @Test
    void should_return_vo_not_entity_on_create() {
        when(repository.findByName("a")).thenReturn(Optional.empty());
        when(repository.save(any()))
                .thenAnswer(
                        inv -> {
                            DemoEntity e = inv.getArgument(0);
                            e.setId(7L);
                            return e;
                        });

        DemoVO vo = service.create(new DemoCreateDTO("a"));

        assertThat(vo.id()).isEqualTo(7L);
        assertThat(vo.name()).isEqualTo("a");
    }
}
