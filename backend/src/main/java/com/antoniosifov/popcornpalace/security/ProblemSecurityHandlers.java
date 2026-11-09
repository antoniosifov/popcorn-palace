package com.antoniosifov.popcornpalace.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Spring Security rejects requests in its filter chain, before they reach a
 * controller, so the usual exception handler never sees those failures. This
 * class hands them to it, which makes 401 and 403 responses use the same
 * problem-details format as every other error (see ApiExceptionHandler).
 */
@Component
class ProblemSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler
{
    private final HandlerExceptionResolver exceptionResolver;

    ProblemSecurityHandlers(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver)
    {
        this.exceptionResolver = exceptionResolver;
    }

    /** No valid credentials: missing, malformed, wrongly signed or expired token. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
    {
        exceptionResolver.resolveException(request, response, null, exception);
    }

    /** Valid credentials, but the account is not allowed to do this. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
    {
        exceptionResolver.resolveException(request, response, null, exception);
    }
}
