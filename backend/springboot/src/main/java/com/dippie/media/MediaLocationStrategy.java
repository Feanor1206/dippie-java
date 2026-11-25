package com.dippie.media;

import java.nio.file.Path;
import java.util.Optional;

// Interface Strategy chung cho từng cách tìm tệp video (local, cache Drive, ổ Drive gắn kết).
public interface MediaLocationStrategy {

    Optional<Path> locate(MediaAsset asset);
}
