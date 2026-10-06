package com.delivery.livestream_service.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.livestream_service.payload.BaseResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class LivestreamErrorContractTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void missingRoomUsesStableMachineReadableCode() throws Exception {
        ResponseEntity<BaseResponse<String>> response = handler.handleLivestreamNotFound(
                new LivestreamNotFoundException("Không tìm thấy livestream"));

        assertError(response, 404, "ROOM_NOT_FOUND");
    }

    @Test
    void invalidLifecycleStateUsesStableMachineReadableCode() throws Exception {
        ResponseEntity<BaseResponse<String>> response = handler.handleInvalidLivestreamStatus(
                new InvalidLivestreamStatusException("Livestream chưa bắt đầu"));

        assertError(response, 400, "INVALID_STATUS");
    }

    @Test
    void ownershipDenialUsesStableMachineReadableCode() throws Exception {
        ResponseEntity<BaseResponse<String>> response = handler.handleUnauthorizedAccess(
                new UnauthorizedLivestreamAccessException("Bạn không có quyền"));

        assertError(response, 403, "OWNERSHIP_DENIED");
    }

    @Test
    void successEnvelopeDoesNotExposeAnErrorMember() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(
                new BaseResponse<>(1, "ok", "Thành công")));

        assertThat(json.has("error")).isFalse();
    }

    @Test
    void missingProductDoesNotMasqueradeAsAMissingRoom() throws Exception {
        ResponseEntity<BaseResponse<String>> response = handler.handleLivestreamProductNotFound(
                new LivestreamProductNotFoundException("Không tìm thấy sản phẩm trong livestream"));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(response.getBody()));
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(json.path("status").asInt()).isZero();
        assertThat(json.has("error")).isFalse();
    }

    private void assertError(ResponseEntity<BaseResponse<String>> response, int httpStatus, String code)
            throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(httpStatus);
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(response.getBody()));
        assertThat(json.path("status").asInt()).isZero();
        assertThat(json.path("message").asText()).isNotBlank();
        assertThat(json.path("data").isNull()).isTrue();
        assertThat(json.path("error").path("code").asText()).isEqualTo(code);
    }
}
