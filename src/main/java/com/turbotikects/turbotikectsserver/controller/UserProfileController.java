package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.ChangePasswordDto;
import com.turbotikects.turbotikectsserver.dto.SelfProfileDto;
import com.turbotikects.turbotikectsserver.dto.UpdateOwnProfileDto;
import com.turbotikects.turbotikectsserver.dto.UserDto;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.UserProfileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

// Deliberately NOT nested under /api/v1/users — UserManagementController already owns
// PATCH/DELETE /api/v1/users/{id}, and Spring's {id} wildcard would also match a literal
// "/me" segment, creating an ambiguous mapping against a completely different permission
// gate (MANAGE_USERS vs AUTHENTICATED here). Every method resolves the target user from the
// session ("currentUser" request attribute) only — never from a path/body-supplied id — so
// there is no way to point this at someone else's account.
@RestController
@RequestMapping("/api/v1/profile")
@RequirePermission("AUTHENTICATED")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    private Long callerId(HttpServletRequest req) {
        UserDto caller = (UserDto) req.getAttribute("currentUser");
        return caller.getUserId();
    }

    @GetMapping
    public SelfProfileDto getOwnProfile(HttpServletRequest req) {
        return userProfileService.getOwnProfile(callerId(req));
    }

    @PatchMapping
    public void updateOwnProfile(@RequestBody UpdateOwnProfileDto dto, HttpServletRequest req) {
        userProfileService.updateOwnProfile(callerId(req), dto);
    }

    @PostMapping("/password")
    public void changeOwnPassword(@RequestBody ChangePasswordDto dto, HttpServletRequest req) {
        userProfileService.changeOwnPassword(callerId(req), dto);
    }
}
