package com.dippie.media;

import java.nio.file.Path;
import org.springframework.http.MediaType;

public record ResolvedMediaStream(Path path, long totalLength, MediaType mediaType) {
}
