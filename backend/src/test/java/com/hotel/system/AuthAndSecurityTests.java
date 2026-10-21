package com.hotel.system;

import com.hotel.system.model.User;
import com.hotel.system.repository.UserRepository;
import com.hotel.system.security.JwtProvider;
import com.hotel.system.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AuthAndSecurityTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        userRepository.findByEmail("testuser@example.com").ifPresent(userRepository::delete);
    }

    @Test
    void testRegisterAndLogin() {
        User user = User.builder()
                .email("testuser@example.com")
                .password("Password@123")
                .fullName("Test User")
                .role("GUEST")
                .phoneNumber("+91-9999999999")
                .build();

        Map<String, Object> registerResult = authService.registerUser(user);
        assertNotNull(registerResult.get("token"));
        assertEquals("testuser@example.com", registerResult.get("email"));
        assertEquals("GUEST", registerResult.get("role"));

        // Verify JWT claims
        String token = (String) registerResult.get("token");
        assertTrue(jwtProvider.isTokenValid(token));
        assertEquals("testuser@example.com", jwtProvider.extractUsername(token));
        assertEquals("GUEST", jwtProvider.extractRole(token));

        // Test login
        Map<String, Object> loginResult = authService.authenticate("testuser@example.com", "Password@123");
        assertNotNull(loginResult.get("token"));
        assertEquals("GUEST", loginResult.get("role"));
    }

    @Test
    void testInvalidPasswordThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            authService.authenticate("guest@hotel.demo", "WrongPassword!999");
        });
    }

    @Test
    void testDuplicateRegistrationThrowsException() {
        User user = User.builder()
                .email("testuser@example.com")
                .password("Password@123")
                .fullName("Test User")
                .role("GUEST")
                .build();

        authService.registerUser(user);

        User duplicate = User.builder()
                .email("testuser@example.com")
                .password("OtherPassword@456")
                .fullName("Duplicate User")
                .role("GUEST")
                .build();

        assertThrows(IllegalArgumentException.class, () -> authService.registerUser(duplicate));
    }
}
