package com.LastBite.modules.media.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ConfirmMediaUploadRequest {

    @NotNull(message = "Upload ID không được để trống")
    private UUID uploadId;

    @NotBlank(message = "Object key không được để trống")
    private String key;
}
