package com.LastBite.modules.auth.service.impl;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.auth.entity.Role;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.enums.RoleScope;
import com.LastBite.modules.auth.enums.UserRole;
import com.LastBite.modules.auth.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleAssignmentService {

    private final RoleRepository roleRepository;

    public Role get(UserRole code, RoleScope expectedScope) {
        Role role = roleRepository.findByCode(code)
                .orElseThrow(() -> new ApiException(ErrorCode.UNEXPECTED_ERROR, "Role chưa được cấu hình"));
        if (role.getScope() != expectedScope) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Role không đúng phạm vi");
        }
        return role;
    }

    public void addPlatformRole(User user, UserRole code) {
        user.addRole(get(code, RoleScope.PLATFORM));
    }
}
