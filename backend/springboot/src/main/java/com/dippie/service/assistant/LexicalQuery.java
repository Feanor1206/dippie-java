package com.dippie.service.assistant;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

// Tập từ khóa đã lọc sạch cụm hỏi khuôn mẫu và từ dừng để chấm điểm BM25.
// Cờ requiresAllWords bật lên với câu hỏi có/không tên phim hoặc câu hỏi ngoài lề để tránh khớp nhầm lác đác một hai từ.
public record LexicalQuery(List<String> words, Set<String> distinctWords, boolean requiresAllWords) {

    private static final Set<String> STOPWORDS = Set.of(
            "ai", "ay", "ban", "bao", "be", "bo", "cac", "cho", "co", "cua", "cung", "da", "dang", "de", "di", "duoc",
            "gi", "hay", "hoac", "khong", "la", "lai", "mot", "minh", "nao", "nhung", "nay", "nhe",
            "nhieu", "no", "nua", "o", "phim", "tap", "episode", "scene", "canh", "doan", "sao", "thi", "tim", "toi",
            "trong", "tu", "va", "vai", "ve", "voi", "xem", "you", "the", "of", "a", "or");

    private static final Pattern BOILERPLATE = Pattern.compile(
            "\\b(?:hom nay|the nao|bao nhieu (?:tap|mua|phan)|may (?:tap|mua|phan)|so luong (?:tap|mua|phan)"
                    + "|nhan vat chinh|nhan vat|gom ai|gioi thieu|noi dung cua|noi ve gi|tom tat|goi y|recommend|liet ke|danh sach|tat ca|toan bo"
                    + "|noi bat|truoc gio ngu|(?:xem )?truoc khi ngu|nhe nhang|ngan(?: hon)?"
                    + "|(?:tap|phim|bo phim) khac"
                    + "|(?:cho |danh cho )?(?:be|tre)(?: \\d{1,2} tuoi)?|(?:tre )?mam non"
                    + "|(?:duoi|it hon|toi da|khong qua)\\s+\\d{1,3}\\s*(?:phut|min)"
                    + "|(?:phut|giay) (?:nao|may|thu may|bao nhieu)|moc thoi gian|timecode|timestamp"
                    + "|(?:mua|phan|season|tap|ep|episode)\\s*#?\\s*\\d+|s\\d+e\\d+)\\b");
    private static final Pattern TITLE_QUESTION = Pattern.compile("\\bco (?:bo )?phim (?!nao\\b).+ khong[.!? ]*$");
    private static final Pattern PROCEDURAL_QUESTION = Pattern.compile(
            "(?:^|\\b)(?:(?:huong dan|chi)(?: cho)? (?:(?:minh|toi|em) )?)?(?:cach|lam the nao(?: de)?|lam sao(?: de)?|cong thuc) "
                    + "|\\bnhu the nao[.!? ]*$"
                    + "|\\b(?:thu do|dan so|dien tich|tong thong|thu tuong|ty gia|chung khoan|bitcoin|may gio roi|bay gio la may gio|hom nay(?: la)? thu may)\\b");

    // Bóc bỏ câu dẫn và từ dừng tiếng Việt; giữ lại từ "ban" khi câu đang hỏi về tình bạn để không mất ý chính.
    public static LexicalQuery from(String source) {
        String folded = LexicalIndex.normalize(source);
        boolean friendship = folded.contains("tinh ban") || folded.contains("ban than") || folded.contains("ban be");
        String content = BOILERPLATE.matcher(folded).replaceAll(" ");
        List<String> words = LexicalIndex.tokenize(content).stream()
                .filter(word -> !STOPWORDS.contains(word) || (friendship && "ban".equals(word)))
                .filter(word -> word.codePoints().anyMatch(Character::isLetter)).toList();
        boolean requiresAllWords = TITLE_QUESTION.matcher(folded).find() || PROCEDURAL_QUESTION.matcher(folded).find();
        return new LexicalQuery(words, Set.copyOf(words), requiresAllWords);
    }

    public boolean isEmpty() {
        return distinctWords.isEmpty();
    }

    // Yêu cầu đạt tối thiểu 3/5 số từ (hoặc đủ 100% khi requiresAllWords bật); riêng truy vấn 2 từ phải khớp cụm hoặc tên phim mới nhận.
    public boolean relevant(int matches, float score, boolean hasPhraseOrName) {
        int size = distinctWords.size();
        int minimum = requiresAllWords ? size : Math.ceilDiv(size * 3, 5);
        return score >= 0.5f && matches >= minimum && (size != 2 || hasPhraseOrName);
    }
}
