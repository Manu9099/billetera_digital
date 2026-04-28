package com.yapeseguro.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateBusinessProfileRequest {

    @NotBlank
    @Size(max = 255)
    private String businessName;

    @NotBlank
    @Pattern(regexp = "^\\d{11}$", message = "RUC must be 11 digits")
    private String ruc;

    @NotBlank
    private String businessCategory;

    private String description;

    private String address;

    private Double latitude;

    private Double longitude;

    private String district;

    @Pattern(regexp = "^9\\d{8}$")
    private String businessPhoneNumber;

    @Email
    private String businessEmail;
}