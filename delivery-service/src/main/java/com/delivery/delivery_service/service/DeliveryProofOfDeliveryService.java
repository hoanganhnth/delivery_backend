package com.delivery.delivery_service.service;

import com.delivery.delivery_service.common.constants.RoleConstants;
import com.delivery.delivery_service.dto.request.CreateProofUploadIntentRequest;
import com.delivery.delivery_service.dto.response.ProofAccessResponse;
import com.delivery.delivery_service.dto.response.ProofOfDeliveryResponse;
import com.delivery.delivery_service.dto.response.ProofUploadIntentResponse;
import com.delivery.delivery_service.entity.Delivery;
import com.delivery.delivery_service.entity.DeliveryProofOfDelivery;
import com.delivery.delivery_service.entity.DeliveryProofStatus;
import com.delivery.delivery_service.entity.DeliveryStatus;
import com.delivery.delivery_service.exception.AccessDeniedException;
import com.delivery.delivery_service.exception.InvalidStatusException;
import com.delivery.delivery_service.exception.ResourceNotFoundException;
import com.delivery.delivery_service.repository.DeliveryProofOfDeliveryRepository;
import com.delivery.delivery_service.repository.DeliveryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Private signed-upload/read workflow and 90-day evidence retention boundary. */
@Slf4j
@Service
public class DeliveryProofOfDeliveryService {

    static final long MAX_PROOF_BYTES = 10L * 1024L * 1024L;
    static final int RETENTION_DAYS = 90;
    private static final int SWEEP_LIMIT = 100;

    private final DeliveryRepository deliveryRepository;
    private final DeliveryProofOfDeliveryRepository proofRepository;
    private final ProofObjectStorageRegistry storageRegistry;
    private final ShipperIdentityResolver shipperIdentityResolver;

    @Value("${delivery.pod.enabled:false}")
    private boolean podEnabled;

    public DeliveryProofOfDeliveryService(DeliveryRepository deliveryRepository,
                                          DeliveryProofOfDeliveryRepository proofRepository,
                                          ProofObjectStorageRegistry storageRegistry,
                                          ShipperIdentityResolver shipperIdentityResolver) {
        this.deliveryRepository = deliveryRepository;
        this.proofRepository = proofRepository;
        this.storageRegistry = storageRegistry;
        this.shipperIdentityResolver = shipperIdentityResolver;
    }

    @Transactional
    public ProofUploadIntentResponse createUploadIntent(Long deliveryId,
                                                         CreateProofUploadIntentRequest request,
                                                         Long principalId,
                                                         Long legacyUserId,
                                                         String role) {
        requireEnabled();
        validateUploadRequest(request);
        Delivery delivery = findDeliveryForUpdate(deliveryId);
        Long shipperId = requireAssignedShipper(delivery, principalId, legacyUserId, role);
        if (delivery.getStatus() != DeliveryStatus.PICKED_UP
                && delivery.getStatus() != DeliveryStatus.DELIVERING) {
            throw new InvalidStatusException("Chỉ có thể tạo bằng chứng sau khi đã lấy hàng");
        }
        if (proofRepository.existsByDeliveryIdAndStatus(deliveryId, DeliveryProofStatus.CONFIRMED)) {
            throw new InvalidStatusException("Đơn hàng đã có bằng chứng giao được xác nhận");
        }

        ProofObjectStorage storage = storageRegistry.requireConfiguredProvider();
        UUID proofId = UUID.randomUUID();
        String objectKey = "delivery-pod/" + deliveryId + "/" + proofId;
        ProofObjectStorage.SignedUpload signedUpload = storage.createSignedUpload(
                new ProofObjectStorage.UploadRequest(objectKey, request.getContentType(), request.getContentLengthBytes()));
        validateSignedUpload(signedUpload);

        DeliveryProofOfDelivery proof = new DeliveryProofOfDelivery();
        proof.setProofId(proofId);
        proof.setDeliveryId(deliveryId);
        proof.setShipperId(shipperId);
        proof.setStorageProvider(storage.providerId());
        proof.setObjectKey(objectKey);
        proof.setContentType(request.getContentType());
        proof.setDeclaredSizeBytes(request.getContentLengthBytes());
        proof.setStatus(DeliveryProofStatus.UPLOAD_PENDING);
        proof.setUploadExpiresAt(signedUpload.expiresAt());
        proofRepository.save(proof);

        ProofUploadIntentResponse response = new ProofUploadIntentResponse();
        response.setProofId(proofId);
        response.setStatus(proof.getStatus());
        response.setSignedUploadUrl(signedUpload.url());
        response.setRequiredHeaders(signedUpload.requiredHeaders() == null
                ? Map.of() : Map.copyOf(signedUpload.requiredHeaders()));
        response.setUploadExpiresAt(signedUpload.expiresAt());
        response.setMaxContentLengthBytes(MAX_PROOF_BYTES);
        return response;
    }

    @Transactional
    public ProofOfDeliveryResponse confirmUpload(Long deliveryId,
                                                 UUID proofId,
                                                 Long principalId,
                                                 Long legacyUserId,
                                                 String role) {
        requireEnabled();
        Delivery delivery = findDeliveryForUpdate(deliveryId);
        Long shipperId = requireAssignedShipper(delivery, principalId, legacyUserId, role);
        DeliveryProofOfDelivery proof = findProofForUpdate(proofId, deliveryId);
        if (!shipperId.equals(proof.getShipperId())) {
            throw new AccessDeniedException("Bằng chứng không thuộc shipper hiện tại");
        }
        if (proof.getStatus() == DeliveryProofStatus.CONFIRMED) {
            return toProofResponse(proof);
        }
        if (proof.getStatus() != DeliveryProofStatus.UPLOAD_PENDING) {
            throw new InvalidStatusException("Bằng chứng không còn ở trạng thái chờ xác nhận");
        }

        LocalDateTime now = LocalDateTime.now();
        if (!proof.getUploadExpiresAt().isAfter(now)) {
            proof.setStatus(DeliveryProofStatus.EXPIRED);
            proofRepository.save(proof);
            throw new InvalidStatusException("URL tải bằng chứng đã hết hạn");
        }

        ProofObjectStorage.StoredObjectMetadata metadata = storageRegistry
                .requireProvider(proof.getStorageProvider())
                .readMetadata(proof.getObjectKey());
        validateStoredObject(proof, metadata);

        proof.setVerifiedSizeBytes(metadata.contentLengthBytes());
        proof.setObjectChecksum(metadata.checksum());
        proof.setContentType(metadata.contentType());
        proof.setStatus(DeliveryProofStatus.CONFIRMED);
        proof.setConfirmedAt(now);
        proof.setRetentionExpiresAt(now.plusDays(RETENTION_DAYS));
        proofRepository.save(proof);
        return toProofResponse(proof);
    }

    @Transactional(readOnly = true)
    public ProofAccessResponse createReadAccess(Long deliveryId,
                                                UUID proofId,
                                                Long principalId,
                                                Long legacyUserId,
                                                String role) {
        requireEnabled();
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin giao hàng với ID: " + deliveryId));
        requireViewer(delivery, principalId, legacyUserId, role);
        DeliveryProofOfDelivery proof = proofRepository.findById(proofId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bằng chứng giao hàng"));
        if (!deliveryId.equals(proof.getDeliveryId())) {
            throw new ResourceNotFoundException("Không tìm thấy bằng chứng giao hàng");
        }
        if (proof.getStatus() != DeliveryProofStatus.CONFIRMED
                || proof.getRetentionExpiresAt() == null
                || !proof.getRetentionExpiresAt().isAfter(LocalDateTime.now())) {
            throw new InvalidStatusException("Bằng chứng giao hàng không còn khả dụng");
        }

        ProofObjectStorage.SignedRead signedRead = storageRegistry
                .requireProvider(proof.getStorageProvider())
                .createSignedRead(proof.getObjectKey());
        if (signedRead == null || signedRead.url() == null || signedRead.url().isBlank()
                || signedRead.expiresAt() == null || !signedRead.expiresAt().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("POD storage returned an invalid signed read URL");
        }
        ProofAccessResponse response = new ProofAccessResponse();
        response.setProofId(proof.getProofId());
        response.setSignedReadUrl(signedRead.url());
        response.setExpiresAt(signedRead.expiresAt());
        return response;
    }

    /** Removes the private object after the fixed 90-day retention period. */
    @Transactional
    public int purgeRetentionExpiredProofs() {
        if (!podEnabled) return 0;
        List<DeliveryProofOfDelivery> candidates = proofRepository.findRetentionExpiredForUpdate(
                LocalDateTime.now(), PageRequest.of(0, SWEEP_LIMIT));
        int purged = 0;
        for (DeliveryProofOfDelivery proof : candidates) {
            try {
                storageRegistry.requireProvider(proof.getStorageProvider()).deleteObject(proof.getObjectKey());
                proof.setStatus(DeliveryProofStatus.PURGED);
                proof.setPurgedAt(LocalDateTime.now());
                proofRepository.save(proof);
                purged++;
            } catch (RuntimeException failure) {
                // Keep the confirmed record so the next bounded sweep retries;
                // never claim retention has completed without object deletion.
                log.warn("Unable to purge POD proof {} for delivery {}", proof.getProofId(), proof.getDeliveryId(), failure);
            }
        }
        return purged;
    }

    private void requireEnabled() {
        if (!podEnabled) {
            throw new InvalidStatusException("Bằng chứng giao hàng chưa được bật");
        }
    }

    private Delivery findDeliveryForUpdate(Long deliveryId) {
        if (deliveryId == null || deliveryId <= 0) {
            throw new InvalidStatusException("Delivery ID is required");
        }
        return deliveryRepository.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin giao hàng với ID: " + deliveryId));
    }

    private DeliveryProofOfDelivery findProofForUpdate(UUID proofId, Long deliveryId) {
        if (proofId == null) throw new InvalidStatusException("Proof ID is required");
        DeliveryProofOfDelivery proof = proofRepository.findByIdForUpdate(proofId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bằng chứng giao hàng"));
        if (!deliveryId.equals(proof.getDeliveryId())) {
            throw new ResourceNotFoundException("Không tìm thấy bằng chứng giao hàng");
        }
        return proof;
    }

    private Long requireAssignedShipper(Delivery delivery, Long principalId, Long legacyUserId, String role) {
        Long shipperId = shipperIdentityResolver.resolveShipperId(principalId, legacyUserId, role);
        if (!Objects.equals(shipperId, delivery.getShipperId())) {
            throw new AccessDeniedException("Chỉ shipper được phân công mới có thể thao tác bằng chứng");
        }
        return shipperId;
    }

    private void requireViewer(Delivery delivery, Long principalId, Long legacyUserId, String role) {
        if (RoleConstants.ADMIN.equals(role)) return;
        if (RoleConstants.SHIPPER.equals(role)) {
            requireAssignedShipper(delivery, principalId, legacyUserId, role);
            return;
        }
        if (RoleConstants.USER.equals(role)
                && ((delivery.getCustomerPrincipalId() != null && delivery.getCustomerPrincipalId().equals(principalId))
                    || (delivery.getCustomerPrincipalId() == null && delivery.getCreatorId().equals(legacyUserId)))) {
            return;
        }
        if (RoleConstants.RESTAURANT_OWNER.equals(role)
                && ((delivery.getRestaurantOwnerPrincipalId() != null
                        && delivery.getRestaurantOwnerPrincipalId().equals(principalId))
                    || (delivery.getRestaurantOwnerPrincipalId() == null
                        && delivery.getRestaurantOwnerId() != null
                        && delivery.getRestaurantOwnerId().equals(legacyUserId)))) {
            return;
        }
        throw new AccessDeniedException("Bạn không có quyền xem bằng chứng giao hàng");
    }

    private void validateUploadRequest(CreateProofUploadIntentRequest request) {
        if (request == null || request.getContentType() == null || !isAllowedImageType(request.getContentType())
                || request.getContentLengthBytes() <= 0 || request.getContentLengthBytes() > MAX_PROOF_BYTES) {
            throw new InvalidStatusException("Ảnh bằng chứng phải là JPEG, PNG hoặc WebP và không quá 10 MB");
        }
    }

    private void validateSignedUpload(ProofObjectStorage.SignedUpload signedUpload) {
        if (signedUpload == null || signedUpload.url() == null || signedUpload.url().isBlank()
                || signedUpload.expiresAt() == null || !signedUpload.expiresAt().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("POD storage returned an invalid signed upload URL");
        }
    }

    private void validateStoredObject(DeliveryProofOfDelivery proof,
                                      ProofObjectStorage.StoredObjectMetadata metadata) {
        if (metadata == null || metadata.contentLengthBytes() <= 0
                || metadata.contentLengthBytes() > MAX_PROOF_BYTES
                || metadata.contentLengthBytes() > proof.getDeclaredSizeBytes()
                || !isAllowedImageType(metadata.contentType())
                || !proof.getContentType().equals(metadata.contentType())) {
            throw new InvalidStatusException("Đối tượng bằng chứng không đúng ràng buộc đã ký");
        }
    }

    private boolean isAllowedImageType(String contentType) {
        return "image/jpeg".equals(contentType)
                || "image/png".equals(contentType)
                || "image/webp".equals(contentType);
    }

    private ProofOfDeliveryResponse toProofResponse(DeliveryProofOfDelivery proof) {
        ProofOfDeliveryResponse response = new ProofOfDeliveryResponse();
        response.setProofId(proof.getProofId());
        response.setStatus(proof.getStatus());
        response.setContentType(proof.getContentType());
        response.setSizeBytes(proof.getVerifiedSizeBytes());
        response.setConfirmedAt(proof.getConfirmedAt());
        response.setRetentionExpiresAt(proof.getRetentionExpiresAt());
        return response;
    }
}
