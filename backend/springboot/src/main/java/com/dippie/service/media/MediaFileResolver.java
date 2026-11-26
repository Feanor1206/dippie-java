package com.dippie.service.media;

import com.dippie.config.media.MediaProperties;
import com.dippie.exception.NotFoundException;
import com.dippie.model.media.MediaAsset;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

// điều phối 3 Strategy tìm file video tùy theo storageProvider của MediaAsset
// nguồn drive thì dò cache máy, ổ Drive gắn kết rồi mới tới local, còn nguồn local chỉ tìm trong local
@Component
public class MediaFileResolver {

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

    // tạo nhanh resolver trong unit test hay chỗ không chạy qua Spring
    public static MediaFileResolver forProperties(MediaProperties properties) {
        return new MediaFileResolver(
                new DriveCacheLocation(properties),
                new DriveMountLocation(properties),
                new LocalMediaLocation(properties)
        );
    }

    // chặn đường dẫn bẩn ngay từ đầu rồi chạy lần lượt các Strategy của nguồn đó
    public Optional<Path> resolve(MediaAsset asset) {
        if (asset == null || !MediaPathPolicy.isValidRelativePath(asset.getRelativePath())) {
            return Optional.empty();
        }
        List<MediaLocationStrategy> strategies = "drive".equalsIgnoreCase(asset.getStorageProvider())
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
