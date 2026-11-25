package com.dippie.media;

import java.nio.file.Path;
import java.util.Optional;

public interface MediaLocationStrategy {

    Optional<Path> locate(MediaAsset asset);
}
