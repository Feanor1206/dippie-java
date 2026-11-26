package com.dippie.service.media;

import com.dippie.config.media.MediaProperties;
import com.dippie.model.media.MediaAsset;

import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

// Strategy tìm video trên ổ Drive gắn kết (như X: trên Windows).
// Một số máy gắn thẳng vào thư mục con nên nếu tìm nguyên đường dẫn không thấy thì thử bỏ tiền tố VideoDieppie/.
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
        Optional<MediaPathPolicy.ValidatedRelativePath> validated = MediaPathPolicy.parse(asset.getRelativePath());
        if (validated.isEmpty()) {
            return Optional.empty();
        }
        // Trên Windows phải kiểm tra ổ đĩa đã thực sự gắn chưa trước khi đọc tệp bên trong.
        if (File.separatorChar == '\\' && driveRoot.equals(driveRoot.getRoot())
                && driveRoot.toString().charAt(1) == ':'
                && !Arrays.asList(File.listRoots()).contains(driveRoot.toFile())) {
            return Optional.empty();
        }
        // Tìm nguyên đường dẫn trước, không thấy mới thử cắt tiền tố thư mục gốc trên Drive.
        Optional<Path> direct = MediaPathPolicy.resolveRegularFile(driveRoot, validated.get());
        if (direct.isPresent()) {
            return direct;
        }
        return validated.get().stripPrefix(DRIVE_PREFIX)
                .flatMap(stripped -> MediaPathPolicy.resolveRegularFile(driveRoot, stripped));
    }

    // Thêm dấu / sau tên ổ kiểu "X:" để Java hiểu là gốc ổ đĩa chứ không phải thư mục hiện hành của ổ đó.
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
