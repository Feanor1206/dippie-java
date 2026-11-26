package com.dippie.service.media;

import com.dippie.config.media.MediaProperties;
import com.dippie.exception.NotFoundException;
import com.dippie.model.media.MediaAsset;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

// Điều phối ba Strategy tìm tệp video tùy theo storageProvider của MediaAsset.
// Với nguồn drive thì dò lần lượt: bản cache cục bộ, ổ Drive gắn kết, rồi mới tới thư mục local; nguồn local chỉ tìm trong thư mục local.
@Component
public class MediaFileResolver {

    private static final String DRIVE_PROVIDER = "drive";
    public static final String MISSING_FILE_MESSAGE = "Không tìm thấy video để phát.";

    private final List<MediaLocationStrategy> driveStrategies;
    private final List<MediaLocationStrategy> localStrategies;

    public MediaFileResolver(
            DriveCacheLocation driveCacheLocation,
            DriveMountLocation driveMountLocation,
            LocalMediaLocation localMediaLocation
    ) {
        this.driveStrategies = List.of(driveCacheLocation, driveMountLocation, localMediaLocation);
        this.localStrategies = List.of(localMediaLocation);
    }

    // Dùng khi tạo resolver trực tiếp trong unit test hoặc chỗ không qua Spring container.
    public static MediaFileResolver forProperties(MediaProperties properties) {
        return new MediaFileResolver(
                new DriveCacheLocation(properties),
                new DriveMountLocation(properties),
                new LocalMediaLocation(properties)
        );
    }

    // Chặn đường dẫn xấu ngay từ đầu rồi chạy lần lượt các Strategy của nguồn tương ứng.
    public Optional<Path> resolve(MediaAsset asset) {
        if (asset == null || !MediaPathPolicy.isValidRelativePath(asset.getRelativePath())) {
            return Optional.empty();
        }
        List<MediaLocationStrategy> strategies = DRIVE_PROVIDER.equalsIgnoreCase(asset.getStorageProvider())
                ? driveStrategies
                : localStrategies;
        for (MediaLocationStrategy strategy : strategies) {
            Optional<Path> found = strategy.locate(asset);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    public Path requireFile(MediaAsset asset) {
        return resolve(asset).orElseThrow(() -> new NotFoundException(MISSING_FILE_MESSAGE));
    }
}
