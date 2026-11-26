package com.dippie.service.media;

import com.dippie.config.media.MediaProperties;
import com.dippie.model.media.MediaAsset;

import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

// nhánh Strategy tìm video trên ổ Drive mount sẵn (kiểu ổ X: trên Windows)
// mấy máy mount thẳng vào thư mục con thì thử bỏ tiền tố VideoDieppie/ nếu tìm đường dẫn gốc không thấy
@Component
public class DriveMountLocation implements MediaLocationStrategy {

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
        // trên Windows phải check ổ đã mount thật chưa trước khi đụng file bên trong
        if (File.separatorChar == '\\' && driveRoot.equals(driveRoot.getRoot())
                && driveRoot.toString().charAt(1) == ':'
                && !Arrays.asList(File.listRoots()).contains(driveRoot.toFile())) {
            return Optional.empty();
        }
        // tìm nguyên đường dẫn trước, không thấy mới cắt tiền tố thư mục gốc trên Drive
        Optional<Path> direct = MediaPathPolicy.resolveRegularFile(driveRoot, validated.get());
        if (direct.isPresent()) {
            return direct;
        }
        return validated.get().stripPrefix("VideoDieppie/")
                .flatMap(stripped -> MediaPathPolicy.resolveRegularFile(driveRoot, stripped));
    }

    // gắn thêm "/" sau kiểu "X:" để Java hiểu là gốc ổ đĩa chứ không phải thư mục hiện tại của ổ đó
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
