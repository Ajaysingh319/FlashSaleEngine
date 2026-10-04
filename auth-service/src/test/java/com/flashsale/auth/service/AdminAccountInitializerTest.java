package com.flashsale.auth.service;

import com.flashsale.auth.document.User;
import com.flashsale.auth.repository.UserRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class AdminAccountInitializerTest {

    private static final String EMAIL = "admin@flashsale.test";
    private static final String PASSWORD = "S3cret-Admin-Pass";

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    private void runWith(String email, String password) {
        new AdminAccountInitializer(userRepository, passwordEncoder, validator, email, password)
                .run(new DefaultApplicationArguments());
    }

    private static User existingUser(String role) {
        User user = new User("user-1", EMAIL, "$2a$10$existingHash", role, Instant.EPOCH, Instant.EPOCH);
        return user;
    }

    @ParameterizedTest(name = "email=[{0}] password=[{1}]")
    @CsvSource(value = {"NULL,NULL", "'',''", "'  ','  '"}, nullValues = "NULL")
    void unsetVariablesCreateNothing(String email, String password) {
        assertDoesNotThrow(() -> runWith(email, password));

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void emailWithoutPasswordFailsStartup() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> runWith(EMAIL, ""));

        assertTrue(exception.getMessage().contains("ADMIN_PASSWORD is missing"));
        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void passwordWithoutEmailFailsStartupWithoutRevealingPassword() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> runWith("", PASSWORD));

        assertTrue(exception.getMessage().contains("ADMIN_EMAIL is missing"));
        assertFalse(exception.getMessage().contains(PASSWORD));
        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void valuesFailingRegistrationRulesFailStartup() {
        IllegalStateException badEmail = assertThrows(IllegalStateException.class, () -> runWith("not-an-email", PASSWORD));
        assertTrue(badEmail.getMessage().contains("ADMIN_EMAIL"));

        IllegalStateException shortPassword = assertThrows(IllegalStateException.class, () -> runWith(EMAIL, "short1"));
        assertTrue(shortPassword.getMessage().contains("ADMIN_PASSWORD"));
        assertFalse(shortPassword.getMessage().contains("short1"));

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void firstStartCreatesAdminWithEncodedPassword(CapturedOutput output) {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(PASSWORD)).thenReturn("$2a$10$encodedHash");

        runWith("  " + EMAIL + " ", PASSWORD);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(EMAIL, saved.getValue().getEmail());
        assertEquals("ADMIN", saved.getValue().getRole());
        assertEquals("$2a$10$encodedHash", saved.getValue().getPassword());
        assertNotNull(saved.getValue().getCreatedAt());
        assertNull(saved.getValue().getId());
        assertFalse(output.getAll().contains(PASSWORD), "password must never be logged");
        assertFalse(output.getAll().contains("$2a$10$encodedHash"), "password hash must never be logged");
    }

    @Test
    void restartWithExistingAdminChangesNothing() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existingUser("ADMIN")));

        runWith(EMAIL, "A-Different-Password-123");

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void existingCustomerWithAdminEmailIsNotPromotedOrModified(CapturedOutput output) {
        User customer = existingUser("CUSTOMER");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(customer));

        runWith(EMAIL, PASSWORD);

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
        assertEquals("CUSTOMER", customer.getRole());
        assertEquals("$2a$10$existingHash", customer.getPassword());
        assertTrue(output.getAll().contains("not promoted"));
        assertFalse(output.getAll().contains(PASSWORD));
    }
}
