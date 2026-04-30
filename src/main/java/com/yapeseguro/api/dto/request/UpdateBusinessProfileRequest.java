package com.yapeseguro.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateBusinessProfileRequest {

    @Size(max = 255)
    private String businessName;

    @Pattern(regexp = "^\\d{11}$", message = "RUC must be 11 digits")
    private String ruc;

    @Size(max = 100)
    private String businessCategory;

    @Size(max = 2000)
    private String description;

    @Size(max = 255)
    private String address;

    private Double latitude;

    private Double longitude;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String district;

    @Pattern(regexp = "^9\\d{8}$", message = "Phone must be a valid Peruvian number")
    private String businessPhoneNumber;

    @Email
    @Size(max = 255)
    private String businessEmail;

    @Size(max = 255)
    private String website;

    private Boolean autoConfirmReceipts;

    private Boolean showFrequentCustomers;

    private Boolean active;
}
