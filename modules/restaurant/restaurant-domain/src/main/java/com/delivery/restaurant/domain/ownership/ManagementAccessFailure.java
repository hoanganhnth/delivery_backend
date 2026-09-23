package com.delivery.restaurant.domain.ownership;

public enum ManagementAccessFailure {
    PRINCIPAL_REQUIRED,
    ACTOR_NOT_ALLOWED,
    ACTOR_DOES_NOT_OWN_RESTAURANT
}
