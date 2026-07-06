package com.LastBite.modules.user.entity;

import com.LastBite.modules.bag.enums.BagType;
import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.user.enums.CollectionTimeSlot;
import com.LastBite.modules.user.enums.DiscoveryOnboardingStatus;
import com.LastBite.modules.user.enums.PreferredDiet;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "user_discovery_preferences", indexes = {
        @Index(name = "idx_user_discovery_preferences_user", columnList = "user_id")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UserDiscoveryPreference extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_diet", nullable = false, length = 30)
    @Builder.Default
    private PreferredDiet preferredDiet = PreferredDiet.MEAT;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_bag_type", nullable = false, length = 20)
    @Builder.Default
    private BagType preferredBagType = BagType.STANDARD;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_preferred_collection_times",
            joinColumns = @JoinColumn(name = "preference_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "slot", nullable = false, length = 30)
    @Builder.Default
    private Set<CollectionTimeSlot> preferredCollectionTimes = new LinkedHashSet<>();

    @Column(name = "default_location_label", length = 255)
    private String defaultLocationLabel;

    @Column(name = "default_lat")
    private Double defaultLat;

    @Column(name = "default_lng")
    private Double defaultLng;

    @Column(name = "default_radius_km")
    private Double defaultRadiusKm;

    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_status", nullable = false, length = 30)
    @Builder.Default
    private DiscoveryOnboardingStatus onboardingStatus = DiscoveryOnboardingStatus.NOT_STARTED;
}
