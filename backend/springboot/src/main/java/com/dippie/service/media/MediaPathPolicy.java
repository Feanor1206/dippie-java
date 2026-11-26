package com.dippie.service.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// check an toàn đường dẫn video trước khi đọc đĩa, chặn path traversal với symlink trỏ ra ngoài root
// Việt đừng bỏ bước soi từng đoạn bên dưới nha, chỉ normalize chuỗi thôi là không chặn nổi symlink thoát ra ngoài đâu
public final class MediaPathPolicy {

    private MediaPathPolicy() {
    }

    // giữ đường dẫn tương đối đã duyệt kèm list các đoạn sạch
    public record ValidatedRelativePath(String raw, List<String> segments) {

        // cắt tiền tố thư mục (như VideoDieppie/) nếu hợp lệ, tránh cắt xong bị dư dấu / ở đầu
        public Optional<ValidatedRelativePath> stripPrefix(String prefix) {
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

    public static boolean isValidRelativePath(String relativePath) {
        return parse(relativePath).isPresent();
    }

    // tách từng đoạn đường dẫn, gặp \, :, ký tự điều khiển hay ".." là loại ngay
    public static Optional<ValidatedRelativePath> parse(String relativePath) {
        if (relativePath == null || relativePath.isBlank() || relativePath.startsWith("/")) {
            return Optional.empty();
        }
        int length = relativePath.length();
        List<String> segments = new ArrayList<>();
        int start = 0;
        // quét từng ký tự chặn ký tự lạ trên Windows/Unix rồi cắt theo dấu /
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
            // gặp ".." (kể cả dính khoảng trắng đuôi trên Windows) là loại luôn, còn đoạn rỗng với "." thì bỏ qua
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

    public static Optional<Path> resolveRegularFile(Path root, String relativePath) {
        if (root == null) {
            return Optional.empty();
        }
        return parse(relativePath).flatMap(validated -> resolveRegularFile(root, validated));
    }

    // dò từng cấp từ root xuống file đích, chặn sạch symlink và chốt lại đường dẫn thật vẫn nằm trong root
    public static Optional<Path> resolveRegularFile(Path root, ValidatedRelativePath validated) {
        if (root == null || validated == null || validated.segments().isEmpty()) {
            return Optional.empty();
        }
        List<String> segments = validated.segments();
        try {
            // lấy đường dẫn thật của root, ổ nào không hỗ trợ toRealPath thì lui về bản normalize
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

            // soi từng cấp bằng NOFOLLOW_LINKS, mấy cấp giữa phải là thư mục thật còn cấp cuối phải là file thật
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

            // so lại bản có với không theo link lần chót cho chắc file không trồi khỏi baseRoot
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

    // gọt khoảng trắng đuôi trước khi so ".." vì Windows tự cắt khoảng trắng cuối lúc mở file
    private static boolean isParentReference(String path, int start, int end) {
        while (end > start && path.charAt(end - 1) == ' ') {
            end--;
        }
        return end - start == 2 && path.charAt(start) == '.' && path.charAt(start + 1) == '.';
    }
}
