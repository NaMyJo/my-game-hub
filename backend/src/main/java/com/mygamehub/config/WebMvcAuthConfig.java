package com.mygamehub.config;

import com.mygamehub.auth.FirebaseAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcAuthConfig implements WebMvcConfigurer {

    private final FirebaseAuthInterceptor firebaseAuthInterceptor;

    public WebMvcAuthConfig(FirebaseAuthInterceptor firebaseAuthInterceptor) {
        this.firebaseAuthInterceptor = firebaseAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(firebaseAuthInterceptor)
                .addPathPatterns("/api/**");
    }
}
