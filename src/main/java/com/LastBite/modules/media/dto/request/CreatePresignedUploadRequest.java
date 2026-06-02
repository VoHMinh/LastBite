package com.LastBite.modules.media.dto.request;

import com.LastBite.modules.media.enums.MediaPurpose;
import com.LastBite.modules.media.enums.MediaTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreatePresignedUploadRequest {

    @NotBlank(message = "Tên file không được để trống")
    @Size(max = 255, message = "Tên file tối đa 255 ký tự")
    private String fileName;

    @NotBlank(message = "Content-Type không được để trống")
    @Size(max = 100, message = "Content-Type tối đa 100 ký tự")
    private String contentType;

    @NotNull(message = "Kích thước file không được để trống")
    @Positive(message = "Kích thước file phải lớn hơn 0")
    private Long fileSize;

    @NotNull(message = "Mục đích upload không được để trống")
    private MediaPurpose purpose;

    private MediaTargetType targetType;

    private UUID targetId;
}
