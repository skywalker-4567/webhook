package com.example.razorpaywebhook.security;

import com.example.razorpaywebhook.domain.entity.User;
import com.example.razorpaywebhook.dto.LoginResponse;
import com.example.razorpaywebhook.enums.UserRole;
import com.example.razorpaywebhook.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public User register(String username, String rawPassword, UserRole role) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .build();
        User saved = userRepository.save(user);
        log.info("Registered user: username={} role={}", username, role);
        return saved;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(String username, String rawPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        String token = jwtService.generateToken(username, user.getRole().name());
        log.info("Login successful: username={}", username);

        return LoginResponse.builder()
                .token(token)
                .expiresIn(3600L)
                .build();
    }
}