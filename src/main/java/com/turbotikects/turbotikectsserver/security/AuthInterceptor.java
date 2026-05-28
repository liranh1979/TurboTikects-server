package com.turbotikects.turbotikectsserver.security;

import com.turbotikects.turbotikectsserver.dto.UserDto;
import com.turbotikects.turbotikectsserver.services.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final UserService userService;

    public AuthInterceptor(@Lazy UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String sessionId = extractSessionId(request);
        if (sessionId == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "No session");
            return false;
        }

        UserDto user = userService.getSessionReadOnly(sessionId);
        if (user == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session expired");
            return false;
        }

        request.setAttribute("currentUser", user);
        return true;
    }

    private String extractSessionId(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if ("SESSION_ID".equals(c.getName())) return c.getValue();
        }
        return null;
    }
}
