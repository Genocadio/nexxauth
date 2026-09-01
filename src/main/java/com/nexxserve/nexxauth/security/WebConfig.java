package com.nexxserve.nexxauth.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RequestTimeoutInterceptor requestTimeoutInterceptor;

    public WebConfig(RequestTimeoutInterceptor requestTimeoutInterceptor) {
        this.requestTimeoutInterceptor = requestTimeoutInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestTimeoutInterceptor);
    }
}
