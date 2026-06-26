package com.LastBite.modules.media.service;

import com.LastBite.common.config.AwsS3Properties;
import com.LastBite.common.exception.ApiException;
import com.LastBite.modules.auth.entity.Role;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.enums.AccountType;
import com.LastBite.modules.auth.enums.AuthProvider;
import com.LastBite.modules.auth.enums.RoleScope;
import com.LastBite.modules.auth.enums.UserRole;
import com.LastBite.modules.auth.enums.UserStatus;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.media.dto.request.ConfirmMediaUploadRequest;
import com.LastBite.modules.media.dto.request.CreatePresignedUploadRequest;
import com.LastBite.modules.media.entity.MediaUpload;
import com.LastBite.modules.media.enums.MediaPurpose;
import com.LastBite.modules.media.enums.MediaTargetType;
import com.LastBite.modules.media.enums.MediaType;
import com.LastBite.modules.media.enums.MediaUploadStatus;
import com.LastBite.modules.media.repository.MediaUploadRepository;
import com.LastBite.modules.media.service.impl.MediaUploadService;
import com.LastBite.modules.merchant.entity.MerchantBusinessProfile;
import com.LastBite.modules.merchant.enums.BusinessLegalType;
import com.LastBite.modules.merchant.repository.MerchantBusinessProfileRepository;
import com.LastBite.modules.merchant.repository.MerchantDocumentRepository;
import com.LastBite.modules.merchant.repository.MerchantStoreMemberRepository;
import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.store.enums.StoreCategory;
import com.LastBite.modules.store.repository.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaUploadServiceTest {

    private final MediaUploadRepository mediaUploadRepository = mock(MediaUploadRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final MerchantBusinessProfileRepository profileRepository =
            mock(MerchantBusinessProfileRepository.class);
    private final MerchantStoreMemberRepository memberRepository =
            mock(MerchantStoreMemberRepository.class);
    private final MerchantDocumentRepository documentRepository =
            mock(MerchantDocumentRepository.class);
    private final MediaStorageServicePort storageService = mock(MediaStorageServicePort.class);
    private final MediaUrlService mediaUrlService = mock(MediaUrlService.class);
    private final AwsS3Properties properties = new AwsS3Properties(
            "ap-southeast-1", "lastbite", 300, 3600, 5, 50, "https://lastbite.s3.amazonaws.com");
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-06-02T03:00:00Z"), ZoneId.of("UTC"));

    private MediaUploadService service;
    private UUID ownerId;
    private User owner;
    private Store store;

    @BeforeEach
    void setUp() {
        service = new MediaUploadService(
                mediaUploadRepository, userRepository, storeRepository, profileRepository,
                memberRepository, documentRepository, storageService, mediaUrlService, properties, clock);
        ownerId = UUID.randomUUID();
        owner = merchantOwner();
        owner.setId(ownerId);

        MerchantBusinessProfile profile = MerchantBusinessProfile.builder()
                .owner(owner)
                .legalType(BusinessLegalType.INDIVIDUAL)
                .representativeFullName("Test Owner")
                .build();
        profile.setId(UUID.randomUUID());

        store = Store.builder()
                .businessProfile(profile)
                .createdBy(owner)
                .name("Test Store")
                .slug("test-store")
                .category(StoreCategory.BAKERY)
                .address("123 Test")
                .build();
        store.setId(UUID.randomUUID());

        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(storeRepository.findDetailById(store.getId())).thenReturn(Optional.of(store));
        when(storageService.createPresignedPutUrl(any(), any(), any(), any()))
                .thenReturn("https://signed-url");
        when(mediaUrlService.signedUrlForKey(any())).thenReturn("https://signed-get-url");
        when(mediaUrlService.resolveUrl(any(), any())).thenReturn("https://signed-get-url");
        when(mediaUploadRepository.save(any(MediaUpload.class))).thenAnswer(invocation -> {
            MediaUpload upload = invocation.getArgument(0);
            if (upload.getId() == null) upload.setId(UUID.randomUUID());
            return upload;
        });
    }

    @Test
    void createsPresignedUrlForDraftStoreImage() {
        var response = service.createPresignedUploadUrl(
                ownerId, request("store-front.jpg", "image/jpeg", 1_024_000L));

        assertEquals("https://signed-url", response.getUploadUrl());
        assertTrue(response.getKey().startsWith(
                "public/store/" + store.getId() + "/" + ownerId + "/store_cover/"));
        assertTrue(response.getKey().endsWith(".jpg"));
        assertEquals(300, response.getExpiresInSeconds());
    }

    @Test
    void rejectsUnsupportedContentType() {
        var ex = assertThrows(ApiException.class, () -> service.createPresignedUploadUrl(
                ownerId, request("bad.gif", "image/gif", 1024L)));

        assertEquals("Loại media không được hỗ trợ", ex.getErrorCode().getDefaultMessage());
    }

    @Test
    void rejectsConfirmWhenKeyDoesNotMatchUpload() {
        MediaUpload upload = pendingUpload("public/store/a.jpg");
        when(mediaUploadRepository.findByIdAndOwnerId(upload.getId(), ownerId))
                .thenReturn(Optional.of(upload));

        ConfirmMediaUploadRequest request = new ConfirmMediaUploadRequest();
        request.setUploadId(upload.getId());
        request.setKey("public/store/b.jpg");

        assertThrows(ApiException.class, () -> service.confirmUpload(ownerId, request));
    }

    @Test
    void confirmsUploadAndUpdatesTargetStoreCover() {
        String key = "public/store/" + store.getId() + "/cover/a.jpg";
        MediaUpload upload = pendingUpload(key);
        when(mediaUploadRepository.findByIdAndOwnerId(upload.getId(), ownerId))
                .thenReturn(Optional.of(upload));
        when(storageService.objectExists("lastbite", key)).thenReturn(true);

        ConfirmMediaUploadRequest request = new ConfirmMediaUploadRequest();
        request.setUploadId(upload.getId());
        request.setKey(key);

        var response = service.confirmUpload(ownerId, request);

        assertEquals(MediaUploadStatus.CONFIRMED, response.getStatus());
        assertEquals(key, store.getCoverImageKey());
        assertEquals("https://lastbite.s3.amazonaws.com/" + key, store.getCoverImageUrl());
        verify(storeRepository).save(store);
    }

    private CreatePresignedUploadRequest request(
            String fileName, String contentType, long fileSize) {
        CreatePresignedUploadRequest request = new CreatePresignedUploadRequest();
        request.setFileName(fileName);
        request.setContentType(contentType);
        request.setFileSize(fileSize);
        request.setPurpose(MediaPurpose.STORE_COVER);
        request.setTargetType(MediaTargetType.STORE);
        request.setTargetId(store.getId());
        return request;
    }

    private MediaUpload pendingUpload(String key) {
        MediaUpload upload = MediaUpload.builder()
                .owner(owner)
                .purpose(MediaPurpose.STORE_COVER)
                .mediaType(MediaType.IMAGE)
                .targetType(MediaTargetType.STORE)
                .targetId(store.getId())
                .bucket("lastbite")
                .objectKey(key)
                .publicUrl("https://lastbite.s3.amazonaws.com/" + key)
                .contentType("image/jpeg")
                .fileSize(1000)
                .status(MediaUploadStatus.PENDING)
                .build();
        upload.setId(UUID.randomUUID());
        return upload;
    }

    private User merchantOwner() {
        Role role = Role.builder()
                .code(UserRole.MERCHANT_OWNER)
                .scope(RoleScope.PLATFORM)
                .build();
        return User.builder()
                .email("owner@example.com")
                .fullName("Test Owner")
                .roles(Set.of(role))
                .accountType(AccountType.PLATFORM)
                .status(UserStatus.ACTIVE)
                .authProvider(AuthProvider.LOCAL)
                .build();
    }
}
