package io.github.gusforge.foundry.controller;

import io.github.gusforge.foundry.model.DemoCreateDTO;
import io.github.gusforge.foundry.model.DemoVO;
import io.github.gusforge.foundry.service.DemoService;
import io.github.gusforge.foundry.support.DataResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demos")
@RequiredArgsConstructor
@Tag(name = "Demo", description = "示例接口，用于演示分层与约定，实际项目中删除")
public class DemoController {

    private final DemoService service;

    @GetMapping("/{id}")
    @Operation(summary = "按 id 查询")
    public DataResponse<DemoVO> get(@PathVariable Long id) {
        return DataResponse.of(service.getById(id));
    }

    @PostMapping
    @Operation(summary = "创建")
    public DataResponse<DemoVO> create(@Valid @RequestBody DemoCreateDTO dto) {
        return DataResponse.of(service.create(dto));
    }
}
