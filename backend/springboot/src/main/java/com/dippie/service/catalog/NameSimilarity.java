package com.dippie.service.catalog;

import com.dippie.common.text.TextFolding;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

// bộ so khớp gần đúng tên với cụm tìm kiếm theo Damerau-Levenshtein, chịu được gõ sai, thiếu, thừa với đảo 2 chữ cạnh nhau
public final class NameSimilarity {

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    private NameSimilarity() {
    }

    // bỏ dấu rồi cắt chuỗi ra từng từ chỉ gồm chữ với số
    public static List<String> words(String text) {
        return WORD.matcher(TextFolding.fold(text)).results().map(MatchResult::group).toList();
    }

    // trượt cửa sổ trên chuỗi dài hơn để tìm chỗ căn khớp có tổng khoảng cách nhỏ nhất, không đoạn nào đạt thì trả -1
    public static int phraseDistance(List<String> query, List<String> name, Predicate<String> allowShortFuzzy) {
        int size = Math.min(query.size(), name.size());
        if (size == 0) {
            return -1;
        }
        // dò từng vị trí trượt, đụng đoạn khớp y hệt (distance == 0) là thoát sớm luôn cho nhanh
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

    // khác phraseDistance ở chỗ cụm chủ đề phải nằm gọn trong danh sách từ đích và có ít nhất 1 từ từ 4 ký tự trở lên kẻo khớp bừa
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

    // cộng dồn khoảng cách từng cặp từ trong 1 cửa sổ, hễ 1 từ vượt ngưỡng là loại cả cửa sổ luôn
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

    // đo khoảng cách 2 từ lẻ, từ 3 chữ chỉ đảo 2 chữ cạnh khi allowShortFuzzy đồng ý, 4 tới 7 chữ lệch tối đa 1, từ 8 trở lên tối đa 2
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

    // siết chặt hơn wordDistance cho từ khóa chủ đề kẻo bắt nhầm mấy từ ngắn na ná nhau
    public static int topicWordDistance(String query, String target, boolean allowShortDeletion,
            Predicate<String> allowShortFuzzy) {
        if (query.equals(target)) {
            return 0;
        }
        int queryLength = query.codePointCount(0, query.length());
        int targetLength = target.codePointCount(0, target.length());
        // từ 3 ký tự thì hoặc đảo 2 chữ cạnh nhau, hoặc thiếu 1 chữ so với từ 4 ký tự khi trùng chữ cái đầu
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
        // hai từ đều 4 ký tự thì phải trùng chữ cái đầu và lệch tối đa 1 phép sửa
        if (queryLength == 4 && targetLength == 4) {
            return query.codePointAt(0) == target.codePointAt(0)
                    ? editDistance(query.codePoints().toArray(), target.codePoints().toArray(), 1) : -1;
        }
        // từ dài hơn thì lấy ngưỡng 1 (tới 7 ký tự) hoặc 2 (từ 8 ký tự trở lên)
        int limit = queryLength <= 7 ? 1 : 2;
        if (queryLength < 4 || targetLength < 4 || Math.abs(queryLength - targetLength) > limit) {
            return -1;
        }
        return editDistance(query.codePoints().toArray(), target.codePoints().toArray(), limit);
    }

    // check xem 2 từ cùng độ dài có chỉ khác nhau đúng 1 cặp chữ đứng cạnh bị đảo chỗ không
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

    // quy hoạch động Damerau-Levenshtein chỉ giữ 3 hàng cho nhẹ RAM, quá limit thì trả -1
    private static int editDistance(int[] query, int[] name, int limit) {
        int[] beforePrevious = new int[name.length + 1];
        int[] previous = new int[name.length + 1];
        int[] current = new int[name.length + 1];
        for (int column = 0; column < previous.length; column++) {
            previous[column] = column;
        }
        // tính đủ 4 phép sửa gồm thay, xóa, thêm với đảo 2 chữ cạnh nhau
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
            // cả hàng đều vượt limit thì cắt sớm luôn khỏi tính mấy hàng sau
            if (minimum > limit) {
                return -1;
            }
            // xoay vòng 3 mảng hàng cho đỡ cấp phát mảng mới ở mỗi vòng lặp
            int[] recycled = beforePrevious;
            beforePrevious = previous;
            previous = current;
            current = recycled;
        }
        return previous[name.length] <= limit ? previous[name.length] : -1;
    }
}
