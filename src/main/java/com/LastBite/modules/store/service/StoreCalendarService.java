package com.LastBite.modules.store.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.store.dto.request.StoreClosureDayRequest;
import com.LastBite.modules.store.dto.request.StoreSpecialHoursRequest;
import com.LastBite.modules.store.dto.response.StoreClosureDayResponse;
import com.LastBite.modules.store.dto.response.StoreSpecialHoursResponse;
import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.store.entity.StoreClosureDay;
import com.LastBite.modules.store.entity.StoreSpecialHours;
import com.LastBite.modules.store.repository.StoreClosureDayRepository;
import com.LastBite.modules.store.repository.StoreSpecialHoursRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StoreCalendarService {

    private final StoreClosureDayRepository closureDayRepository;
    private final StoreSpecialHoursRepository specialHoursRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public boolean isStoreClosed(Store store, LocalDate date) {
        if (closureDayRepository.existsByStoreIdAndClosedDate(store.getId(), date)) {
            return true;
        }
        return specialHoursRepository.findByStoreIdAndSpecialDate(store.getId(), date)
                .map(special -> special.isClosed())
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean supportsPickupWindow(Store store, LocalDate date, LocalTime pickupStart, LocalTime pickupEnd) {
        if (isStoreClosed(store, date)) {
            return false;
        }
        return specialHoursRepository.findByStoreIdAndSpecialDate(store.getId(), date)
                .filter(special -> !special.isClosed())
                .map(special -> !pickupStart.isBefore(special.getOpenTime())
                        && !pickupEnd.isAfter(special.getCloseTime()))
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public List<StoreClosureDayResponse> listClosures(Store store, LocalDate from, LocalDate to) {
        DateRange range = range(from, to);
        return closureDayRepository
                .findAllByStoreIdAndClosedDateBetweenOrderByClosedDateAsc(store.getId(), range.from(), range.to())
                .stream()
                .map(this::toClosureResponse)
                .toList();
    }

    @Transactional
    public StoreClosureDayResponse upsertClosure(UUID actorId, Store store, StoreClosureDayRequest request) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        StoreClosureDay closure = closureDayRepository.findByStoreIdAndClosedDate(store.getId(), request.getClosedDate())
                .orElseGet(() -> StoreClosureDay.builder()
                        .store(store)
                        .closedDate(request.getClosedDate())
                        .createdBy(actor)
                        .build());
        closure.setReason(trimToNull(request.getReason()));
        closure.setCreatedBy(actor);
        return toClosureResponse(closureDayRepository.save(closure));
    }

    @Transactional
    public void deleteClosure(Store store, LocalDate closedDate) {
        StoreClosureDay closure = closureDayRepository.findByStoreIdAndClosedDate(store.getId(), closedDate)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay ngay nghi"));
        closureDayRepository.delete(closure);
    }

    @Transactional(readOnly = true)
    public List<StoreSpecialHoursResponse> listSpecialHours(Store store, LocalDate from, LocalDate to) {
        DateRange range = range(from, to);
        return specialHoursRepository
                .findAllByStoreIdAndSpecialDateBetweenOrderBySpecialDateAsc(store.getId(), range.from(), range.to())
                .stream()
                .map(this::toSpecialHoursResponse)
                .toList();
    }

    @Transactional
    public StoreSpecialHoursResponse upsertSpecialHours(UUID actorId, Store store, StoreSpecialHoursRequest request) {
        if (!request.isClosed()) {
            if (request.getOpenTime() == null || request.getCloseTime() == null
                    || !request.getOpenTime().isBefore(request.getCloseTime())) {
                throw new ApiException(ErrorCode.INVALID_INPUT, "Gio mo cua dac biet khong hop le");
            }
        }
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        StoreSpecialHours special = specialHoursRepository.findByStoreIdAndSpecialDate(store.getId(), request.getSpecialDate())
                .orElseGet(() -> StoreSpecialHours.builder()
                        .store(store)
                        .specialDate(request.getSpecialDate())
                        .createdBy(actor)
                        .build());
        special.setClosed(request.isClosed());
        special.setOpenTime(request.isClosed() ? null : request.getOpenTime());
        special.setCloseTime(request.isClosed() ? null : request.getCloseTime());
        special.setReason(trimToNull(request.getReason()));
        special.setCreatedBy(actor);
        return toSpecialHoursResponse(specialHoursRepository.save(special));
    }

    @Transactional
    public void deleteSpecialHours(Store store, LocalDate specialDate) {
        StoreSpecialHours special = specialHoursRepository.findByStoreIdAndSpecialDate(store.getId(), specialDate)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay gio dac biet"));
        specialHoursRepository.delete(special);
    }

    private DateRange range(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now(clock) : from;
        LocalDate end = to == null ? start.plusMonths(3) : to;
        if (end.isBefore(start)) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Khoang ngay khong hop le");
        }
        return new DateRange(start, end);
    }

    private StoreClosureDayResponse toClosureResponse(StoreClosureDay closure) {
        return StoreClosureDayResponse.builder()
                .id(closure.getId())
                .storeId(closure.getStore().getId())
                .closedDate(closure.getClosedDate())
                .reason(closure.getReason())
                .createdByUserId(closure.getCreatedBy() == null ? null : closure.getCreatedBy().getId())
                .createdAt(closure.getCreatedAt())
                .updatedAt(closure.getUpdatedAt())
                .build();
    }

    private StoreSpecialHoursResponse toSpecialHoursResponse(StoreSpecialHours special) {
        return StoreSpecialHoursResponse.builder()
                .id(special.getId())
                .storeId(special.getStore().getId())
                .specialDate(special.getSpecialDate())
                .openTime(special.getOpenTime())
                .closeTime(special.getCloseTime())
                .closed(special.isClosed())
                .reason(special.getReason())
                .createdByUserId(special.getCreatedBy() == null ? null : special.getCreatedBy().getId())
                .createdAt(special.getCreatedAt())
                .updatedAt(special.getUpdatedAt())
                .build();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }
}
