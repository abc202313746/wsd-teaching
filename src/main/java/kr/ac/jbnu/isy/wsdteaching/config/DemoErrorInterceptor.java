package kr.ac.jbnu.isy.wsdteaching.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.ac.jbnu.isy.wsdteaching.api.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class DemoErrorInterceptor implements HandlerInterceptor {
    private final boolean enabled;

    public DemoErrorInterceptor(@Value("${app.demo-errors-enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String demoError = request.getHeader("X-Demo-Error");
        if (!enabled || demoError == null) {
            return true;
        }
        switch (demoError) {
            case "500" -> throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "DEMO_INTERNAL_ERROR", "실습용으로 서버 내부 오류를 재현했습니다.");
            case "503" -> throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "DEMO_SERVICE_UNAVAILABLE", "실습용으로 일시적인 서비스 중단을 재현했습니다.");
            default -> throw new ApiException(HttpStatus.BAD_REQUEST,
                    "INVALID_DEMO_ERROR", "X-Demo-Error에는 500 또는 503을 지정하세요.");
        }
    }
}
