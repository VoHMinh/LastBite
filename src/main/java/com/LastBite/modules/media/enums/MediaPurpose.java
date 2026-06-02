package com.LastBite.modules.media.enums;

public enum MediaPurpose {
    STORE_COVER(MediaType.IMAGE, true),
    STORE_LOGO(MediaType.IMAGE, true),
    BUSINESS_LICENSE(MediaType.IMAGE, true),
    USER_AVATAR(MediaType.IMAGE, false),
    FEEDBACK_IMAGE(MediaType.IMAGE, false),
    FEEDBACK_VIDEO(MediaType.VIDEO, false);

    private final MediaType mediaType;
    private final boolean storePurpose;

    MediaPurpose(MediaType mediaType, boolean storePurpose) {
        this.mediaType = mediaType;
        this.storePurpose = storePurpose;
    }

    public MediaType mediaType() {
        return mediaType;
    }

    public boolean isStorePurpose() {
        return storePurpose;
    }
}
