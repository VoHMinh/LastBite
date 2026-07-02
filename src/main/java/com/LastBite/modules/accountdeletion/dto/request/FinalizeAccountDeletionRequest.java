package com.LastBite.modules.accountdeletion.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FinalizeAccountDeletionRequest {
    private boolean force;

    @Size(max = 1000)
    private String adminNote;
}
