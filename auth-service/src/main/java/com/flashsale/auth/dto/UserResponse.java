package com.flashsale.auth.dto;

import com.flashsale.auth.document.User;

import java.time.Instant;

/** Current-user profile (PRD 4.1). Never carries the password hash. */
public record UserResponse(String id, String email, String fullName, String role, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.getCreatedAt());
    }
}
