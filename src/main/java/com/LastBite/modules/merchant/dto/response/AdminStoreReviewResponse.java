package com.LastBite.modules.merchant.dto.response;

import com.LastBite.modules.store.dto.response.StoreDetailResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminStoreReviewResponse {
    private StoreDetailResponse store;
    private BusinessProfileResponse businessProfile;
    private List<BankAccountResponse> bankAccounts;
    private List<AdminDocumentResponse> documents;
    private String pendingBusinessProfileSnapshot;
    private String pendingStoreSnapshot;
}
