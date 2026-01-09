package com.delivery.livestream_service.service;

import com.delivery.livestream_service.dto.response.TokenResponse;
import com.delivery.livestream_service.entity.Livestream;
import com.delivery.livestream_service.enums.LivestreamStatus;
import com.delivery.livestream_service.enums.TokenRole;
import com.delivery.livestream_service.exception.InvalidLivestreamStatusException;
import com.delivery.livestream_service.exception.LivestreamNotFoundException;
import com.delivery.livestream_service.repository.LivestreamRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for generating streaming tokens (Agora/LiveKit)
 * Note: This is a placeholder implementation. 
 * For production, integrate with actual Agora RTC SDK or LiveKit SDK.
 */
@Slf4j
@Service
public class StreamTokenService {

    @Value("${agora.app-id}")
    private String agoraAppId;

    @Value("${agora.app-certificate}")
    private String agoraAppCertificate;

    private final LivestreamRepository livestreamRepository;

    public StreamTokenService(LivestreamRepository livestreamRepository) {
        this.livestreamRepository = livestreamRepository;
    }

    public TokenResponse generateToken(UUID livestreamId, Long userId, TokenRole role, Integer expireSeconds) {
        log.info("Generating token for livestream={}, user={}, role={}, expire={}", 
                livestreamId, userId, role, expireSeconds);

        Livestream livestream = livestreamRepository.findById(livestreamId)
                .orElseThrow(() -> new LivestreamNotFoundException("Không tìm thấy livestream với ID: " + livestreamId));

        // Validate livestream status based on role
        if (role == TokenRole.VIEWER && livestream.getStatus() != LivestreamStatus.LIVE) {
            throw new InvalidLivestreamStatusException("Livestream chưa bắt đầu. Không thể tạo token cho viewer.");
        }

        String roomId = livestream.getRoomId();
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(expireSeconds);

        // TODO: Integrate with actual Agora SDK
        // For now, returning a mock token
        String token = generateAgoraToken(roomId, userId, role, expireSeconds);

        log.info("Token generated successfully for livestream={}, expires at {}", livestreamId, expiresAt);
        return new TokenResponse(token, roomId, livestreamId, expiresAt);
    }

    /**
     * Placeholder for Agora token generation
     * In production, use: io.agora.rtc.RtcTokenBuilder
     */
    private String generateAgoraToken(String roomId, Long userId, TokenRole role, Integer expireSeconds) {
        // Mock implementation - replace with actual Agora SDK call
        // Example with Agora SDK:
        // RtcTokenBuilder builder = new RtcTokenBuilder();
        // int uid = userId.intValue();
        // int privilege = role == TokenRole.HOST ? 1 : 2;
        // int timestamp = (int)(System.currentTimeMillis() / 1000 + expireSeconds);
        // return builder.buildTokenWithUid(agoraAppId, agoraAppCertificate, roomId, uid, privilege, timestamp);
        
        log.warn("Using mock token - replace with actual Agora SDK integration");
        return String.format("MOCK_TOKEN_%s_%s_%s_%d", roomId, userId, role, System.currentTimeMillis());
    }
}
