package gamelife.content;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ContentController {
    private final ContentService content;

    public ContentController(ContentService content) {
        this.content = content;
    }

    @GetMapping("/api/content")
    public ContentService.Catalog catalog() {
        return content.catalog();
    }

    /** Settings, "Reload content": rescans the folders, no restart needed. */
    @PostMapping("/api/content/reload")
    public ContentService.Catalog reload() {
        return content.reload();
    }
}
