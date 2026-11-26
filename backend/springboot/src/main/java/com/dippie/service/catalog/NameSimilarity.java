package com.dippie.service.catalog;

import com.dippie.common.text.TextFolding;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

// Bộ so khớp gần đúng tên và cụm từ tìm kiếm theo khoảng cách Damerau-Levenshtein (chịu lỗi gõ sai, thiếu, thừa và đảo hai chữ cạnh nhau).
public final class NameSimilarity {

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    private NameSimilarity() {
    }

    // Bỏ dấu rồi tách văn bản thành từng từ chỉ gồm chữ và số.
    public static List<String> words(String text) {
        return WORD.matcher(TextFolding.fold(text)).results().map(MatchResult::group).toList();
    }

    // Trượt cửa sổ trên chuỗi từ dài hơn để tìm vị trí căn khớp có tổng khoảng cách sửa đổi nhỏ nhất; trả -1 nếu không đoạn nào đạt ngưỡng.
    public static int phraseDistance(List<String> query, List<String> name, Predicate<String> allowShortFuzzy) {
        int size = Math.min(query.size(), name.size());
        if (size == 0) {
            return -1;
        }
        // Dò lần lượt từng vị trí trượt; gặp đoạn khớp y hệt (distance == 0) thì thoát sớm luôn.
        int best = Integer.MAX_VALUE;
        int span = Math.abs(query.size() - name.size());
        for (int offset = 0; offset <= span; offset++) {
            int queryStart = query.size() > name.size() ? offset : 0;
            int nameStart = query.size() < name.size() ? offset : 0;
            int distance = windowDistance(query, queryStart, name, nameStart, size, allowShortFuzzy, false);
            if (distance == 0) {
                return 0;
            }
            if (distance > 0) {
                best = Math.min(best, distance);
            }
        }
        return best == Integer.MAX_VALUE ? -1 : best;
    }

    // Khác phraseDistance ở chỗ cụm chủ đề phải nằm gọn trong danh sách từ đích và có ít nhất một từ từ 4 ký tự trở lên để tránh khớp bừa.
    public static int topicPhraseDistance(List<String> query, List<String> target, Predicate<String> allowShortFuzzy) {
        if (query.isEmpty() || target.size() < query.size()
                || query.stream().noneMatch(word -> word.codePointCount(0, word.length()) >= 4)) {
            return -1;
        }
        int best = Integer.MAX_VALUE;
        int span = target.size() - query.size();
        for (int offset = 0; offset <= span; offset++) {
            int distance = windowDistance(query, 0, target, offset, query.size(), allowShortFuzzy, true);
            if (distance == 0) {
                return 0;
            }
            if (distance > 0) {
                best = Math.min(best, distance);
            }
        }
        return best == Integer.MAX_VALUE ? -1 : best;
    }

    // Cộng dồn khoảng cách từng cặp từ trong một vị trí cửa sổ; chỉ cần một từ vượt ngưỡng là loại cả cửa sổ đó.
    private static int windowDistance(List<String> query, int queryStart, List<String> name, int nameStart, int size,
            Predicate<String> allowShortFuzzy, boolean topic) {
        int total = 0;
        boolean allowShortDeletion = topic && size >= 2;
        for (int index = 0; index < size; index++) {
            String queryWord = query.get(queryStart + index);
            String targetWord = name.get(nameStart + index);
            int distance = topic
                    ? topicWordDistance(queryWord, targetWord, allowShortDeletion, allowShortFuzzy)
                    : wordDistance(queryWord, targetWord, allowShortFuzzy);
            if (distance < 0) {
                return -1;
            }
            total += distance;
        }
        return total;
    }

    // Tính khoảng cách giữa hai từ lẻ: từ 3 ký tự chỉ cho đảo hai chữ cạnh nhau khi allowShortFuzzy đồng ý, từ 4 đến 7 ký tự lệch tối đa 1, từ 8 ký tự trở lên tối đa 2.
    public static int wordDistance(String query, String name, Predicate<String> allowShortFuzzy) {
        if (query.equals(name)) {
            return 0;
        }
        int queryLength = query.codePointCount(0, query.length());
        int nameLength = name.codePointCount(0, name.length());
        if (queryLength == 3 && nameLength == 3) {
            if (!allowShortFuzzy.test(query)) {
                return -1;
            }
            return isAdjacentTransposition(query.codePoints().toArray(), name.codePoints().toArray()) ? 1 : -1;
        }
        int limit = queryLength <= 7 ? 1 : 2;
        if (queryLength < 4 || nameLength < 4 || Math.abs(queryLength - nameLength) > limit) {
            return -1;
        }
        return editDistance(query.codePoints().toArray(), name.codePoints().toArray(), limit);
    }

    // Siết chặt hơn wordDistance cho từ khóa chủ đề để đỡ nhận nhầm các từ ngắn gần giống nhau.
    public static int topicWordDistance(String query, String target, boolean allowShortDeletion,
            Predicate<String> allowShortFuzzy) {
        if (query.equals(target)) {
            return 0;
        }
        int queryLength = query.codePointCount(0, query.length());
        int targetLength = target.codePointCount(0, target.length());
        // Từ 3 ký tự: hoặc đảo hai chữ cạnh nhau, hoặc thiếu 1 ký tự so với từ 4 ký tự khi trùng chữ cái đầu.
        if (queryLength == 3) {
            if (!allowShortFuzzy.test(query)) {
                return -1;
            }
            if (targetLength == 3) {
                return isAdjacentTransposition(query.codePoints().toArray(), target.codePoints().toArray()) ? 1 : -1;
            }
            if (allowShortDeletion && targetLength == 4 && query.codePointAt(0) == target.codePointAt(0)) {
                return editDistance(query.codePoints().toArray(), target.codePoints().toArray(), 1);
            }
            return -1;
        }
        // Hai từ cùng 4 ký tự phải trùng chữ cái đầu và lệch không quá 1 thao tác.
        if (queryLength == 4 && targetLength == 4) {
            return query.codePointAt(0) == target.codePointAt(0)
                    ? editDistance(query.codePoints().toArray(), target.codePoints().toArray(), 1) : -1;
        }
        // Từ dài hơn dùng ngưỡng sai số 1 (tới 7 ký tự) hoặc 2 (từ 8 ký tự trở lên).
        int limit = queryLength <= 7 ? 1 : 2;
        if (queryLength < 4 || targetLength < 4 || Math.abs(queryLength - targetLength) > limit) {
            return -1;
        }
        return editDistance(query.codePoints().toArray(), target.codePoints().toArray(), limit);
    }

    // Kiểm tra hai từ cùng độ dài có khác nhau đúng một cặp ký tự liền kề bị đổi chỗ hay không.
    private static boolean isAdjacentTransposition(int[] first, int[] second) {
        if (first.length != second.length) {
            return false;
        }
        int mismatch = -1;
        for (int index = 0; index < first.length; index++) {
            if (first[index] != second[index]) {
                if (mismatch >= 0 || index + 1 >= first.length
                        || first[index] != second[index + 1] || first[index + 1] != second[index]) {
                    return false;
                }
                mismatch = index;
                index++;
            }
        }
        return mismatch >= 0;
    }

    // Quy hoạch động Damerau-Levenshtein chỉ giữ 3 hàng để tiết kiệm bộ nhớ; trả -1 nếu vượt quá limit.
    private static int editDistance(int[] query, int[] name, int limit) {
        int[] beforePrevious = new int[name.length + 1];
        int[] previous = new int[name.length + 1];
        int[] current = new int[name.length + 1];
        for (int column = 0; column < previous.length; column++) {
            previous[column] = column;
        }
        // Tính 4 phép sửa: thay thế, xóa, thêm và đảo hai ký tự đứng liền nhau.
        for (int row = 1; row <= query.length; row++) {
            current[0] = row;
            int minimum = row;
            for (int column = 1; column <= name.length; column++) {
                int cost = query[row - 1] == name[column - 1] ? 0 : 1;
                int best = Math.min(previous[column - 1] + cost,
                        Math.min(previous[column] + 1, current[column - 1] + 1));
                if (row > 1 && column > 1
                        && query[row - 1] == name[column - 2] && query[row - 2] == name[column - 1]) {
                    best = Math.min(best, beforePrevious[column - 2] + 1);
                }
                current[column] = best;
                minimum = Math.min(minimum, best);
            }
            // Cả hàng đều lớn hơn limit thì dừng luôn khỏi tính mấy hàng sau.
            if (minimum > limit) {
                return -1;
            }
            // Xoay vòng 3 mảng hàng thay vì cấp phát mảng mới ở mỗi vòng lặp.
            int[] recycled = beforePrevious;
            beforePrevious = previous;
            previous = current;
            current = recycled;
        }
        return previous[name.length] <= limit ? previous[name.length] : -1;
    }
}
