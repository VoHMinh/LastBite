package com.LastBite.modules.auth.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.auth.enums.RoleScope;
import com.LastBite.modules.auth.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "roles")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Role extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 50)
    private UserRole code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoleScope scope;
}
