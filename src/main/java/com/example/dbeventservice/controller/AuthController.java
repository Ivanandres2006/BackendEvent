package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.JwtUtils;
import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.model.Enum.Role;
import com.example.dbeventservice.model.User;
import com.example.dbeventservice.repositories.UserRepository;
import com.example.dbeventservice.service.*;
import jakarta.validation.Valid;
import lombok.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    private SmsService smsService;

    @Autowired
    private GoogleVerifierService googleVerifierService;

    @Autowired
    private AppleVerifierService appleVerifierService;

    @Autowired
    private Mailer emailService;

    @Autowired
    JwtUtils jwtUtils;

    private static final Set<String> RESERVED_USERNAMES = Set.of(
            "admin", "support", "root", "system", "moderator", "staff", "gay", "homosexual", "marico", "faggot"
    );

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        String identifier = loginRequest.getIdentifier();
        String password = loginRequest.getPassword();

        // Try finding the user by email or username
        User user = userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByUsername(identifier))
                .orElse(null);

        if (user == null) {
            return ResponseEntity
                    .status(401)
                    .body(new MessageResponse("Invalid credentials"));
        }

        try {
            // Authenticate using email (Spring Security still expects email/username registered in UserDetailsService)
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getEmail(), password)
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

            String accessToken = jwtUtils.generateJwtToken(userDetails);
            String refreshToken = jwtUtils.generateRefreshToken(userDetails);

            return ResponseEntity.ok(new JwtResponse(
                    accessToken,
                    refreshToken,
                    userDetails.getId(),
                    userDetails.getFirstName(),
                    userDetails.getLastName(),
                    userDetails.getEmail(),
                    userDetails.getPhone(),
                    userDetails.getUsername(),
                    user.getPassword() != null && !user.getPassword().isBlank(),
                    user.getUsername() != null &&
                            !user.getUsername().isBlank() &&
                            !user.getUsername().startsWith("user_")
            ));

        } catch (BadCredentialsException ex) {
            return ResponseEntity
                    .status(401)
                    .body(new MessageResponse("Invalid credentials"));
        }
    }

    @PostMapping("/set-username")
    public ResponseEntity<?> setUsername(@RequestBody Map<String, String> body, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Unauthorized"));
        }

        String newUsername = body.get("username");
        if (newUsername == null || newUsername.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Username is required"));
        }

        String normalizedUsername = newUsername.trim().toLowerCase();

        if (RESERVED_USERNAMES.contains(normalizedUsername)) {
            return ResponseEntity.badRequest().body(new MessageResponse("Username is not allowed"));
        }

        if (userRepository.existsByUsername(newUsername)) {
            return ResponseEntity.badRequest().body(new MessageResponse("Username is already taken"));
        }

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userRepository.findById(userDetails.getId()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponse("User not found"));
        }

        user.setUsername(newUsername.trim());
        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("✅ Username set successfully"));
    }

    @PostMapping("/set-password")
    public ResponseEntity<?> setPassword(@RequestBody SetPasswordRequest request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Unauthorized"));
        }

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userRepository.findById(userDetails.getId()).orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponse("User not found"));
        }

        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            return ResponseEntity.badRequest().body(new MessageResponse("You already have a password set"));
        }

        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            return ResponseEntity.badRequest().body(new MessageResponse("Password must be at least 6 characters"));
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("✅ Password has been set successfully"));
    }

    @PostMapping("/apple-login")
    public ResponseEntity<?> loginWithApple(@RequestBody ExternalAuthRequest request) {
        if (!"apple".equalsIgnoreCase(request.getProvider())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Unsupported provider."));
        }

        var claims = appleVerifierService.verify(request.getIdToken());

        if (claims == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Invalid Apple token."));
        }

        String externalId = (String) claims.get("sub");
        String email = (String) claims.get("email");

        User user = userRepository.findByExternalProviderAndExternalId("apple", externalId).orElse(null);

        if (user == null) {
            // Generate a temporary placeholder username
            String placeholderUsername = "user_" + UUID.randomUUID().toString().substring(0, 8);

            // Apple may not always provide first/last name on subsequent logins
            String firstName = (String) claims.get("given_name");
            String lastName = (String) claims.get("family_name");

            user = User.builder()
                    .firstName(firstName != null ? firstName : "")
                    .lastName(lastName != null ? lastName : "")
                    .email(email)
                    .username(placeholderUsername) // ✅ Add placeholder
                    .emailVerified(true)
                    .phoneVerified(false)
                    .password("") // not used
                    .externalProvider("apple")
                    .externalId(externalId)
                    .role(Role.USER)
                    .avatarUrl("")
                    .bio("")
                    .hideProfile(false)
                    .hideAttendance(false)
                    .build();

            userRepository.save(user);
        }

        UserDetailsImpl userDetails = UserDetailsImpl.build(user);
        String accessToken = jwtUtils.generateJwtToken(userDetails);
        String refreshToken = jwtUtils.generateRefreshToken(userDetails);

        return ResponseEntity.ok(new JwtResponse(
                accessToken,
                refreshToken,
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                user.getUsername(),
                user.getPassword() != null && !user.getPassword().isBlank(),
                // 10th = hasUsername
                user.getUsername() != null &&
                        !user.getUsername().isBlank() &&
                        !user.getUsername().startsWith("user_")
        ));
    }

    @PostMapping("/google-login")
    public ResponseEntity<?> loginWithGoogle(@RequestBody ExternalAuthRequest request) {
        if (!"google".equalsIgnoreCase(request.getProvider())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Unsupported provider."));
        }

        var payload = googleVerifierService.verify(request.getIdToken());

        if (payload == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Invalid Google token."));
        }

        String externalId = payload.getSubject(); // Google's unique user ID
        String email = payload.getEmail();
        String firstName = (String) payload.get("given_name");
        String lastName = (String) payload.get("family_name");

        User user = userRepository.findByExternalProviderAndExternalId("google", externalId).orElse(null);

        if (user == null) {
            // Generate a temporary placeholder username
            String placeholderUsername = "user_" + UUID.randomUUID().toString().substring(0, 8);

            // Register new Google user
            user = User.builder()
                    .firstName(firstName)
                    .lastName(lastName)
                    .email(email)
                    .username(placeholderUsername) // ✅ Add placeholder to avoid DB null error
                    .emailVerified(true)
                    .phoneVerified(false)
                    .password("") // not used
                    .externalProvider("google")
                    .externalId(externalId)
                    .role(Role.USER)
                    .avatarUrl("")
                    .bio("")
                    .hideProfile(false)
                    .hideAttendance(false)
                    .build();

            userRepository.save(user);
        }

        UserDetailsImpl userDetails = UserDetailsImpl.build(user);
        String accessToken = jwtUtils.generateJwtToken(userDetails);
        String refreshToken = jwtUtils.generateRefreshToken(userDetails);

        return ResponseEntity.ok(new JwtResponse(
                accessToken,
                refreshToken,
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                user.getUsername(),
                user.getPassword() != null && !user.getPassword().isBlank(),
                // 10th = hasUsername
                user.getUsername() != null &&
                        !user.getUsername().isBlank() &&
                        !user.getUsername().startsWith("user_")
        ));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyCode(@RequestBody VerifyRequest request) {
        String email = request.getEmail();
        String code = request.getCode();

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(new MessageResponse("User not found."));
        }

        if (user.getVerificationCode() == null) {
            return ResponseEntity.badRequest().body(new MessageResponse("No code available. Please request a new one."));
        }

        System.out.println("User's saved code: " + user.getVerificationCode());
        System.out.println("Code received from client: " + code);

        if (!code.trim().equalsIgnoreCase(user.getVerificationCode().trim())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Invalid verification code."));
        }

        if (user.getCodeGeneratedAt() == null ||
                user.getCodeGeneratedAt().isBefore(LocalDateTime.now().minusMinutes(10))) {
            return ResponseEntity.badRequest().body(new MessageResponse("Verification code has expired."));
        }

        user.setEmailVerified(true);
        user.setVerificationCode(null);
        user.setCodeGeneratedAt(null);
        user.setLastResendAt(null);
        userRepository.save(user);

        UserDetailsImpl userDetails =  UserDetailsImpl.build(user);
        String accessToken = jwtUtils.generateJwtToken(userDetails);
        String refreshToken = jwtUtils.generateRefreshToken(userDetails);

        return ResponseEntity.ok(Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken
        ));
    }


    @PostMapping("/resend")
    public ResponseEntity<?> resendVerification(@RequestBody ResendRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);

        if (user == null) {
            return ResponseEntity.badRequest().body(new MessageResponse("No user found with this email."));
        }

        if (user.isEmailVerified()) {
            return ResponseEntity.badRequest().body(new MessageResponse("This email is already verified."));
        }

        if (user.getLastResendAt() != null &&
                user.getLastResendAt().isAfter(LocalDateTime.now().minusSeconds(10))) {
            return ResponseEntity.badRequest().body(new MessageResponse("Please wait before requesting another code."));
        }

        String newCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        user.setVerificationCode(newCode);
        user.setCodeGeneratedAt(LocalDateTime.now());
        user.setLastResendAt(LocalDateTime.now());

        // ✅ This must always be called after setting the new code
        userRepository.save(user);

        try {
            if (request.isUseSms()) {
                smsService.sendVerificationCode(user.getPhone(), newCode);
            } else {
                emailService.sendVerificationEmail(
                        user.getEmail(),
                        user.getFirstName() + " " + user.getLastName(),
                        newCode
                );
            }
        } catch (Exception e) {
            return ResponseEntity
                    .internalServerError()
                    .body(new MessageResponse("Failed to send verification: " + e.getMessage()));
        }

        return ResponseEntity.ok(new MessageResponse("Verification code resent successfully."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);

        if (user == null) {
            return ResponseEntity.badRequest().body(new MessageResponse("No user found with this email."));
        }

        String resetCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        user.setVerificationCode(resetCode);
        user.setCodeGeneratedAt(LocalDateTime.now());
        user.setLastResendAt(LocalDateTime.now());
        userRepository.save(user);

        try {
            emailService.sendPasswordResetEmail(
                    user.getEmail(),
                    user.getFirstName() + " " + user.getLastName(),
                    resetCode
            );
        } catch (Exception e) {
            return ResponseEntity
                    .internalServerError()
                    .body(new MessageResponse("Failed to send reset email: " + e.getMessage()));
        }

        return ResponseEntity.ok(new MessageResponse("Password reset code sent to your email."));
    }


    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        String email = request.getEmail();
        String code = request.getCode();
        String newPassword = request.getNewPassword();

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(new MessageResponse("User not found."));
        }

        if (user.getVerificationCode() == null || !user.getVerificationCode().trim().equalsIgnoreCase(code.trim())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Invalid verification code."));
        }

        if (user.getCodeGeneratedAt() == null ||
                user.getCodeGeneratedAt().isBefore(LocalDateTime.now().minusMinutes(10))) {
            return ResponseEntity.badRequest().body(new MessageResponse("Verification code has expired."));
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setVerificationCode(null);
        user.setCodeGeneratedAt(null);
        user.setLastResendAt(null);
        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("Password has been reset successfully."));
    }

    @PostMapping("/verify-reset-code")
    public ResponseEntity<?> verifyResetCode(@RequestBody VerifyRequest request) {
        String email = request.getEmail();
        String code = request.getCode();

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(new MessageResponse("User not found."));
        }

        if (user.getVerificationCode() == null || !user.getVerificationCode().trim().equalsIgnoreCase(code.trim())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Invalid verification code."));
        }

        if (user.getCodeGeneratedAt() == null ||
                user.getCodeGeneratedAt().isBefore(LocalDateTime.now().minusMinutes(10))) {
            return ResponseEntity.badRequest().body(new MessageResponse("Verification code has expired."));
        }

        return ResponseEntity.ok(new MessageResponse("Code verified. You may now reset your password."));
    }


    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }

        return ResponseEntity.ok("Token is valid");
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Email is already in use!"));
        }

        if (signUpRequest.getPhone() != null && userRepository.existsByPhone(signUpRequest.getPhone())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Phone is already in use!"));
        }

        if (RESERVED_USERNAMES.contains(signUpRequest.getUsername().toLowerCase())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Username is not allowed"));
        }

        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Username is already taken!"));
        }

        String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        User user = User.builder()
                .firstName(signUpRequest.getFirstName())
                .lastName(signUpRequest.getLastName())
                .email(signUpRequest.getEmail())
                .phone(signUpRequest.getPhone())
                .username(signUpRequest.getUsername())
                .password(passwordEncoder.encode(signUpRequest.getPassword()))
                .emailVerified(false)
                .phoneVerified(false)
                .verificationCode(code)
                .externalProvider(null)
                .externalId(null)
                .codeGeneratedAt(LocalDateTime.now())
                .lastResendAt(LocalDateTime.now())
                .role(Role.USER)
                .avatarUrl("")
                .bio("")
                .hideProfile(false)
                .hideAttendance(false)
                .build();

        userRepository.save(user);

        try {
            if (signUpRequest.isUseSms()) {
                smsService.sendVerificationCode(user.getPhone(), code);
            } else {
                emailService.sendVerificationEmail(
                        user.getEmail(),
                        user.getFirstName() + " " + user.getLastName(),
                        code
                );
            }
        } catch (Exception e) {
            return ResponseEntity
                    .internalServerError()
                    .body(new MessageResponse("Failed to send verification: " + e.getMessage()));
        }

        return ResponseEntity.ok(new MessageResponse("User registered. Please check your email for the verification code."));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");

        if (refreshToken == null || refreshToken.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Refresh token is missing"));
        }

        if (!jwtUtils.validateJwtToken(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid refresh token"));
        }

        String email = jwtUtils.getUserNameFromJwtToken(refreshToken);
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "User not found"));
        }

        UserDetailsImpl userDetails = UserDetailsImpl.build(userOpt.get());

        String newAccessToken = jwtUtils.generateJwtToken(userDetails);
        String newRefreshToken = jwtUtils.generateRefreshToken(userDetails);

        return ResponseEntity.ok(Map.of(
                "accessToken", newAccessToken,
                "refreshToken", newRefreshToken
        ));
    }

    // === DTOs ===

    @Getter @Setter
    static class LoginRequest {
        private String identifier; // Accepts email OR username
        private String password;
    }

    @Getter @Setter
    static class ResendRequest {
        private String email;
        private boolean useSms;
    }

    @Getter @Setter
    static class VerifyRequest {
        private String email;
        private String code;
    }

    @Getter @Setter
    static class SignupRequest {
        private String firstName;
        private String lastName;
        private String email;
        private String phone;
        private String password;
        private String username; // ✅ Add this line
        private boolean useSms;
    }

    @Getter @AllArgsConstructor
    static class JwtResponse {
        private String token;
        private String refreshToken;
        private Long id;
        private String firstName;
        private String lastName;
        private String email;
        private String phone;
        private String username; // ✅ ADD THIS
        private boolean hasPassword;
        private boolean hasUsername; // ✅ NEW FIELD// ✅ NEW FIELD
    }

    @Getter @Setter
    public static class ExternalAuthRequest {
        private String idToken;
        private String provider;
    }

    @Getter @Setter
    static class ResetPasswordRequest {
        private String email;
        private String code;
        private String newPassword;
    }

    @Getter @Setter
    static class ForgotPasswordRequest {
        private String email;
    }

    @Getter @Setter
    static class SetPasswordRequest {
        private String newPassword;
    }

    @Getter @AllArgsConstructor
    static class MessageResponse {
        private String message;
    }
}
