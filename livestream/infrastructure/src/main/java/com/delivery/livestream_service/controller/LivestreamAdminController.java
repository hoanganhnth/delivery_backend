package com.delivery.livestream_service.controller;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.livestream_service.dto.response.LivestreamResponse;
import com.delivery.livestream_service.mapper.LivestreamMapper;
import com.delivery.livestream_service.payload.BaseResponse;
import com.delivery.livestream_service.repository.LivestreamRepository;
import com.delivery.livestream_service.service.LivestreamModerationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/livestreams/admin")
@ConditionalOnProperty(name = "app.livestream.api-enabled", havingValue = "true")
public class LivestreamAdminController {
    private final LivestreamRepository repository;
    private final LivestreamMapper mapper;

    public LivestreamAdminController(LivestreamRepository repository, LivestreamMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public record RoomPage(List<LivestreamResponse> content, int page, int size, long totalElements, int totalPages) {}

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<BaseResponse<RoomPage>> list(
            @AuthenticationPrincipal AuthenticatedActor actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        LivestreamModerationService.requireAdmin(actor);
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Invalid livestream page or size");
        }
        var rows = repository.findAll(PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        var response = new RoomPage(rows.getContent().stream().map(mapper::toResponse).toList(),
                page, size, rows.getTotalElements(), rows.getTotalPages());
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Danh sách livestream"));
    }
}
