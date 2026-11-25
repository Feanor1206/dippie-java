package com.dippie.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MediaFileResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void localProviderResolvesFromLocalRootOnly() throws IOException {
        Path localRoot = Files.createDirectories(tempDir.resolve("local"));
        Path cacheRoot = Files.createDirectories(tempDir.resolve("cache"));
        Path driveRoot = Files.createDirectories(tempDir.resolve("drive"));

        Path localFile = localRoot.resolve("synthetic/feature.mp4");
        Files.createDirectories(localFile.getParent());
        Files.writeString(localFile, "local-bytes", StandardCharsets.UTF_8);

        Path cacheFile = cacheRoot.resolve("synthetic/feature.mp4");
        Files.createDirectories(cacheFile.getParent());
        Files.writeString(cacheFile, "cache-bytes", StandardCharsets.UTF_8);

        Path driveOnlyFile = driveRoot.resolve("only-on-drive.mp4");
        Files.writeString(driveOnlyFile, "drive-bytes", StandardCharsets.UTF_8);

        MediaProperties properties = new MediaProperties(
                localRoot.toString(),
                cacheRoot.toString(),
                driveRoot.toString()
        );
        MediaFileResolver resolver = MediaFileResolver.forProperties(properties);

        MediaAsset localAsset = asset("local", "synthetic/feature.mp4", 11);
        assertThat(resolver.resolve(localAsset)).contains(localFile.toRealPath());

        MediaAsset missingLocalAsset = asset("local", "only-on-drive.mp4", 11);
        assertThat(resolver.resolve(missingLocalAsset)).isEmpty();
    }

    @Test
    void localLocationFallsBackToRepositoryMediaRootWhenUnconfigured() throws IOException {
        Path repoMedia = Files.createDirectories(tempDir.resolve("repo-media"));
        Path video = repoMedia.resolve("inside.mp4");
        Files.writeString(video, "inside", StandardCharsets.UTF_8);

        var roots = LocalMediaLocation.resolveRoots(new MediaProperties("", null, null), repoMedia);
        assertThat(LocalMediaLocation.locateInRoots(asset("local", "inside.mp4", 6), roots))
                .contains(video.toRealPath());
    }

    @Test
    void driveProviderUsesCacheWithExactSizeThenMountThenLocal() throws IOException {
        Path localRoot = Files.createDirectories(tempDir.resolve("local"));
        Path cacheRoot = Files.createDirectories(tempDir.resolve("cache"));
        Path driveRoot = Files.createDirectories(tempDir.resolve("drive"));

        String relativePath = "VideoDieppie/feature.mp4";
        Path cacheFile = cacheRoot.resolve(relativePath);
        Path driveFile = driveRoot.resolve(relativePath);
        Path localFile = localRoot.resolve(relativePath);
        Files.createDirectories(cacheFile.getParent());
        Files.createDirectories(driveFile.getParent());
        Files.createDirectories(localFile.getParent());

        Files.writeString(cacheFile, "mirror-video", StandardCharsets.UTF_8);
        Files.writeString(driveFile, "drive--video", StandardCharsets.UTF_8);
        Files.writeString(localFile, "local--video", StandardCharsets.UTF_8);

        MediaFileResolver resolver = MediaFileResolver.forProperties(new MediaProperties(
                localRoot.toString(),
                cacheRoot.toString(),
                driveRoot.toString()
        ));

        MediaAsset exactMatch = asset("drive", relativePath, "mirror-video".length());
        assertThat(resolver.resolve(exactMatch)).contains(cacheFile.toRealPath());

        MediaAsset shorterCache = asset("drive", relativePath, "mirror-video".length() + 5);
        assertThat(resolver.resolve(shorterCache)).contains(driveFile.toRealPath());

        MediaAsset zeroLength = asset("drive", relativePath, 0);
        assertThat(resolver.resolve(zeroLength)).contains(driveFile.toRealPath());

        Files.delete(driveFile);
        assertThat(resolver.resolve(shorterCache)).contains(localFile.toRealPath());
    }

    @Test
    void driveMountStripsVideoDieppiePrefixAndRejectsRootedResult() throws IOException {
        Path driveRoot = Files.createDirectories(tempDir.resolve("drive"));
        Path strippedTarget = driveRoot.resolve("series/ep1.mp4");
        Files.createDirectories(strippedTarget.getParent());
        Files.writeString(strippedTarget, "episode-data", StandardCharsets.UTF_8);

        Path outside = tempDir.resolve("outside.mp4");
        Files.writeString(outside, "secret", StandardCharsets.UTF_8);

        DriveMountLocation mount = new DriveMountLocation(new MediaProperties(null, null, driveRoot.toString()));

        assertThat(mount.locate(asset("drive", "VideoDieppie/series/ep1.mp4", 12)))
                .contains(strippedTarget.toRealPath());
        assertThat(mount.locate(asset("drive", "VideoDieppie//outside.mp4", 6))).isEmpty();
        assertThat(mount.locate(asset("drive", "VideoDieppie/../outside.mp4", 6))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "../canary.mp4",
            "..\\canary.mp4",
            "sub/../../canary.mp4",
            ".. /canary.mp4",
            "/canary.mp4",
            "C:/canary.mp4",
            "C:\\canary.mp4",
            "canary.mp4:stream",
            "sub\\inside.mp4",
            "bad\0file.mp4",
            "bad\nfile.mp4"
    })
    void rejectsTraversalAndUnsafeRelativePathsAcrossAllStrategies(String unsafePath) throws IOException {
        Path root = Files.createDirectories(tempDir.resolve("media"));
        Files.writeString(tempDir.resolve("canary.mp4"), "secret", StandardCharsets.UTF_8);
        Path sub = Files.createDirectories(root.resolve("sub"));
        Files.writeString(sub.resolve("inside.mp4"), "inside", StandardCharsets.UTF_8);

        MediaFileResolver resolver = MediaFileResolver.forProperties(new MediaProperties(
                root.toString(),
                root.toString(),
                root.toString()
        ));

        assertThat(resolver.resolve(asset("local", unsafePath, 6))).isEmpty();
        assertThat(resolver.resolve(asset("drive", unsafePath, 6))).isEmpty();
    }

    @Test
    void rejectsDirectoryTargets() throws IOException {
        Path root = Files.createDirectories(tempDir.resolve("media"));
        Files.createDirectories(root.resolve("folder"));

        MediaFileResolver resolver = MediaFileResolver.forProperties(
                new MediaProperties(root.toString(), null, null)
        );
        assertThat(resolver.resolve(asset("local", "folder", 0))).isEmpty();
    }

    @Test
    void rejectsSymlinksInsideAndOutsideRoot() throws IOException {
        Path root = Files.createDirectories(tempDir.resolve("media"));
        Path outside = tempDir.resolve("canary.mp4");
        Path inside = root.resolve("inside.mp4");
        Files.writeString(outside, "secret", StandardCharsets.UTF_8);
        Files.writeString(inside, "inside", StandardCharsets.UTF_8);

        Path outsideLink = root.resolve("link-outside.mp4");
        Path insideLink = root.resolve("link-inside.mp4");
        try {
            Files.createSymbolicLink(outsideLink, outside);
            Files.createSymbolicLink(insideLink, inside);
        } catch (IOException | UnsupportedOperationException ex) {
            assumeTrue(false, "Symlink creation not permitted in this environment");
            return;
        }

        MediaFileResolver resolver = MediaFileResolver.forProperties(
                new MediaProperties(root.toString(), root.toString(), root.toString())
        );

        assertThat(resolver.resolve(asset("local", "link-outside.mp4", 6))).isEmpty();
        assertThat(resolver.resolve(asset("local", "link-inside.mp4", 6))).isEmpty();
        assertThat(resolver.resolve(asset("drive", "link-outside.mp4", 6))).isEmpty();
        assertThat(resolver.resolve(asset("drive", "link-inside.mp4", 6))).isEmpty();
    }

    private static MediaAsset asset(String provider, String relativePath, long byteLength) {
        return new MediaAsset(
                "ast-test",
                "movie-1",
                "feature",
                provider,
                null,
                relativePath,
                "v1",
                "video/mp4",
                byteLength,
                10.0,
                true,
                true
        );
    }
}
