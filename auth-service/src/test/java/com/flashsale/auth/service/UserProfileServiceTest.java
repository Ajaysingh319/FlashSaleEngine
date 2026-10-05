package com.flashsale.auth.service;

import com.flashsale.auth.document.User;
import com.flashsale.auth.dto.UpdateProfileRequest;
import com.flashsale.auth.dto.UserResponse;
import com.flashsale.auth.exception.UserNotFoundException;
import com.flashsale.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserProfileServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");
    private static final String EMAIL = "fan@example.com";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserProfileService service =
            new UserProfileService(userRepository, Clock.fixed(NOW, ZoneOffset.UTC));

    private User existingUser() {
        User user = new User("user-1", EMAIL, null, "$2a$10$hash", "CUSTOMER", Instant.EPOCH, Instant.EPOCH);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void profileShowsAccountDetailsButNeverThePassword() {
        existingUser();

        UserResponse profile = service.getProfile(EMAIL);

        assertEquals(new UserResponse("user-1", EMAIL, null, "CUSTOMER", Instant.EPOCH), profile);
    }

    @Test
    void updateChangesOnlyTheFullName() {
        User user = existingUser();
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse profile = service.updateProfile(EMAIL, new UpdateProfileRequest("  Asha Rao "));

        assertEquals("Asha Rao", profile.fullName());
        assertEquals(EMAIL, user.getEmail());
        assertEquals("CUSTOMER", user.getRole());
        assertEquals("$2a$10$hash", user.getPassword());
        assertEquals(NOW, user.getUpdatedAt());
    }

    @Test
    void missingAccountIsNotFound() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.getProfile(EMAIL));
        verify(userRepository, never()).save(any());
    }
}
