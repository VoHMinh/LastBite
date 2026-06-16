package com.LastBite.modules.store.enums;

/**
 * Trạng thái admin xác minh cửa hàng.
 * <p>
 * Luồng: PENDING → VERIFIED (hiển thị trên app) / REJECTED (kèm lý do)
 */
public enum VerificationStatus {
    DRAFT,
    /** Đang chờ admin duyệt. */
    PENDING,
    CHANGES_REQUESTED,
    /** Đã duyệt — cửa hàng hiển thị với khách hàng. */
    VERIFIED,
    /** Bị từ chối — chủ cửa hàng có thể chỉnh sửa và gửi lại. */
    REJECTED
}
