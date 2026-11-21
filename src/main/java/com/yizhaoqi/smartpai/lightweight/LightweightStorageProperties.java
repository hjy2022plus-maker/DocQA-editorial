package com.yizhaoqi.smartpai.lightweight;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;

/** Configuration for the single-machine dissertation profile. */
@Configuration
@ConfigurationProperties(prefix = "app.storage")
public class LightweightStorageProperties {
    private Path root = Paths.get("./data/documents");
    private long maxFileSizeBytes = 20L * 1024 * 1024;
    private Set<String> allowedExtensions = new LinkedHashSet<>(Set.of("pdf", "doc", "docx", "md", "txt"));

    public Path getRoot() { return root; }
    public void setRoot(Path root) { this.root = root; }
    public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    public void setMaxFileSizeBytes(long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; }
    public Set<String> getAllowedExtensions() { return allowedExtensions; }
    public void setAllowedExtensions(Set<String> allowedExtensions) { this.allowedExtensions = allowedExtensions; }
}
