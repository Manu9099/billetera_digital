package com.yapeseguro.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AuthResponse {

    private UUID userId;
    private String email;
    private String phoneNumber;
    private String firstName;
    private String lastName;

    private String accessToken;
    private String tokenType;
}