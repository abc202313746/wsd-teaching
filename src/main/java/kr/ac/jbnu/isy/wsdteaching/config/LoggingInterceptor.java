package kr.ac.jbnu.isy.wsdteaching.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoggingInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);
    private static final String START_TIME = LoggingInterceptor.class.getName() + ".startTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME, System.nanoTime());
        log.info("요청 시작: {} {}", request.getMethod(), request.getRequestURI());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        Long started = (Long) request.getAttribute(START_TIME);
        long durationMs = started == null ? 0 : (System.nanoTime() - started) / 1_000_000;
        log.info("요청 완료: {} {}, status={}, durationMs={}",
                request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
    }
}
