package kr.ac.jbnu.isy.wsdteaching.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final LoggingInterceptor loggingInterceptor;
    private final DemoErrorInterceptor demoErrorInterceptor;

    public WebConfig(LoggingInterceptor loggingInterceptor, DemoErrorInterceptor demoErrorInterceptor) {
        this.loggingInterceptor = loggingInterceptor;
        this.demoErrorInterceptor = demoErrorInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 오류 재현 중에도 완료 로그가 남도록 로깅을 먼저 등록한다.
        registry.addInterceptor(loggingInterceptor).addPathPatterns("/api/**").order(0);
        registry.addInterceptor(demoErrorInterceptor).addPathPatterns("/api/**").order(1);
    }
}
