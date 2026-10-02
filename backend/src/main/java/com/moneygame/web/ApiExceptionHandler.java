package com.moneygame.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

/**
 * REST 오류를 HTTP 상태로 바꾼다. 본문은 {"error": "사유"}.
 *
 *   IllegalArgumentException  400  요청이 잘못됐다 (닉네임 형식, 없는 사용자, 인원 범위 등)
 *   NoSuchElementException    404  방이 없다
 *   IllegalStateException     409  지금 상태에서는 할 수 없다 (이미 시작함, 가득 참, 시나리오 없음 등)
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    public record ErrorBody(String error) {
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorBody> badRequest(Exception e) {
        String message = e instanceof HttpMessageNotReadableException
                ? "요청 본문을 읽을 수 없습니다 (JSON 형식, 필드 값 확인)"
                : e.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorBody(message));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorBody> notFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody(e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorBody> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorBody(e.getMessage()));
    }
}
