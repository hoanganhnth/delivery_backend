package com.delivery.user.domain.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class UserModelTest {

    @Test
    void userCarriesIdentityProfileAndBlockStateWithoutFrameworkTypes() {
        LocalDate dob = LocalDate.of(1990, 1, 2);
        LocalDateTime blockedAt = LocalDateTime.of(2026, 9, 27, 10, 15);
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 1, 8, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 9, 27, 10, 15);
        User user = new User(
                7L, 42L, 42L, "ACTIVE", 3L, "user@example.com", "USER",
                "Delivery User", "+84123456789", dob, "avatar.png", "District 1",
                false, true, blockedAt, 99L, "fraud review", createdAt, updatedAt);

        assertAll(
                () -> assertEquals(7L, user.id()),
                () -> assertEquals(42L, user.authId()),
                () -> assertEquals(42L, user.principalId()),
                () -> assertEquals("ACTIVE", user.identityStatus()),
                () -> assertEquals(3L, user.identityStatusVersion()),
                () -> assertEquals("user@example.com", user.email()),
                () -> assertEquals("USER", user.role()),
                () -> assertEquals("Delivery User", user.fullName()),
                () -> assertEquals("+84123456789", user.phone()),
                () -> assertEquals(dob, user.dob()),
                () -> assertEquals("avatar.png", user.avatarUrl()),
                () -> assertEquals("District 1", user.address()),
                () -> assertEquals(false, user.isActive()),
                () -> assertEquals(true, user.isBlocked()),
                () -> assertEquals(blockedAt, user.blockedAt()),
                () -> assertEquals(99L, user.blockedBy()),
                () -> assertEquals("fraud review", user.blockReason()),
                () -> assertEquals(createdAt, user.createdAt()),
                () -> assertEquals(updatedAt, user.updatedAt()));
        assertEquals(user, user);
        assertEquals(user.hashCode(), user.hashCode());
        assertEquals(user.toString(), user.toString());
    }

    @Test
    void userAddressCarriesOwnershipAndDeliveryCoordinates() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 1, 8, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 9, 2, 8, 0);
        UserAddress address = new UserAddress(
                5L, 7L, "Home", "Delivery User", "+84123456789", "12 Main Street",
                "Ward 1", "District 1", "Ho Chi Minh City", "700000", 10.7769, 106.7009,
                true, createdAt, updatedAt);

        assertAll(
                () -> assertEquals(5L, address.id()),
                () -> assertEquals(7L, address.userId()),
                () -> assertEquals("Home", address.label()),
                () -> assertEquals("Delivery User", address.recipientName()),
                () -> assertEquals("+84123456789", address.phoneNumber()),
                () -> assertEquals("12 Main Street", address.addressLine()),
                () -> assertEquals("Ward 1", address.ward()),
                () -> assertEquals("District 1", address.district()),
                () -> assertEquals("Ho Chi Minh City", address.city()),
                () -> assertEquals("700000", address.postalCode()),
                () -> assertEquals(10.7769, address.latitude()),
                () -> assertEquals(106.7009, address.longitude()),
                () -> assertEquals(true, address.isDefault()),
                () -> assertEquals(createdAt, address.createdAt()),
                () -> assertEquals(updatedAt, address.updatedAt()));
        assertEquals(address, address);
        assertEquals(address.hashCode(), address.hashCode());
        assertEquals(address.toString(), address.toString());
    }
}
