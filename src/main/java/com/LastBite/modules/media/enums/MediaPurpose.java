package com.LastBite.modules.media.enums;

public enum MediaPurpose {
    STORE_COVER(MediaType.IMAGE, true, false),
    STORE_LOGO(MediaType.IMAGE, true, false),
    STORE_STOREFRONT(MediaType.IMAGE, true, false),
    STORE_MENU(MediaType.IMAGE, true, false),
    STORE_GALLERY(MediaType.IMAGE, true, false),
    BUSINESS_LICENSE(MediaType.IMAGE, false, true),
    REPRESENTATIVE_ID(MediaType.IMAGE, false, true),
    AUTHORIZATION_LETTER(MediaType.IMAGE, false, true),
    BANK_PROOF(MediaType.IMAGE, false, true),
    FOOD_SAFETY_CERTIFICATE(MediaType.IMAGE, false, true),
    USER_AVATAR(MediaType.IMAGE, false, false),
    FEEDBACK_IMAGE(MediaType.IMAGE, false, false),
    FEEDBACK_VIDEO(MediaType.VIDEO, false, false);

    private final MediaType mediaType;
    private final boolean storePurpose;
    private final boolean privateObject;

    MediaPurpose(MediaType mediaType, boolean storePurpose, boolean privateObject) {
        this.mediaType = mediaType;
        this.storePurpose = storePurpose;
        this.privateObject = privateObject;
    }

    public MediaType mediaType() {
        return mediaType;
    }

    public boolean isStorePurpose() {
        return storePurpose;
    }

    public boolean isPrivateObject() {
        return privateObject;
    }

    public boolean isBusinessDocument() {
        return privateObject;
    }
}
