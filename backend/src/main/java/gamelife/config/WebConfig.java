package gamelife.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Serves the content folder at /content/** straight from disk, without caching, so a picture
 * dropped into content/enemies shows up after "reload content" with no rebuild (brief 12b).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String contentDir;

    public WebConfig(@Value("${gamelife.content-dir}") String contentDir) {
        this.contentDir = contentDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(contentDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/content/**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/")
                .setCacheControl(CacheControl.noCache());
    }
}
