package com.delivery.livestream_service.service;

import com.delivery.livestream_service.dto.event.LivestreamEndedEvent;
import com.delivery.livestream_service.dto.event.LivestreamStartedEvent;
import com.delivery.livestream_service.dto.request.CreateLivestreamRequest;
import com.delivery.livestream_service.dto.response.JoinLivestreamResponse;
import com.delivery.livestream_service.dto.response.LivestreamResponse;
import com.delivery.livestream_service.dto.response.StartLivestreamResponse;
import com.delivery.livestream_service.entity.Livestream;
import com.delivery.livestream_service.enums.LivestreamStatus;
import com.delivery.livestream_service.enums.TokenRole;
import com.delivery.livestream_service.exception.LivestreamNotFoundException;
import com.delivery.livestream_service.mapper.LivestreamMapper;
import com.delivery.livestream_service.repository.LivestreamRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.delivery.livestream.api.*;
import com.delivery.livestream.application.LifecycleUseCases;
import com.delivery.livestream_service.dto.response.TokenResponse;

@Slf4j
@Service
public class LivestreamService {
    private final LifecycleUseCases<Livestream, LivestreamResponse, TokenResponse, StartLivestreamResponse, JoinLivestreamResponse, CreateLivestreamRequest> useCases;
    public LivestreamService(LivestreamRepository repository, LivestreamEventPublisher events,
                             LivestreamMapper mapper, StreamTokenService tokens) {
        useCases = new LifecycleUseCases<>(new LifecyclePorts<>() {
            public Livestream find(UUID id) {
                return repository.findById(id).orElseThrow(() -> new LivestreamNotFoundException("Không tìm thấy livestream với ID: " + id));
            }
            public RoomSnapshot snapshot(Livestream room) { return LivestreamCompatibility.snapshot(room); }
            public Livestream create(CreateLivestreamRequest request, Long seller) {
                Livestream room = new Livestream();
                room.setSellerId(seller); room.setRestaurantId(request.getRestaurantId());
                room.setTitle(request.getTitle()); room.setDescription(request.getDescription());
                room.setStreamProvider(request.getStreamProvider()); room.setStatus(LivestreamStatus.CREATED);
                return repository.save(room);
            }
            public Livestream start(Livestream room) {
                room.setStatus(LivestreamStatus.LIVE); room.setStartedAt(LocalDateTime.now());
                return repository.save(room);
            }
            public Livestream end(Livestream room) {
                room.setStatus(LivestreamStatus.ENDED); room.setEndedAt(LocalDateTime.now());
                return repository.save(room);
            }
            public Livestream count(Livestream room, long count) { room.setViewCount(count); return repository.save(room); }
            public TokenResponse token(UUID id, Long user, String role, int ttl) {
                return tokens.generateToken(id, user, TokenRole.valueOf(role), ttl);
            }
            public void started(Livestream room) {
                events.publishLivestreamStarted(new LivestreamStartedEvent(room.getId(), room.getSellerId(), room.getRestaurantId(), room.getRoomId(), room.getStartedAt()));
            }
            public void ended(Livestream room) {
                long minutes = Duration.between(room.getStartedAt(), room.getEndedAt()).toMinutes();
                events.publishLivestreamEnded(new LivestreamEndedEvent(room.getId(), room.getSellerId(), room.getRestaurantId(), room.getStartedAt(), room.getEndedAt(), minutes));
            }
            public LivestreamResponse response(Livestream room) { return mapper.toResponse(room); }
            public StartLivestreamResponse startResponse(Livestream livestream, TokenResponse tokenResponse, int uid) {
                StartLivestreamResponse response = new StartLivestreamResponse();
                response.setLivestreamId(livestream.getId());
                response.setChannelName(livestream.getChannelName());
                response.setStatus(livestream.getStatus());
                response.setToken(tokenResponse.getToken());
                response.setUid(uid);
                response.setRole("HOST");
                response.setTokenExpiresAt(tokenResponse.getExpiresAt());
                response.setTitle(livestream.getTitle());
                response.setRestaurantId(livestream.getRestaurantId());
                response.setStartedAt(livestream.getStartedAt());
                
                return response;

            }
            public JoinLivestreamResponse joinResponse(Livestream livestream, TokenResponse tokenResponse, int uid) {
                JoinLivestreamResponse response = new JoinLivestreamResponse();
                response.setLivestreamId(livestream.getId());
                response.setChannelName(livestream.getChannelName());
                response.setTitle(livestream.getTitle());
                response.setRestaurantId(livestream.getRestaurantId());
                response.setToken(tokenResponse.getToken());
                response.setUid(uid);
                response.setTokenExpiresAt(tokenResponse.getExpiresAt());
                response.setSellerId(livestream.getSellerId());
                response.setStartedAt(livestream.getStartedAt());
                // Số viewer đồng thời (concurrent) cần heartbeat/leave hoặc Agora RTM API —
                // chưa triển khai. Tổng lượt xem tích luỹ có ở LivestreamResponse.viewCount.

                return response;

            }
            public List<Livestream> list(String filter, Long value, int limit) {
                var page = PageRequest.of(0, limit);
                return switch (filter) {
                    case "active" -> repository.findByStatusOrderByCreatedAtDesc(LivestreamStatus.LIVE, page);
                    case "seller" -> repository.findBySellerIdOrderByCreatedAtDesc(value, page);
                    case "restaurant" -> repository.findByRestaurantIdOrderByCreatedAtDesc(value, page);
                    default -> throw new IllegalArgumentException(filter);
                };
            }
        });
    }
    @Transactional
    public LivestreamResponse createLivestream(CreateLivestreamRequest request, Long sellerId, String role) {
        return LivestreamCompatibility.call(() -> useCases.create(request, sellerId, LivestreamCompatibility.name(request.getStreamProvider())));
    }
    @Transactional
    public StartLivestreamResponse startLivestream(UUID id, Long sellerId, String role) {
        return LivestreamCompatibility.call(() -> useCases.start(id, sellerId, role));
    }
    @Transactional
    public LivestreamResponse endLivestream(UUID id, Long sellerId, String role) {
        return LivestreamCompatibility.call(() -> useCases.end(id, sellerId, role));
    }
    @Transactional(readOnly = true)
    public List<LivestreamResponse> getActiveLivestreams() { return useCases.list("active", null); }
    @Transactional(readOnly = true)
    public LivestreamResponse getLivestreamById(UUID id) { return useCases.inspect(id); }
    public JoinLivestreamResponse joinLivestream(UUID id, Long viewerId) { return joinLivestream(id, viewerId, true); }
    @Transactional
    public JoinLivestreamResponse joinLivestream(UUID id, Long viewerId, boolean countView) {
        return LivestreamCompatibility.call(() -> useCases.join(id, viewerId, countView));
    }
    @Transactional(readOnly = true)
    public List<LivestreamResponse> getLivestreamsBySeller(Long sellerId) { return useCases.list("seller", sellerId); }
    @Transactional(readOnly = true)
    public List<LivestreamResponse> getLivestreamsByRestaurant(Long restaurantId) { return useCases.list("restaurant", restaurantId); }
}
