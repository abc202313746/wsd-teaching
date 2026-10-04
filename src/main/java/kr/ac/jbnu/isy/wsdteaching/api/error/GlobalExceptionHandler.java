package kr.ac.jbnu.isy.wsdteaching.api.error;

import kr.ac.jbnu.isy.wsdteaching.api.response.ApiError;
import kr.ac.jbnu.isy.wsdteaching.api.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleApiException(ApiException exception) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(exception.getStatus());
        if (exception.getStatus() == HttpStatus.SERVICE_UNAVAILABLE) {
            builder.header(HttpHeaders.RETRY_AFTER, "5");
        }
        return builder.body(ApiResponse.error(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ApiError>> handleUnexpectedException(Exception exception) {
        log.error("Unexpected server error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request
    ) {
        String code;
        String message;
        switch (statusCode.value()) {
            case 400 -> {
                code = "BAD_REQUEST";
                message = "요청 본문과 파라미터 형식을 확인하세요.";
            }
            case 404 -> {
                code = "NOT_FOUND";
                message = "요청한 경로를 찾을 수 없습니다.";
            }
            case 405 -> {
                code = "METHOD_NOT_ALLOWED";
                message = "이 경로에서 지원하지 않는 HTTP 메서드입니다.";
            }
            case 415 -> {
                code = "UNSUPPORTED_MEDIA_TYPE";
                message = "요청 본문은 application/json 형식으로 보내세요.";
            }
            default -> {
                code = "HTTP_ERROR";
                message = "요청을 처리할 수 없습니다.";
            }
        }
        return new ResponseEntity<>(ApiResponse.error(code, message), headers, statusCode);
    }
}
