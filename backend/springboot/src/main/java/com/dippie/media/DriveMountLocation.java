package com.dippie.media;

import java.nio.file.Path;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class DriveMountLocation implements MediaLocationStrategy {

    private static final String DRIVE_PREFIX = "VideoDieppie/";

    private final Path driveRoot;

    public DriveMountLocation(MediaProperties properties) {
        String configured = properties == null ? null : properties.driveMount();
        this.driveRoot = normalizeMountRoot(configured);
    }

    @Override
    public Optional<Path> locate(MediaAsset asset) {
        if (driveRoot == null || asset == null) {
            return Optional.empty();
        }
        String relativePath = asset.getRelativePath();
        if (!MediaPathPolicy.isValidRelativePath(relativePath)) {
            return Optional.empty();
        }
        Optional<Path> direct = MediaPathPolicy.resolveRegularFile(driveRoot, relativePath);
        if (direct.isPresent()) {
            return direct;
        }
        if (relativePath.startsWith(DRIVE_PREFIX)) {
            String stripped = relativePath.substring(DRIVE_PREFIX.length());
            return MediaPathPolicy.resolveRegularFile(driveRoot, stripped);
        }
        return Optional.empty();
    }

    private static Path normalizeMountRoot(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        String trimmed = configured.trim();
        if (trimmed.length() == 2 && Character.isLetter(trimmed.charAt(0)) && trimmed.charAt(1) == ':') {
            return Path.of(trimmed + "/");
        }
        return Path.of(trimmed);
    }
}
