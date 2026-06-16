package com.LastBite.common.config;

import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.auth.service.impl.RoleAssignmentService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminSeederTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final Environment environment = mock(Environment.class);
    private final RoleAssignmentService roleAssignmentService = mock(RoleAssignmentService.class);
    private final ApplicationArguments arguments = mock(ApplicationArguments.class);

    @Test
    void prodWithExistingAdminDoesNotRequirePassword() {
        AdminSeeder seeder = seederWith("Admin@123456");
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(userRepository.existsByEmail("admin@lastbite.com")).thenReturn(true);

        assertDoesNotThrow(() -> seeder.run(arguments));

        verify(userRepository, never()).save(any());
    }

    @Test
    void prodWithoutAdminRequiresNonDefaultPassword() {
        AdminSeeder seeder = seederWith("Admin@123456");
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(userRepository.existsByEmail("admin@lastbite.com")).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> seeder.run(arguments));

        verify(userRepository, never()).save(any());
    }

    @Test
    void prodWithoutAdminCreatesAdminWhenPasswordIsConfigured() {
        AdminSeeder seeder = seederWith("MatKhauManh@2026");
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(userRepository.existsByEmail("admin@lastbite.com")).thenReturn(false);
        when(passwordEncoder.encode("MatKhauManh@2026")).thenReturn("hashed-password");

        assertDoesNotThrow(() -> seeder.run(arguments));

        verify(userRepository).save(any());
    }

    private AdminSeeder seederWith(String password) {
        AdminSeeder seeder = new AdminSeeder(
                userRepository, passwordEncoder, environment, roleAssignmentService);
        ReflectionTestUtils.setField(seeder, "adminEmail", "admin@lastbite.com");
        ReflectionTestUtils.setField(seeder, "adminPassword", password);
        ReflectionTestUtils.setField(seeder, "adminFullName", "LastBite Admin");
        return seeder;
    }
}
