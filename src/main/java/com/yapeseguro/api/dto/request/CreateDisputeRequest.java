package com.yapeseguro.api.dto.request;

import com.yapeseguro.infrastructure.persistence.entities.DisputeEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateDisputeRequest {

    @NotNull(message = "El motivo de disputa es obligatorio")
    private DisputeEntity.DisputeReason reason;

    @NotBlank(message = "La descripción de la disputa es obligatoria")
    @Size(max = 3000, message = "La descripción no puede exceder 3000 caracteres")
    private String description;

    @Size(max = 2000, message = "La URL de imagen no puede exceder 2000 caracteres")
    private String qrPhotoUrl;

    @Size(max = 5000, message = "El chat no puede exceder 5000 caracteres")
    private String chatTranscript;
}