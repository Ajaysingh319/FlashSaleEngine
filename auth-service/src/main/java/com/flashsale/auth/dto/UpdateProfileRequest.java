package com.flashsale.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Editable profile fields. Email and role are deliberately not editable through the profile. */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 100, message = "must be at most 100 characters") String fullName) {
}
