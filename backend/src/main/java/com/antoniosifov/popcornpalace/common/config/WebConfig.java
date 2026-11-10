package com.antoniosifov.popcornpalace.common.config;

import com.antoniosifov.popcornpalace.common.observability.TraceIdFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

@Configuration
public class WebConfig implements WebMvcConfigurer
{
    private final String[] allowedOriginPatterns;

    public WebConfig(@Value("${app.cors.allowed-origin-patterns}") String[] allowedOriginPatterns)
    {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry)
    {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Location", TraceIdFilter.HEADER);
    }

    /** Injected wherever the current time matters, so tests can control it. */
    @Bean
    Clock clock()
    {
        return Clock.systemUTC();
    }
}
