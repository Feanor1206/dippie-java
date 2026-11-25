package com.dippie.assistant.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IntentParser {

    private static final int FLAGS = Pattern.UNICODE_CHARACTER_CLASS;

    private static final Pattern PATCH_RE = Pattern.compile(
            "^(?:khong|bo |loai |them |chi |it |nhieu |con |doi |tranh |tru |only |without |exclude |instead |before |after |canh sau do\\b)",
            FLAGS
    );
    private static final Pattern YEAR_RE = Pattern.compile(
            "(truoc|sau|tu|den|nam|before|after)\\s*(?:nam\\s*)?(18\\d{2}|19\\d{2}|20\\d{2}|21\\d{2})\\b",
            FLAGS
    );
    private static final Pattern COUNT_RE = Pattern.compile(
            "(?:\\btop\\s*|\\bchi\\s+)(\\d{1,3}|mot)\\b"
                    + "|(?<!\\btap )(?<!\\bmua )(?<!\\bphan )(?<!\\bso )"
                    + "\\b(\\d{1,3}|mot)\\s+(?:tap|canh|doan|ket qua|(?:bo )?phim|goi y)\\b",
            FLAGS
    );
    private static final Pattern LIST_HINT_RE = Pattern.compile(
            "(?:^|\\btim\\s+)(?:cac|nhung|(?:mot )?vai|nhieu)\\s+(?:tap|phim|canh|doan)\\b"
                    + "|\\b(?:liet ke|danh sach)\\s+(?:(?:cac|nhung)\\s+)?(?:tap|phim|canh|doan)\\b",
            FLAGS
    );
    private static final Pattern CHITCHAT_RE = Pattern.compile(
            "(?:xin chao|chao|hi|hello|cam on|thanks|ban la ai)[.!?\\s]*",
            FLAGS
    );
    private static final Pattern EXCLUDE_PREFIX_RE = Pattern.compile(
            "(?:khong|tranh|bo|loai|tru|without|exclude)[^,.!?;]{0,32}$",
            FLAGS
    );
    private static final Pattern RECOMMEND_HINT_RE = Pattern.compile("goi y|recommend", FLAGS);
    private static final Pattern MOMENT_HINT_RE = Pattern.compile("\\b(?:canh|doan|scene|moment)\\b", FLAGS);
    private static final Pattern MOOD_RE = Pattern.compile(
            "\\b(?:vui|buon|hai huoc|giai tri|thu gian|stress|cang thang|cheerful|funny|sad)\\b",
            FLAGS
    );
    private static final Pattern EXACT_EPISODE_RE = Pattern.compile(
            "\\b(?:(?:tap|ep|episode|mua|phan|season)\\s*#?\\s*\\d+|s\\d+e\\d+)\\b",
            FLAGS
    );
    private static final Pattern SERIES_COUNT_RE = Pattern.compile(
            "\\b(?:bao nhieu|may|so luong)\\s+(tap|mua|phan)\\b",
            FLAGS
    );
    private static final Pattern SERIES_OVERVIEW_RE = Pattern.compile(
            "\\b(?:la (?:phim |bo phim )?gi|noi ve (?:gi|cai gi)|gioi thieu|noi dung cua)\\b",
            FLAGS
    );
    private static final Pattern SERIES_CHARACTERS_RE = Pattern.compile(
            "\\bnhan vat chinh\\b|\\bnhan vat\\b.*\\b(?:la ai|gom ai|co ai|nhung ai)\\b",
            FLAGS
    );
    private static final Pattern TITLE_HINT_RE = Pattern.compile("\\b(?:ten|tua de)\\b", FLAGS);
    private static final Pattern SAD_TARGET_RE = Pattern.compile("\\b(?:phim|tap|bo phim)\\s+buon\\b", FLAGS);

    private static final Map<String, Pattern> GENRE_PATTERNS = buildGenrePatterns();

    private static Map<String, Pattern> buildGenrePatterns() {
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        aliases.put("animation", List.of("hoat hinh", "animation", "cartoon"));
        aliases.put("comedy", List.of("hai huoc", "phim hai", "comedy"));
        aliases.put("horror", List.of("kinh di", "horror"));
        aliases.put("sci-fi", List.of("vien tuong", "sci-fi", "science fiction"));
        aliases.put("family", List.of("gia dinh", "family"));
        aliases.put("adventure", List.of("phieu luu", "adventure"));
        aliases.put("romance", List.of("lang man", "romance"));
        aliases.put("action", List.of("hanh dong", "action"));
        aliases.put("drama", List.of("chinh kich", "drama"));

        Map<String, Pattern> patterns = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : aliases.entrySet()) {
            String combined = entry.getValue().stream()
                    .map(Pattern::quote)
                    .reduce((a, b) -> a + "|" + b)
                    .orElseThrow();
            patterns.put(entry.getKey(), Pattern.compile(combined, FLAGS));
        }
        return Collections.unmodifiableMap(patterns);
    }

    public ChatIntent parse(String query) {
        return parse(query, List.of());
    }

    public ChatIntent parse(String query, List<ChatHistoryMessage> history) {
        String safeQuery = query == null ? "" : query;
        String trimmedQuery = safeQuery.trim();
        List<String> previous = new ArrayList<>();
        if (history != null) {
            for (ChatHistoryMessage message : history) {
                if (message == null || !"user".equals(message.role())) {
                    continue;
                }
                String content = message.content();
                if (content == null || content.isBlank() || content.trim().equals(trimmedQuery)) {
                    continue;
                }
                if (!isPatch(content)) {
                    previous.clear();
                }
                previous.add(content);
            }
        }

        boolean patch = !previous.isEmpty() && isPatch(safeQuery);
        String semantic;
        if (patch) {
            List<String> combined = new ArrayList<>(previous.size() + 1);
            combined.addAll(previous);
            combined.add(safeQuery);
            semantic = String.join(" ", combined);
        } else {
            semantic = safeQuery;
        }

        String folded = TextFolding.fold(semantic);
        Map<String, Object> constraints = new LinkedHashMap<>();
        List<String> include = new ArrayList<>();
        List<String> exclude = new ArrayList<>();

        for (Map.Entry<String, Pattern> entry : GENRE_PATTERNS.entrySet()) {
            Matcher matcher = entry.getValue().matcher(folded);
            List<Integer> starts = new ArrayList<>();
            while (matcher.find()) {
                starts.add(matcher.start());
            }
            if (starts.isEmpty()) {
                continue;
            }
            boolean excluded = false;
            for (int start : starts) {
                String window = folded.substring(Math.max(0, start - 40), start);
                if (EXCLUDE_PREFIX_RE.matcher(window).find()) {
                    excluded = true;
                    break;
                }
            }
            if (excluded) {
                exclude.add(entry.getKey());
            } else {
                include.add(entry.getKey());
            }
        }

        if (!include.isEmpty()) {
            constraints.put("include_genres", List.copyOf(include));
        }
        if (!exclude.isEmpty()) {
            constraints.put("exclude_genres", List.copyOf(exclude));
        }

        Matcher yearMatcher = YEAR_RE.matcher(folded);
        while (yearMatcher.find()) {
            String relation = yearMatcher.group(1);
            int year = Integer.parseInt(yearMatcher.group(2));
            if ("truoc".equals(relation) || "before".equals(relation)) {
                constraints.put("year_max", year - 1);
            } else if ("sau".equals(relation) || "after".equals(relation)) {
                constraints.put("year_min", year + 1);
            } else if ("tu".equals(relation)) {
                constraints.put("year_min", year);
            } else if ("den".equals(relation)) {
                constraints.put("year_max", year);
            } else {
                constraints.put("year_min", year);
                constraints.put("year_max", year);
            }
        }

        String current = TextFolding.collapseWhitespace(TextFolding.fold(safeQuery));
        String seriesInfo = null;
        boolean moment = MOMENT_HINT_RE.matcher(folded).find();
        boolean exactEpisode = EXACT_EPISODE_RE.matcher(folded).find();
        boolean recommend = RECOMMEND_HINT_RE.matcher(folded).find();

        if (!(moment || exactEpisode || recommend)) {
            Matcher seriesCount = SERIES_COUNT_RE.matcher(current);
            if (seriesCount.find()) {
                seriesInfo = "tap".equals(seriesCount.group(1)) ? "episodes" : "seasons";
            } else if (SERIES_CHARACTERS_RE.matcher(current).find()) {
                seriesInfo = "characters";
            } else if (SERIES_OVERVIEW_RE.matcher(current).find()) {
                seriesInfo = "summary";
            }
        }
        if (seriesInfo != null) {
            constraints.put("series_only", true);
        }

        boolean chitchat = CHITCHAT_RE.matcher(current).matches();
        boolean mood = MOOD_RE.matcher(folded).find();
        boolean moodDiscovery = mood
                && !(seriesInfo != null || moment || exactEpisode || TITLE_HINT_RE.matcher(folded).find());
        String mode = chitchat ? "chitchat" : (recommend || moodDiscovery ? "recommend" : "find_moment");

        int requested;
        Matcher countMatcher = COUNT_RE.matcher(current);
        if (countMatcher.find()) {
            String amount = countMatcher.group(1) != null ? countMatcher.group(1) : countMatcher.group(2);
            requested = "mot".equals(amount) ? 1 : Math.max(1, Math.min(100, Integer.parseInt(amount)));
        } else {
            requested = (recommend || LIST_HINT_RE.matcher(current).find()) ? 8 : 1;
        }

        String intentType = patch ? "CONVERSATIONAL_PATCH" : (chitchat ? "NO_OP" : "DISCOVERY");
        String scope = seriesInfo != null ? "series" : (moment ? "scene" : "episode");
        boolean moodRecommendation = moodDiscovery && "recommend".equals(mode);
        String moodTarget = SAD_TARGET_RE.matcher(folded).find() ? "sad" : "cheerful";

        return new ChatIntent(
                intentType,
                mode,
                scope,
                semantic,
                constraints,
                requested,
                seriesInfo,
                include,
                exclude,
                moodRecommendation,
                moodTarget
        );
    }

    private static boolean isPatch(String query) {
        return PATCH_RE.matcher(TextFolding.fold(query).trim()).find();
    }
}
