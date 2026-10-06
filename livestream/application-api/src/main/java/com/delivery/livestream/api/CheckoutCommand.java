package com.delivery.livestream.api;
import java.util.List;
import java.util.UUID;
public record CheckoutCommand(UUID room, Long restaurant, List<Long> products) { }
