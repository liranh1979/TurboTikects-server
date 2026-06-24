package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.repositorys.UserRepository;
import com.turbotikects.turbotikectsserver.services.UserService;
import com.turbotikects.turbotikectsserver.dto.LoginRequest;
import com.turbotikects.turbotikectsserver.dto.UserDto;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class LoginController {


    @Autowired UserService userService;

    @PostMapping("/api/v1/auth/login")
    public UserDto login(@RequestBody LoginRequest loginRequest, HttpServletResponse response) {

        UserDto userDto = userService.login(loginRequest);

        String sessionId = UUID.randomUUID().toString();

        Cookie cookie = new Cookie("SESSION_ID", sessionId);
        cookie.setHttpOnly(true);   // Prevents JavaScript access (XSS protection)
        cookie.setSecure(false);    // Set to true if using HTTPS
        cookie.setPath("/");        // Available for all routes
        cookie.setMaxAge(60 * 60);  // 1 hour expiry
        response.addCookie(cookie);
        userService.saveSession(sessionId,userDto);
        return userDto;

    }

    @GetMapping("/api/v1/auth/me")
    public  UserDto  loginWithCookie(HttpServletRequest httpServletRequest){

        Cookie cookie =  getCookie(httpServletRequest);
        if(cookie == null){
            return null;
        }

        UserDto userDto = userService.getSessionReadOnly(cookie.getValue());
        // "/api/v1/auth/**" is public, so AuthInterceptor's per-request freshening never runs
        // for this endpoint — refresh preferredLanguage here too, otherwise a Personal
        // Settings language change wouldn't take effect until the next login.
        if (userDto != null) {
            userDto.setPreferredLanguage(userService.getPreferredLanguage(userDto.getUserId()));
        }
        return userDto;
    }

    @PostMapping("/api/v1/auth/logout")
    public void logout(HttpServletRequest httpServletRequest, HttpServletResponse response){
        Cookie cookie =  getCookie(httpServletRequest);
        if(cookie == null){
            return;
        }

       userService.logoutUser(cookie.getValue());

        cookie = new Cookie("SESSION_ID", null);
        cookie.setHttpOnly(true);   // Prevents JavaScript access (XSS protection)
        cookie.setSecure(false);    // Set to true if using HTTPS
        cookie.setPath("/");        // Available for all routes
        cookie.setMaxAge(60 * 60);  // 1 hour expiry
        response.addCookie(cookie);
    }


    private Cookie getCookie(HttpServletRequest httpServletRequest){

        Cookie[] cookies =  httpServletRequest.getCookies();
        if(cookies == null){
            return null;
        }

        for( Cookie cookie: cookies){
            if(cookie != null && "SESSION_ID".equals(cookie.getName())){
                return cookie;
            }
        }
        return null;

    }


}
