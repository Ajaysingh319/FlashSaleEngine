package com.flashsale.auth.service;

import com.flashsale.auth.document.User;
import com.flashsale.auth.dto.RegisterRequest;
import com.flashsale.auth.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Creates the initial ADMIN account from ADMIN_EMAIL and ADMIN_PASSWORD (registration always assigns CUSTOMER).
 * Both unset: nothing happens. Only one set, or values failing the registration rules: startup fails.
 * An existing account with that email is never modified: an ADMIN is left as is, and a non-ADMIN is not promoted,
 * since its password was chosen by whoever registered it. The password is never logged or included in errors.
 */
@Component
@Slf4j
public class AdminAccountInitializer implements ApplicationRunner {
    static final String ADMIN_ROLE = "ADMIN";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;
    private final String email;
    private final String password;

    public AdminAccountInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder, Validator validator,
                                   @Value("${app.admin.email:}") String email,
                                   @Value("${app.admin.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
        this.email = email == null ? "" : email.trim();
        this.password = password == null ? "" : password;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean emailSet = !email.isEmpty();
        boolean passwordSet = !password.isBlank();
        if (!emailSet && !passwordSet) {
            log.info("Initial admin account not configured (ADMIN_EMAIL and ADMIN_PASSWORD are not set)");
            return;
        }
        if (emailSet != passwordSet) {
            throw new IllegalStateException("ADMIN_EMAIL and ADMIN_PASSWORD must be set together; "
                    + (emailSet ? "ADMIN_PASSWORD" : "ADMIN_EMAIL") + " is missing");
        }
        validateAgainstRegistrationRules();

        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            if (ADMIN_ROLE.equals(existing.get().getRole())) {
                log.info("Initial admin account {} already exists; leaving it unchanged", email);
            } else {
                log.warn("Account {} already exists with role {}; it is not promoted to ADMIN or modified",
                        email, existing.get().getRole());
            }
            return;
        }

        Instant now = Instant.now();
        User admin = new User();
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole(ADMIN_ROLE);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        userRepository.save(admin);
        log.info("Created initial admin account {}", email);
    }

    /** Same constraints as self-registration (valid email, password length); reports field names only. */
    private void validateAgainstRegistrationRules() {
        RegisterRequest candidate = new RegisterRequest();
        candidate.setEmail(email);
        candidate.setPassword(password);
        String invalidFields = validator.validate(candidate).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(path -> "password".equals(path.toString()) ? "ADMIN_PASSWORD" : "ADMIN_EMAIL")
                .distinct().sorted()
                .collect(Collectors.joining(", "));
        if (!invalidFields.isEmpty()) {
            throw new IllegalStateException("Invalid initial admin configuration: " + invalidFields
                    + " does not meet the registration rules (valid email; password of at least 8 characters)");
        }
    }
}
