package io.github.gusforge.foundry.model;

import java.time.LocalDateTime;

public record DemoVO(Long id, String name, Integer status, LocalDateTime createdAt) {}
