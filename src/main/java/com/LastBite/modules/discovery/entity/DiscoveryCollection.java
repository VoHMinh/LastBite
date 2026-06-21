package com.LastBite.modules.discovery.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.discovery.enums.DiscoveryCollectionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "discovery_collections", indexes = {
        @Index(name = "idx_discovery_collections_active_order", columnList = "is_active,display_order")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class DiscoveryCollection extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String slug;

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DiscoveryCollectionType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_definition", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> ruleDefinition = new LinkedHashMap<>();

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    @Column(name = "max_items", nullable = false)
    @Builder.Default
    private int maxItems = 10;

    @Column(name = "min_items_to_display", nullable = false)
    @Builder.Default
    private int minItemsToDisplay = 3;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
