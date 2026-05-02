package com.yapeseguro.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateExpenseCategoryRequest {

    @NotBlank
    @Size(max = 100)
    private String categoryName;

    @Size(max = 50)
    private String iconCode;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "El color debe tener formato HEX. Ejemplo: #7C3AED")
    private String colorHex;
}