package com.flashsale.auth.service;

import com.flashsale.auth.document.User;
import com.flashsale.auth.dto.UpdateProfileRequest;
import com.flashsale.auth.dto.UserResponse;
import com.flashsale.auth.exception.UserNotFoundException;
import com.flashsale.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;

/** Reads and edits the authenticated user's own profile (PRD 16: /api/v1/users/me). */
@Service
@RequiredArgsConstructor
public class UserProfileService {
    private final UserRepository userRepository;
    private final Clock clock;

    public UserResponse getProfile(String email) {
        return UserResponse.from(findUser(email));
    }

    public UserResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = findUser(email);
        user.setFullName(request.fullName().trim());
        user.setUpdatedAt(clock.instant());
        return UserResponse.from(userRepository.save(user));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(UserNotFoundException::new);
    }
}
