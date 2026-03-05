package com.payrout.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RawBodyCachingFilter extends OncePerRequestFilter {

    public static final String RAW_BODY_ATTR = "RAW_BODY";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request);

        // ✅ available before controller runs
        wrapped.setAttribute(RAW_BODY_ATTR, wrapped.getCachedBody());

        filterChain.doFilter(wrapped, response);
    }
}