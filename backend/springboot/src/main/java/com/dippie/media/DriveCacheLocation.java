package com.dippie.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class DriveCacheLocation implements MediaLocationStrategy {

    private final Path cacheRoot;

    public DriveCacheLocation(MediaProperties properties) {
        String configured = properties == null ? null : properties.cacheRoot();
        this.cacheRoot = configured == null || configured.isBlank() ? null : Path.of(configured.trim());
    }

    @Override
    public Optional<Path> locate(MediaAsset asset) {
        if (cacheRoot == null || asset == null || asset.getByteLength() <= 0) {
            return Optional.empty();
        }
        Optional<Path> candidate = MediaPathPolicy.resolveRegularFile(cacheRoot, asset.getRelativePath());
        if (candidate.isEmpty()) {
            return Optional.empty();
        }
        try {
            if (Files.size(candidate.get()) == asset.getByteLength()) {
                return candidate;
            }
        } catch (IOException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
