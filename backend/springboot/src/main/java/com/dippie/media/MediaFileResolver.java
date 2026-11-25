package com.dippie.media;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MediaFileResolver {

    private static final String DRIVE_PROVIDER = "drive";

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

    public static MediaFileResolver forProperties(MediaProperties properties) {
        return new MediaFileResolver(
                new DriveCacheLocation(properties),
                new DriveMountLocation(properties),
                new LocalMediaLocation(properties)
        );
    }

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
}
