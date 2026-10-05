package com.flashsale.auth.controller;

import com.flashsale.auth.dto.UpdateProfileRequest;
import com.flashsale.auth.dto.UserResponse;
import com.flashsale.auth.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The authenticated user's own profile; the user is always taken from the verified access token. */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {
    private final UserProfileService userProfileService;

    @GetMapping
    public UserResponse getProfile(@AuthenticationPrincipal UserDetails user) {
        return userProfileService.getProfile(user.getUsername());
    }

    @PutMapping
    public UserResponse updateProfile(@AuthenticationPrincipal UserDetails user,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        return userProfileService.updateProfile(user.getUsername(), request);
    }
}
