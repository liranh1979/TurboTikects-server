package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.UserDto;
import com.turbotikects.turbotikectsserver.dto.dashboard.TicketsDashboardResponseDto;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.DashboardService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/tickets")
    @RequirePermission("TICKET_MANAGER")
    public TicketsDashboardResponseDto getTicketsDashboard(HttpServletRequest request) {
        UserDto caller = currentUser(request);
        Integer companyId = (caller != null && !caller.isSuperAdmin()) ? caller.getCompanyId() : null;
        return dashboardService.getDashboard(companyId);
    }

    private UserDto currentUser(HttpServletRequest request) {
        Object user = request.getAttribute("currentUser");
        if (user instanceof UserDto dto) return dto;
        return null;
    }
}
