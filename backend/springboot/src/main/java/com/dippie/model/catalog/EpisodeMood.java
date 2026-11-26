package com.dippie.model.catalog;

import com.dippie.common.text.TextFolding;

import java.util.List;
import java.util.regex.Pattern;

// nhận diện nhu cầu xem (đi ngủ, cho bé, tập ngắn) với chấm độ êm dịu của tập bằng regex không dấu
public final class EpisodeMood {

    // từ khóa viết không dấu hết để khớp trên chuỗi đã qua TextFolding.fold
    private static final Pattern COMPILATION = phrase(
            "tap hop|tong hop|tuyen tap|bo suu tap|compilations?|\\d+\\s*(?:hours?|hrs?|gio)");
    private static final Pattern BEDTIME = phrase("truoc khi ngu|truoc gio ngu|gio di ngu|ru ngu|bedtime");
    private static final Pattern CHILD = phrase("cho (?:be|tre)|(?:be|tre) \\d{1,2} tuoi|mam non|preschool");
    private static final Pattern SHORT = phrase("tap ngan|phim ngan|ngan hon|ngan|short");
    private static final Pattern CALM = phrase(
            "nhe nhang|em diu|yen binh|am ap|thu gian|di ngu|chuc ngu ngon|bedtime|calm|gentle|preschool|mam non"
                    + "|doc sach|truyen co tich|ke chuyen|vuon hoa|lam vuon|cham cay|cham soc dong vat|sinh hoat"
                    + "|picnic|painting|drawing");
    private static final Pattern INTENSE = phrase(
            "mat ngu|khong ngu duoc|dang so|kinh di|ac mong|nightmare|scary|thunderstorms?|giong bao|sam set"
                    + "|mister skinnylegs|con nhen|slapstick");

    private EpisodeMood() {
    }

    public static boolean bedtimeRequest(String query) {
        return BEDTIME.matcher(normalized(query)).find();
    }

    public static boolean childRequest(String query) {
        return CHILD.matcher(normalized(query)).find();
    }

    public static boolean shortRequest(String query) {
        return SHORT.matcher(normalized(query)).find();
    }

    // ghép tiêu đề, mô tả, từ khóa với chủ đề rồi bỏ dấu sẵn một lượt cho mấy hàm *Folded xài lại
    public static String normalizeDetails(String title, String description, List<String> keywords, List<String> themes) {
        return foldDetails(title, description, keywords, themes);
    }

    // bắt mấy video kiểu tuyển tập dài nhiều tiếng chứ không phải tập lẻ
    public static boolean compilation(String title, List<String> keywords, List<String> themes) {
        return compilationFolded(foldDetails(title, null, keywords, themes));
    }

    public static boolean compilationFolded(String foldedDetails) {
        return COMPILATION.matcher(foldedDetails).find();
    }

    // lọc mấy tập có yếu tố rùng rợn, giông bão hay ác mộng để né lúc gợi ý phim nhẹ nhàng
    public static boolean intense(String title, String description, List<String> keywords, List<String> themes) {
        return intenseFolded(foldDetails(title, description, keywords, themes));
    }

    public static boolean intenseFolded(String foldedDetails) {
        return INTENSE.matcher(foldedDetails).find();
    }

    public static int calmScore(String seriesId, String title, String description, List<String> keywords,
            List<String> themes) {
        return calmScoreFolded(seriesId, foldDetails(title, description, keywords, themes));
    }

    // cộng điểm nền cho mấy bộ vốn êm dịu (Peppa Pig +6, Jadoo hay Ori +4), có thêm từ khóa nhẹ nhàng thì +4 nữa
    public static int calmScoreFolded(String seriesId, String foldedDetails) {
        int seriesScore = switch (seriesId == null ? "" : seriesId) {
            case "ser_peppa_pig" -> 6;
            case "ser_jadoo", "ser_ori" -> 4;
            default -> 0;
        };
        return seriesScore + (CALM.matcher(foldedDetails).find() ? 4 : 0);
    }

    private static String foldDetails(String title, String description, List<String> keywords, List<String> themes) {
        return normalized(String.join(" ", title == null ? "" : title, description == null ? "" : description,
                keywords == null ? "" : String.join(" ", keywords), themes == null ? "" : String.join(" ", themes)));
    }

    // bỏ dấu xong đổi '_' với '-' sang khoảng trắng để biên từ \b trong regex bắt đúng
    private static String normalized(String text) {
        return TextFolding.collapseWhitespace(TextFolding.fold(text).replace('_', ' ').replace('-', ' '));
    }

    private static Pattern phrase(String alternatives) {
        return Pattern.compile("\\b(?:" + alternatives + ")\\b");
    }
}
