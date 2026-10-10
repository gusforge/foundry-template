package io.github.gusforge.foundry.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DemoCreateDTO(@NotBlank @Size(max = 128) String name) {}
