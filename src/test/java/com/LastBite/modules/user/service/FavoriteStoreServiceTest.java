package com.LastBite.modules.user.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.store.enums.StoreCategory;
import com.LastBite.modules.store.enums.StoreStatus;
import com.LastBite.modules.store.enums.VerificationStatus;
import com.LastBite.modules.store.repository.StoreRepository;
import com.LastBite.modules.user.entity.FavoriteStore;
import com.LastBite.modules.user.repository.FavoriteStoreRepository;
import com.LastBite.modules.user.service.impl.FavoriteStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class FavoriteStoreServiceTest {

    private final FavoriteStoreRepository favoriteStoreRepository = mock(FavoriteStoreRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final FavoriteStoreService service = new FavoriteStoreService(
            favoriteStoreRepository, userRepository, storeRepository);

    @Test
    void addCreatesFavoriteStore() {
        UUID userId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        User user = user(userId);
        Store store = store(storeId);
        when(favoriteStoreRepository.findByUserIdAndStoreId(userId, storeId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(storeRepository.findById(storeId)).thenReturn(Optional.of(store));

        var response = service.add(userId, storeId);

        assertEquals(storeId, response.getId());
        verify(favoriteStoreRepository).save(any(FavoriteStore.class));
    }

    @Test
    void addExistingFavoriteIsIdempotent() {
        UUID userId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        Store store = store(storeId);
        when(favoriteStoreRepository.findByUserIdAndStoreId(userId, storeId))
                .thenReturn(Optional.of(FavoriteStore.builder().store(store).build()));

        var response = service.add(userId, storeId);

        assertEquals(storeId, response.getId());
        verify(favoriteStoreRepository, never()).save(any());
    }

    @Test
    void deleteIsIdempotent() {
        UUID userId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        when(favoriteStoreRepository.deleteByUserIdAndStoreId(userId, storeId)).thenReturn(0L);

        service.delete(userId, storeId);

        verify(favoriteStoreRepository).deleteByUserIdAndStoreId(userId, storeId);
    }

    @Test
    void addMissingStoreThrowsStoreNotFound() {
        UUID userId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        when(favoriteStoreRepository.findByUserIdAndStoreId(userId, storeId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId)));
        when(storeRepository.findById(storeId)).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> service.add(userId, storeId));
        verify(favoriteStoreRepository, never()).save(any());
    }

    @Test
    void listReturnsStores() {
        UUID userId = UUID.randomUUID();
        Store store = store(UUID.randomUUID());
        when(favoriteStoreRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(FavoriteStore.builder().store(store).build()));

        var result = service.list(userId);

        assertEquals(1, result.size());
        assertEquals(store.getId(), result.getFirst().getId());
    }

    private User user(UUID userId) {
        User user = User.builder()
                .email("user@test.local")
                .fullName("Test User")
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private Store store(UUID storeId) {
        Store store = Store.builder()
                .name("Store Test")
                .slug("store-test")
                .category(StoreCategory.CAFE)
                .address("Quan 1")
                .status(StoreStatus.ACTIVE)
                .verificationStatus(VerificationStatus.VERIFIED)
                .build();
        ReflectionTestUtils.setField(store, "id", storeId);
        return store;
    }
}
