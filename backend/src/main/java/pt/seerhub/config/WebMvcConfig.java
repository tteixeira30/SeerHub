package pt.seerhub.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import pt.seerhub.community.security.CommunityPermissionInterceptor;

/**
 * Regista o {@link CommunityPermissionInterceptor} (R4) sobre {@code /api/**}.
 * Deliberadamente <b>sem</b> {@code @EnableWebMvc} — não substituir a
 * auto-configuração de MVC do Spring Boot, só acrescentar o interceptor.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CommunityPermissionInterceptor communityPermissionInterceptor;

    public WebMvcConfig(CommunityPermissionInterceptor communityPermissionInterceptor) {
        this.communityPermissionInterceptor = communityPermissionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(communityPermissionInterceptor).addPathPatterns("/api/**");
    }
}
