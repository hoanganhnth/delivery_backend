package com.delivery.api_gateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import com.delivery.api_gateway.dto.AuthRegisterRequest;
import com.delivery.api_gateway.dto.AuthRegisterResponse;
import com.delivery.api_gateway.dto.OrchestratorRegisterRequest;
import com.delivery.api_gateway.dto.UserRegisterRequest;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/orchestrator")
@RequiredArgsConstructor
public class OrchestratorController {

    private final RestTemplate restTemplate;

    @Value("${internal.token}")
    private String internalToken;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody OrchestratorRegisterRequest request) {
        try {
            // B1: Gửi yêu cầu đăng ký đến auth-service
            AuthRegisterRequest authRegisterRequest = new AuthRegisterRequest(
                    request.getEmail(),
                    request.getPassword(),
                    request.getRole()
            );

            restTemplate.postForEntity(
                    "http://localhost:8081/api/auth/register",
                    authRegisterRequest,
                    Void.class
            );

            // B2: Gọi GET /accounts/email/{email} với Internal-Token
            HttpHeaders headers = new HttpHeaders();
            headers.set("Internal-Token", internalToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<AuthRegisterResponse> authInfoResponse = restTemplate.exchange(
                    "http://localhost:8081/api/auth/accounts/email/" + request.getEmail(),
                    HttpMethod.GET,
                    entity,
                    AuthRegisterResponse.class
            );

            AuthRegisterResponse authInfo = authInfoResponse.getBody();
            if (authInfo == null || authInfo.getAuthId() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Không lấy được thông tin auth");
            }

            // B3: Tạo user bên user-service
            UserRegisterRequest userRequest = new UserRegisterRequest();
            userRequest.setAuthId(authInfo.getAuthId());
            userRequest.setEmail(request.getEmail());
            userRequest.setRole(request.getRole());
            userRequest.setFullName(request.getFullName());
            userRequest.setPhone(request.getPhone());
            userRequest.setDob(request.getDob());
            userRequest.setAddress(request.getAddress());

            restTemplate.postForEntity(
                    "http://localhost:8084/api/users",
                    userRequest,
                    Void.class
            );

            return ResponseEntity.ok("Đăng ký thành công");

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body("Lỗi từ service: " + ex.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Lỗi hệ thống: " + e.getMessage());
        }
    }
}
