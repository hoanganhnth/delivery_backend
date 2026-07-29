package com.delivery.search_service.controller;

import com.delivery.search_service.exception.SearchUnavailableException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.delivery.search_service.payload.BaseResponse;

@RestControllerAdvice
public class SearchExceptionHandler {

    @ExceptionHandler(SearchUnavailableException.class)
    ResponseEntity<BaseResponse<Void>> handleUnavailable(SearchUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new BaseResponse<>(0, null, "Search is temporarily unavailable"));
    }

    @ExceptionHandler({ConstraintViolationException.class, IllegalArgumentException.class})
    ResponseEntity<BaseResponse<Void>> handleBadRequest(Exception exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new BaseResponse<>(0, null, exception.getMessage()));
    }
}
