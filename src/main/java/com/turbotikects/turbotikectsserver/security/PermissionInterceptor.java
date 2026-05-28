package com.turbotikects.turbotikectsserver.security;

import com.turbotikects.turbotikectsserver.dto.UserDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;

@Component
public class PermissionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod method)) return true;

        RequirePermission annotation = method.getMethodAnnotation(RequirePermission.class);
        if (annotation == null) {
            annotation = method.getBeanType().getAnnotation(RequirePermission.class);
        }
        if (annotation == null) return true;

        UserDto user = (UserDto) request.getAttribute("currentUser");
        if (user == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        if (user.isSuperAdmin()) return true;

        List<String> effective = user.getEffectivePermissions();
        if (effective == null) effective = List.of();

        String[] required = annotation.value();
        boolean hasAny = Arrays.stream(required).anyMatch(effective::contains);
        if (!hasAny) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Insufficient permissions");
            return false;
        }

        return true;
    }
}
