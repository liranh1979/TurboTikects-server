package com.turbotikects.turbotikectsserver.config;

import com.turbotikects.turbotikectsserver.security.AuthInterceptor;
import com.turbotikects.turbotikectsserver.security.PermissionInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.origins}")
    private String allowedOrigins;

    private final AuthInterceptor authInterceptor;
    private final PermissionInterceptor permissionInterceptor;

    public WebConfig(AuthInterceptor authInterceptor, PermissionInterceptor permissionInterceptor) {
        this.authInterceptor = authInterceptor;
        this.permissionInterceptor = permissionInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // SAML ACS endpoint receives POSTs from external IdPs (e.g. Azure AD).
        // The IdP's Origin will never be in our allowed list, so permit all origins here.
        registry.addMapping("/api/v1/saml2/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST")
                .allowCredentials(false)
                .maxAge(3600);

        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        String[] publicPaths = {
            "/api/v1/auth/**",
            "/api/v1/locales/**",
            "/api/v1/languages",
            "/api/v1/sso/**",
            "/api/v1/saml2/**",
            "/api/v1/attachments/download",
            "/api/v1/attachments/preview",
            "/api/v1/attachments/stream"
        };

        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(publicPaths);

        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(publicPaths);
    }
}