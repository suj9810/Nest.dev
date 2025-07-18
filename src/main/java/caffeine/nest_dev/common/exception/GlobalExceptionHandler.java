package caffeine.nest_dev.common.exception;

import caffeine.nest_dev.common.dto.CommonResponse;
import caffeine.nest_dev.common.enums.BaseCode;
import caffeine.nest_dev.common.enums.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<CommonResponse<Object>> handleBaseException(BaseException e) {
        log.warn(e.getMessage());
        e.printStackTrace(); // 에러 로그 띄우기
        BaseCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(CommonResponse.of(errorCode));
    }

    // SSE 연결 끊김 예외 처리
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsableException(AsyncRequestNotUsableException e) {
        log.warn("Servlet container error notification for disconnected client");
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncRequestTimeoutException(AsyncRequestTimeoutException e) {
        log.warn("SSE request timeout - client connection may be slow or disconnected");
    }

    @ExceptionHandler(HttpMessageNotWritableException.class)
    public ResponseEntity<?> handleHttpMessageNotWritableException(
            HttpMessageNotWritableException e,
            HttpServletRequest request) {

        String acceptHeader = request.getHeader("Accept");

        if (acceptHeader != null && acceptHeader.contains("text/event-stream")) {
            log.warn("Cannot write CommonResponse to SSE stream: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        log.warn("HTTP 메시지 쓰기 실패: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CommonResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    // Optionally → 모든 예외 (예상 못한 500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponse<Object>> handleException(Exception e) {
        log.warn(e.getMessage());

        e.printStackTrace(); // 에러 로그 띄우기
        return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CommonResponse.of(caffeine.nest_dev.common.enums.ErrorCode.INTERNAL_SERVER_ERROR));
    }
}