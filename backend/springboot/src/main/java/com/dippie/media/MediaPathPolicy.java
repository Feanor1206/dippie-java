package com.dippie.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class MediaPathPolicy {

    private MediaPathPolicy() {
    }

    static boolean isValidRelativePath(String value) {
        if (value == null || value.isBlank() || value.startsWith("/")) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\' || ch == ':' || ch < 32 || ch == 127) {
                return false;
            }
        }
        String[] parts = value.split("/", -1);
        for (String part : parts) {
            if ("..".equals(stripTrailingSpaces(part))) {
                return false;
            }
        }
        return true;
    }

    static Optional<Path> resolveRegularFile(Path root, String relativePath) {
        if (root == null || !isValidRelativePath(relativePath)) {
            return Optional.empty();
        }
        List<String> segments = extractSegments(relativePath);
        if (segments.isEmpty()) {
            return Optional.empty();
        }
        try {
            Path baseRoot;
            boolean usedRealRoot;
            try {
                baseRoot = root.toRealPath();
                if (!Files.isDirectory(baseRoot, LinkOption.NOFOLLOW_LINKS)) {
                    return Optional.empty();
                }
                usedRealRoot = true;
            } catch (IOException ex) {
                if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                    return Optional.empty();
                }
                baseRoot = root.toAbsolutePath().normalize();
                usedRealRoot = false;
            }

            Path current = baseRoot;
            for (int i = 0; i < segments.size(); i++) {
                current = current.resolve(segments.get(i));
                BasicFileAttributes attributes = Files.readAttributes(
                        current,
                        BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS
                );
                if (attributes.isSymbolicLink() || attributes.isOther()) {
                    return Optional.empty();
                }
                boolean lastSegment = i == segments.size() - 1;
                if (lastSegment ? !attributes.isRegularFile() : !attributes.isDirectory()) {
                    return Optional.empty();
                }
            }

            if (usedRealRoot) {
                Path noFollow = current.toRealPath(LinkOption.NOFOLLOW_LINKS);
                Path followed = current.toRealPath();
                if (!noFollow.equals(followed) || !followed.startsWith(baseRoot) || followed.equals(baseRoot)) {
                    return Optional.empty();
                }
                return Optional.of(followed);
            }

            Path normalized = current.toAbsolutePath().normalize();
            if (!normalized.startsWith(baseRoot) || normalized.equals(baseRoot)) {
                return Optional.empty();
            }
            return Optional.of(normalized);
        } catch (IOException | InvalidPathException | SecurityException ex) {
            return Optional.empty();
        }
    }

    private static List<String> extractSegments(String relativePath) {
        List<String> segments = new ArrayList<>();
        for (String part : relativePath.split("/", -1)) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            segments.add(part);
        }
        return segments;
    }

    private static String stripTrailingSpaces(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == ' ') {
            end--;
        }
        return value.substring(0, end);
    }
}
