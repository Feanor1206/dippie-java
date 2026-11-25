package com.dippie.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Kiểm tra an toàn đường dẫn video trước khi đọc ổ đĩa, chặn path traversal và symlink thoát khỏi thư mục gốc.
// Việt nhớ đừng bỏ bước kiểm tra từng đoạn bên dưới, vì chỉ chuẩn hóa chuỗi thôi không chặn được symlink trỏ ra ngoài.
final class MediaPathPolicy {

    private MediaPathPolicy() {
    }

    // Giữ đường dẫn tương đối đã qua kiểm tra cùng danh sách các đoạn đường dẫn sạch.
    record ValidatedRelativePath(String raw, List<String> segments) {

        // Cắt bỏ tiền tố thư mục (như VideoDieppie/) nếu hợp lệ, tránh tình huống cắt xong bị dính dấu / ở đầu.
        Optional<ValidatedRelativePath> stripPrefix(String prefix) {
            if (!raw.startsWith(prefix) || raw.length() <= prefix.length() || raw.charAt(prefix.length()) == '/') {
                return Optional.empty();
            }
            if (segments.size() <= 1) {
                return Optional.empty();
            }
            return Optional.of(new ValidatedRelativePath(
                    raw.substring(prefix.length()),
                    segments.subList(1, segments.size())));
        }
    }

    static boolean isValidRelativePath(String relativePath) {
        return parse(relativePath).isPresent();
    }

    // Tách đường dẫn thành từng đoạn và loại ngay dấu \, dấu :, ký tự điều khiển hoặc đoạn "..".
    static Optional<ValidatedRelativePath> parse(String relativePath) {
        if (relativePath == null || relativePath.isBlank() || relativePath.startsWith("/")) {
            return Optional.empty();
        }
        int length = relativePath.length();
        List<String> segments = new ArrayList<>();
        int start = 0;
        // Quét từng ký tự để chặn ký tự lạ trên Windows/Unix rồi cắt theo dấu /.
        for (int i = 0; i <= length; i++) {
            if (i < length) {
                char ch = relativePath.charAt(i);
                if (ch == '\\' || ch == ':' || ch < 32 || ch == 127) {
                    return Optional.empty();
                }
                if (ch != '/') {
                    continue;
                }
            }
            // Gặp ".." (kể cả có khoảng trắng đuôi trên Windows) thì loại luôn; đoạn rỗng và "." thì bỏ qua.
            int end = i;
            if (isParentReference(relativePath, start, end)) {
                return Optional.empty();
            }
            if (end > start && !(end - start == 1 && relativePath.charAt(start) == '.')) {
                segments.add(relativePath.substring(start, end));
            }
            start = i + 1;
        }
        return segments.isEmpty() ? Optional.empty() : Optional.of(new ValidatedRelativePath(relativePath, List.copyOf(segments)));
    }

    static Optional<Path> resolveRegularFile(Path root, String relativePath) {
        if (root == null) {
            return Optional.empty();
        }
        return parse(relativePath).flatMap(validated -> resolveRegularFile(root, validated));
    }

    // Đi từng cấp từ thư mục gốc xuống tệp đích, chặn mọi symlink và xác nhận đường dẫn thật vẫn nằm trong root.
    static Optional<Path> resolveRegularFile(Path root, ValidatedRelativePath validated) {
        if (root == null || validated == null || validated.segments().isEmpty()) {
            return Optional.empty();
        }
        List<String> segments = validated.segments();
        try {
            // Lấy đường dẫn thật của thư mục gốc; nếu hệ thống tệp không hỗ trợ toRealPath thì rơi về đường dẫn chuẩn hóa.
            Path baseRoot;
            boolean usedRealRoot;
            try {
                baseRoot = root.toRealPath();
                if (!Files.isDirectory(baseRoot, LinkOption.NOFOLLOW_LINKS)) {
                    return Optional.empty();
                }
                usedRealRoot = true;
            } catch (IOException ex) {
                if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                    return Optional.empty();
                }
                baseRoot = root.toAbsolutePath().normalize();
                usedRealRoot = false;
            }

            // Soi từng cấp với NOFOLLOW_LINKS: các cấp giữa phải là thư mục thật, cấp cuối phải là tệp thật.
            Path current = baseRoot;
            for (int i = 0; i < segments.size(); i++) {
                current = current.resolve(segments.get(i));
                BasicFileAttributes attributes = Files.readAttributes(
                        current,
                        BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS
                );
                if (attributes.isSymbolicLink() || attributes.isOther()) {
                    return Optional.empty();
                }
                boolean lastSegment = i == segments.size() - 1;
                if (lastSegment ? !attributes.isRegularFile() : !attributes.isDirectory()) {
                    return Optional.empty();
                }
            }

            // So sánh đường dẫn có và không đi theo link lần cuối để chắc chắn tệp không thoát ra khỏi baseRoot.
            if (usedRealRoot) {
                Path noFollow = current.toRealPath(LinkOption.NOFOLLOW_LINKS);
                Path followed = current.toRealPath();
                if (!noFollow.equals(followed) || !followed.startsWith(baseRoot) || followed.equals(baseRoot)) {
                    return Optional.empty();
                }
                return Optional.of(followed);
            }

            Path normalized = current.toAbsolutePath().normalize();
            if (!normalized.startsWith(baseRoot) || normalized.equals(baseRoot)) {
                return Optional.empty();
            }
            return Optional.of(normalized);
        } catch (IOException | InvalidPathException | SecurityException ex) {
            return Optional.empty();
        }
    }

    // Cắt khoảng trắng cuối đoạn trước khi so ".." vì Windows tự bỏ khoảng trắng đuôi khi mở tệp.
    private static boolean isParentReference(String path, int start, int end) {
        while (end > start && path.charAt(end - 1) == ' ') {
            end--;
        }
        return end - start == 2 && path.charAt(start) == '.' && path.charAt(start + 1) == '.';
    }
}
