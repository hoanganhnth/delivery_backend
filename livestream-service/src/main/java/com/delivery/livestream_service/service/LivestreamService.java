package com.delivery.livestream_service.service;

import com.delivery.livestream_service.dto.event.LivestreamEndedEvent;
import com.delivery.livestream_service.dto.event.LivestreamStartedEvent;
import com.delivery.livestream_service.dto.request.CreateLivestreamRequest;
import com.delivery.livestream_service.dto.response.JoinLivestreamResponse;
import com.delivery.livestream_service.dto.response.LivestreamProductResponse;
import com.delivery.livestream_service.dto.response.LivestreamResponse;
import com.delivery.livestream_service.dto.response.StartLivestreamResponse;
import com.delivery.livestream_service.entity.Livestream;
import com.delivery.livestream_service.enums.LivestreamStatus;
import com.delivery.livestream_service.enums.TokenRole;
import com.delivery.livestream_service.exception.InvalidLivestreamStatusException;
import com.delivery.livestream_service.exception.LivestreamNotFoundException;
import com.delivery.livestream_service.exception.UnauthorizedLivestreamAccessException;
import com.delivery.livestream_service.mapper.LivestreamMapper;
import com.delivery.livestream_service.repository.LivestreamRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LivestreamService {

    private final LivestreamRepository livestreamRepository;
    private final LivestreamEventPublisher eventPublisher;
    private final LivestreamMapper mapper;
    private final StreamTokenService streamTokenService;

    public LivestreamService(LivestreamRepository livestreamRepository,
                            LivestreamEventPublisher eventPublisher,
                            LivestreamMapper mapper,
                            StreamTokenService streamTokenService) {
        this.livestreamRepository = livestreamRepository;
        this.eventPublisher = eventPublisher;
        this.mapper = mapper;
        this.streamTokenService = streamTokenService;
    }

    @Transactional
    public LivestreamResponse createLivestream(CreateLivestreamRequest request, Long sellerId, String role) {
        log.info("Creating livestream: title={}, seller={}, restaurant={}", 
                request.getTitle(), sellerId, request.getRestaurantId());

        Livestream livestream = new Livestream();
        livestream.setSellerId(sellerId);
        livestream.setRestaurantId(request.getRestaurantId());
        livestream.setTitle(request.getTitle());
        livestream.setDescription(request.getDescription());
        livestream.setStreamProvider(request.getStreamProvider());
        livestream.setStatus(LivestreamStatus.CREATED);

        livestream = livestreamRepository.save(livestream);
        
        log.info("Livestream created successfully: id={}, roomId={}", livestream.getId(), livestream.getRoomId());
        return mapper.toResponse(livestream);
    }

    @Transactional
    public StartLivestreamResponse startLivestream(UUID id, Long sellerId, String role) {
        log.info("🎬 Starting livestream: id={}, seller={}", id, sellerId);

        Livestream livestream = livestreamRepository.findById(id)
                .orElseThrow(() -> new LivestreamNotFoundException("Không tìm thấy livestream với ID: " + id));

        checkSellerPermission(sellerId, livestream);

        if (livestream.getStatus() != LivestreamStatus.CREATED) {
            throw new InvalidLivestreamStatusException(
                    "Không thể bắt đầu livestream. Trạng thái hiện tại: " + livestream.getStatus());
        }

        livestream.setStatus(LivestreamStatus.LIVE);
        livestream.setStartedAt(LocalDateTime.now());
        livestream = livestreamRepository.save(livestream);

        // Generate Agora token for HOST (seller)
        int expireSeconds = 3600; // 1 hour
        var tokenResponse = streamTokenService.generateToken(id, sellerId, TokenRole.HOST, expireSeconds);

        // Publish Kafka event
        LivestreamStartedEvent event = new LivestreamStartedEvent(
                livestream.getId(),
                livestream.getSellerId(),
                livestream.getRestaurantId(),
                livestream.getRoomId(),
                livestream.getStartedAt()
        );
        eventPublisher.publishLivestreamStarted(event);

        log.info("✅ Livestream started successfully: id={}, channelName={}", id, livestream.getChannelName());
        
        // Build response với token để seller join Agora ngay
        StartLivestreamResponse response = new StartLivestreamResponse();
        response.setLivestreamId(livestream.getId());
        response.setChannelName(livestream.getChannelName());
        response.setStatus(livestream.getStatus());
        response.setToken(tokenResponse.getToken());
        response.setUid(sellerId.intValue());
        response.setRole("HOST");
        response.setTokenExpiresAt(tokenResponse.getExpiresAt());
        response.setTitle(livestream.getTitle());
        response.setRestaurantId(livestream.getRestaurantId());
        response.setStartedAt(livestream.getStartedAt());
        
        return response;
    }

    @Transactional
    public LivestreamResponse endLivestream(UUID id, Long sellerId, String role) {
        log.info("Ending livestream: id={}, seller={}", id, sellerId);

        Livestream livestream = livestreamRepository.findById(id)
                .orElseThrow(() -> new LivestreamNotFoundException("Không tìm thấy livestream với ID: " + id));

        checkSellerPermission(sellerId, livestream);

        if (livestream.getStatus() != LivestreamStatus.LIVE) {
            throw new InvalidLivestreamStatusException(
                    "Không thể kết thúc livestream. Trạng thái hiện tại: " + livestream.getStatus());
        }

        livestream.setStatus(LivestreamStatus.ENDED);
        livestream.setEndedAt(LocalDateTime.now());
        livestream = livestreamRepository.save(livestream);

        // Calculate duration
        long durationMinutes = Duration.between(livestream.getStartedAt(), livestream.getEndedAt()).toMinutes();

        // Publish Kafka event
        LivestreamEndedEvent event = new LivestreamEndedEvent(
                livestream.getId(),
                livestream.getSellerId(),
                livestream.getRestaurantId(),
                livestream.getStartedAt(),
                livestream.getEndedAt(),
                durationMinutes
        );
        eventPublisher.publishLivestreamEnded(event);

        log.info("Livestream ended successfully: id={}, duration={} minutes", id, durationMinutes);
        return mapper.toResponse(livestream);
    }

    @Transactional(readOnly = true)
    public List<LivestreamResponse> getActiveLivestreams() {
        log.info("Getting all active livestreams");
        return livestreamRepository.findByStatus(LivestreamStatus.LIVE)
                .stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public LivestreamResponse getLivestreamById(UUID id) {
        log.info("Getting livestream by id: {}", id);
        Livestream livestream = livestreamRepository.findById(id)
                .orElseThrow(() -> new LivestreamNotFoundException("Không tìm thấy livestream với ID: " + id));
        return mapper.toResponse(livestream);
    }

    /**
     * Viewer join livestream - generate token VIEWER
     */
    @Transactional(readOnly = true)
    public JoinLivestreamResponse joinLivestream(UUID id, Long viewerId) {
        log.info("👀 Viewer joining livestream: id={}, viewer={}", id, viewerId);

        Livestream livestream = livestreamRepository.findById(id)
                .orElseThrow(() -> new LivestreamNotFoundException("Không tìm thấy livestream với ID: " + id));

        // Check livestream đang LIVE
        if (livestream.getStatus() != LivestreamStatus.LIVE) {
            throw new InvalidLivestreamStatusException(
                    "Livestream chưa bắt đầu hoặc đã kết thúc. Trạng thái: " + livestream.getStatus());
        }

        // Generate Agora token for VIEWER
        int expireSeconds = 3600; // 1 hour
        var tokenResponse = streamTokenService.generateToken(id, viewerId, TokenRole.VIEWER, expireSeconds);

        log.info("✅ Viewer joined successfully: livestream={}, viewer={}, channelName={}", 
                id, viewerId, livestream.getChannelName());

        // Build response với token để viewer join Agora
        JoinLivestreamResponse response = new JoinLivestreamResponse();
        response.setLivestreamId(livestream.getId());
        response.setChannelName(livestream.getChannelName());
        response.setTitle(livestream.getTitle());
        response.setRestaurantId(livestream.getRestaurantId());
        response.setToken(tokenResponse.getToken());
        response.setUid(viewerId.intValue());
        response.setTokenExpiresAt(tokenResponse.getExpiresAt());
        response.setSellerId(livestream.getSellerId());
        response.setStartedAt(livestream.getStartedAt());
        // response.setCurrentViewers(0); // TODO: Implement viewer counting

        return response;
    }

    @Transactional(readOnly = true)
    public List<LivestreamResponse> getLivestreamsBySeller(Long sellerId) {
        log.info("Getting livestreams by seller: {}", sellerId);
        return livestreamRepository.findBySellerId(sellerId)
                .stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LivestreamResponse> getLivestreamsByRestaurant(Long restaurantId) {
        log.info("Getting livestreams by restaurant: {}", restaurantId);
        return livestreamRepository.findByRestaurantId(restaurantId)
                .stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
    }

    private void checkSellerPermission(Long sellerId, Livestream livestream) {
        if (!livestream.getSellerId().equals(sellerId)) {
            throw new UnauthorizedLivestreamAccessException("Bạn không có quyền thao tác với livestream này");
        }
    }
}
