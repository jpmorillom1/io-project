package jpap.dev.io_api.infrastructure.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // El navegador manda Origin incluso en requests "same-origin" detrás de un reverse
    // proxy (ver docker-compose.yml: nginx del contenedor `ui` reenvía /api al backend).
    // Spring rechaza con 403 cualquier Origin que no matchee esta lista, así que el
    // dominio público de cada deploy tiene que estar acá — no alcanza con localhost.
    private final String[] allowedOrigins;

    public WebConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
