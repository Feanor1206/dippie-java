package com.dippie.media;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

// Một Strategy của MediaFileResolver để tìm video trong thư mục media cục bộ trên máy chủ.
@Component
public class LocalMediaLocation implements MediaLocationStrategy {

    private static final String DEFAULT_MEDIA_DIR = "media";
    private static final Path DEFAULT_REPO_MEDIA_DIR = Path.of("..", "..", DEFAULT_MEDIA_DIR);

    private final List<Path> roots;

    public LocalMediaLocation(MediaProperties properties) {
        this.roots = resolveRoots(properties, DEFAULT_REPO_MEDIA_DIR);
    }

    @Override
    public Optional<Path> locate(MediaAsset asset) {
        return locateInRoots(asset, roots);
    }

    // Thử lần lượt từng thư mục gốc ứng viên và dừng ngay ở tệp hợp lệ đầu tiên.
    static Optional<Path> locateInRoots(MediaAsset asset, List<Path> candidateRoots) {
        if (asset == null) {
            return Optional.empty();
        }
        Optional<MediaPathPolicy.ValidatedRelativePath> validated = MediaPathPolicy.parse(asset.getRelativePath());
        if (validated.isEmpty()) {
            return Optional.empty();
        }
        for (Path root : candidateRoots) {
            Optional<Path> candidate = MediaPathPolicy.resolveRegularFile(root, validated.get());
            if (candidate.isPresent()) {
                return candidate;
            }
        }
        return Optional.empty();
    }

    // Khi dùng cấu hình mặc định, mình tìm thêm cả ../../media để chạy trong backend/springboot vẫn thấy thư mục media ở gốc repo.
    static List<Path> resolveRoots(MediaProperties properties, Path repositoryMediaRoot) {
        String configuredRoot = properties == null ? null : properties.root();
        if (configuredRoot == null || configuredRoot.isBlank() || DEFAULT_MEDIA_DIR.equals(configuredRoot.trim())) {
            return List.of(Path.of(DEFAULT_MEDIA_DIR), repositoryMediaRoot);
        }
        return List.of(Path.of(configuredRoot.trim()));
    }
}
