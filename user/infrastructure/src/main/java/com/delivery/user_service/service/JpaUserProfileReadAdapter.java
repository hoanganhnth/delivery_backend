package com.delivery.user_service.service;

import com.delivery.user.application.api.UserProfileReadPort;
import com.delivery.user.application.api.UserProfileResult;
import com.delivery.user.application.api.UserStatisticsResult;
import com.delivery.user_service.entity.User;
import com.delivery.user_service.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** JPA adapter for profile reads and administrative summaries. */
@Component
@RequiredArgsConstructor
public class JpaUserProfileReadAdapter implements UserProfileReadPort {

    private final UserRepository users;

    @Override
    public UserProfileResult byAuthId(Long authId) {
        User user = users.findByAuthId(authId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found by auth ID"));
        return JpaUserProfileAdapter.toResult(user);
    }

    @Override
    public UserProfileResult byPrincipalId(Long principalId) {
        User user = users.findByPrincipalId(principalId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found by principal ID"));
        return JpaUserProfileAdapter.toResult(user);
    }

    @Override
    public UserStatisticsResult statistics() {
        return new UserStatisticsResult(
                users.count(),
                users.countByRole("USER"),
                users.countByRole("ADMIN"),
                users.countByRole("SHIPPER"),
                users.countByRole("SHOP_OWNER"),
                users.countByIsActive(true),
                users.countByIsBlocked(true));
    }

    @Override
    public List<UserProfileResult> all() {
        return users.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 100)).stream()
                .map(JpaUserProfileAdapter::toResult)
                .toList();
    }
}
