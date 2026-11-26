package com.dippie.service.media;

import com.dippie.model.media.MediaAsset;

import java.nio.file.Path;
import java.util.Optional;

// kiểu Strategy chung cho các nguồn tìm video (local, cache Drive, ổ Drive gắn kết)
public interface MediaLocationStrategy {

    Optional<Path> locate(MediaAsset asset);
}
