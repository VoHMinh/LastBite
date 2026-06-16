package com.LastBite.modules.store.enums;

/**
 * Trạng thái vận hành của cửa hàng.
 */
public enum StoreStatus {
    DRAFT,
    /** Cửa hàng đang mở và hoạt động. */
    ACTIVE,
    /** Chủ cửa hàng tạm ngưng hoạt động. */
    PAUSED,
    /** Chủ cửa hàng đóng vĩnh viễn. */
    CLOSED,
    /** Bị admin đình chỉ do vi phạm chính sách. */
    SUSPENDED
}
