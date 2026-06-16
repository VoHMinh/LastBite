package com.LastBite.modules.review.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.media.entity.MediaUpload;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "review_photos")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewPhoto extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_upload_id", nullable = false, unique = true)
    private MediaUpload mediaUpload;
}
