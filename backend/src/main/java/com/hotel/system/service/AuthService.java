package com.hotel.system.service;

import com.hotel.system.model.User;
import com.hotel.system.repository.UserRepository;
import com.hotel.system.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public Map<String, Object> registerUser(User user) {
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already registered: " + user.getEmail());
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if (user.getRole() == null || user.getRole().trim().isEmpty()) {
            user.setRole("GUEST");
        } else {
            user.setRole(user.getRole().trim().toUpperCase());
        }
        user.setCreatedAt(LocalDateTime.now());
        User savedUser = userRepository.save(user);

        String token = jwtProvider.generateToken(
                savedUser.getEmail(),
                savedUser.getRole(),
                savedUser.getFullName(),
                savedUser.getId()
        );

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("email", savedUser.getEmail());
        response.put("role", savedUser.getRole());
        response.put("fullName", savedUser.getFullName());
        response.put("userId", savedUser.getId());
        return response;
    }

    public Map<String, Object> authenticate(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        String token = jwtProvider.generateToken(
                user.getEmail(),
                user.getRole(),
                user.getFullName(),
                user.getId()
        );

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("email", user.getEmail());
        response.put("role", user.getRole());
        response.put("fullName", user.getFullName());
        response.put("userId", user.getId());
        return response;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}
