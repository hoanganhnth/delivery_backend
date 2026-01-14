package com.delivery.livestream_service.service;

import com.delivery.livestream_service.config.agora.RtcTokenBuilder;
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
 * Service for generating streaming tokens (Agora)
 * Uses RtcTokenBuilder to generate real Agora tokens
 */
@Slf4j
@Service
public class StreamTokenService {

    @Value("${agora.app-id}")
    private String agoraAppId;

    @Value("${agora.app-certificate}")
    private String agoraAppCertificate;
    
    private final int expireTimeInSeconds = 3600;   
    private final LivestreamRepository livestreamRepository;
    private final RtcTokenBuilder rtcTokenBuilder;

    public StreamTokenService(LivestreamRepository livestreamRepository) {
        this.livestreamRepository = livestreamRepository;
        this.rtcTokenBuilder = new RtcTokenBuilder();
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
        String channelName = livestream.getChannelName();
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(expireSeconds);

        // Generate real Agora token using RtcTokenBuilder
        String token = generateAgoraToken(channelName, userId, role, expireSeconds);

        log.info("Token generated successfully for livestream={}, channelName={}, expires at {}", 
                livestreamId, channelName, expiresAt);
        return new TokenResponse(token, roomId, livestreamId, expiresAt);
    }

    /**
     * Generate Agora token using RtcTokenBuilder
     */
    private String generateAgoraToken(String channelName, Long userId, TokenRole role, Integer expireSeconds) {
        try {
            int uid = userId.intValue();
            int timestamp = (int)(System.currentTimeMillis() / 1000 + expireSeconds);
            
            // Convert TokenRole to Agora Role
            RtcTokenBuilder.Role agoraRole = (role == TokenRole.HOST) 
                    ? RtcTokenBuilder.Role.Role_Publisher 
                    : RtcTokenBuilder.Role.Role_Subscriber;
            
            String token = rtcTokenBuilder.buildTokenWithUid(
                    agoraAppId, 
                    agoraAppCertificate, 
                    channelName, 
                    uid, 
                    agoraRole, 
                    timestamp
            );
            
            log.info("✅ Agora token generated: channel={}, uid={}, role={}", channelName, uid, role);
            return token;
            
        } catch (Exception e) {
            log.error("❌ Failed to generate Agora token: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể tạo Agora token: " + e.getMessage());
        }
    }
}
