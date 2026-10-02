package com.delivery.user.application.api;

public record ApplyUserIdentityStatusCommand(Long principalId, String status,
        long lifecycleVersion, Long changedByPrincipalId) {}
