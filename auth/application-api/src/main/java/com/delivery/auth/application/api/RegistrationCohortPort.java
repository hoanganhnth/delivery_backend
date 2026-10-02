package com.delivery.auth.application.api;

public interface RegistrationCohortPort {
    int bucket(String canonicalEmail);
}
