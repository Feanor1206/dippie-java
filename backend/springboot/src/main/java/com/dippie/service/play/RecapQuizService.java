package com.dippie.service.play;

import com.dippie.common.json.JsonText;
import com.dippie.dto.play.RecapGateItemView;
import com.dippie.dto.play.RecapQuizAnswerRequest;
import com.dippie.dto.play.RecapQuizAnswerResponse;
import com.dippie.dto.play.RecapQuizItemView;
import com.dippie.dto.play.RecapQuizResponse;
import com.dippie.dto.play.RecapStatusView;
import com.dippie.exception.InvalidRequestException;
import com.dippie.exception.NotFoundException;
import com.dippie.exception.UnauthorizedException;
import com.dippie.model.catalog.CatalogCharacter;
import com.dippie.model.catalog.CatalogEpisode;
import com.dippie.model.catalog.CatalogRecord;
import com.dippie.model.catalog.CatalogScene;
import com.dippie.model.catalog.CatalogSegment;
import com.dippie.model.catalog.PublicationStatus;
import com.dippie.model.catalog.SeriesEpisode;
import com.dippie.model.play.ChildEpisodeRecapGate;
import com.dippie.model.play.ChildPlaySettings;
import com.dippie.model.play.RecapQuizType;
import com.dippie.model.profile.ChildProfile;
import com.dippie.repository.catalog.CatalogCharacterRepository;
import com.dippie.repository.catalog.CatalogEpisodeRepository;
import com.dippie.repository.catalog.CatalogSceneRepository;
import com.dippie.repository.catalog.CatalogSegmentRepository;
import com.dippie.repository.play.ChildEpisodeRecapGateRepository;
import com.dippie.repository.play.ChildPlaySettingsRepository;
import com.dippie.repository.profile.ChildProfileRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import com.dippie.service.catalog.CatalogReadModel;
import com.dippie.service.catalog.CatalogReadModelCache;
import com.dippie.service.catalog.CatalogVisibility;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// dựng câu đố ôn tập từ dữ liệu catalog có sẵn (cảnh/phân đoạn/plot_facts, nhân vật hoặc tên tập) và quản lý mở khóa tập kế tiếp
@Service
public class RecapQuizService {

    private static final String LOGIN_REQUIRED = "Bạn cần đăng nhập để tiếp tục.";
    private static final Duration QUIZ_TTL = Duration.ofMinutes(10);
    private static final Comparator<Integer> NUMBER_ORDER = Comparator.nullsLast(Comparator.naturalOrder());
    private static final Comparator<SeriesEpisode> SERIES_EPISODE_ORDER = Comparator
            .comparing(SeriesEpisode::seasonNumber, NUMBER_ORDER)
            .thenComparing(SeriesEpisode::episodeNumber, NUMBER_ORDER)
            .thenComparing(SeriesEpisode::id);

    private final CurrentViewer currentViewer;
    private final ChildProfileRepository childProfileRepository;
    private final ChildPlaySettingsRepository settingsRepository;
    private final ChildEpisodeRecapGateRepository gateRepository;
    private final CatalogEpisodeRepository episodeRepository;
    private final CatalogSceneRepository sceneRepository;
    private final CatalogSegmentRepository segmentRepository;
    private final CatalogCharacterRepository characterRepository;
    private final CatalogVisibility catalogVisibility;
    private final CatalogReadModelCache catalogReadModelCache;
    private final PlayTokenCodec tokenCodec;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RecapQuizService(
            CurrentViewer currentViewer,
            ChildProfileRepository childProfileRepository,
            ChildPlaySettingsRepository settingsRepository,
            ChildEpisodeRecapGateRepository gateRepository,
            CatalogEpisodeRepository episodeRepository,
            CatalogSceneRepository sceneRepository,
            CatalogSegmentRepository segmentRepository,
            CatalogCharacterRepository characterRepository,
            CatalogVisibility catalogVisibility,
            CatalogReadModelCache catalogReadModelCache,
            PlayTokenCodec tokenCodec,
            Clock clock
    ) {
        this.currentViewer = currentViewer;
        this.childProfileRepository = childProfileRepository;
        this.settingsRepository = settingsRepository;
        this.gateRepository = gateRepository;
        this.episodeRepository = episodeRepository;
        this.sceneRepository = sceneRepository;
        this.segmentRepository = segmentRepository;
        this.characterRepository = characterRepository;
        this.catalogVisibility = catalogVisibility;
        this.catalogReadModelCache = catalogReadModelCache;
        this.tokenCodec = tokenCodec;
        this.clock = clock;
    }

    // khi bé xem xong 1 tập thì đặt cổng khóa tập kế tiếp nếu tập đó chưa được mở khóa từ trước
    @Transactional
    public void onEpisodeCompleted(Long profileId, String seriesId, String completedEpisodeId) {
        if (profileId == null || seriesId == null || completedEpisodeId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        ChildPlaySettings settings = settingsRepository.findById(profileId)
                .orElseGet(() -> ChildPlaySettings.defaults(profileId, now));
        if (!settings.isRecapToNextEnabled()) {
            return;
        }
        String nextEpisodeId = findNextEpisodeId(seriesId, completedEpisodeId);
        if (nextEpisodeId == null) {
            return;
        }
        if (gateRepository.findByProfileIdAndTargetEpisodeId(profileId, nextEpisodeId).isPresent()) {
            return;
        }
        try {
            gateRepository.saveAndFlush(new ChildEpisodeRecapGate(
                    profileId,
                    seriesId,
                    completedEpisodeId,
                    nextEpisodeId,
                    false,
                    now
            ));
        } catch (DataIntegrityViolationException ignored) {
            // hai request kết thúc tập cùng lúc thì giữ bản ghi vừa chèn trước
        }
    }

    @Transactional(readOnly = true)
    public RecapStatusView buildStatus(Long profileId, ChildPlaySettings settings, String seriesId) {
        if (!settings.isRecapToNextEnabled()) {
            return RecapStatusView.disabled();
        }
        List<ChildEpisodeRecapGate> gates = (seriesId != null && !seriesId.isBlank())
                ? gateRepository.findByProfileIdAndSeriesId(profileId, seriesId.trim())
                : gateRepository.findByProfileIdAndUnlockedFalse(profileId);
        List<RecapGateItemView> pending = new ArrayList<>();
        List<String> unlocked = new ArrayList<>();
        for (ChildEpisodeRecapGate gate : gates) {
            if (gate.isUnlocked()) {
                unlocked.add(gate.getTargetEpisodeId());
            } else {
                pending.add(new RecapGateItemView(gate.getSourceEpisodeId(), gate.getTargetEpisodeId()));
            }
        }
        return new RecapStatusView(true, List.copyOf(pending), List.copyOf(unlocked));
    }

    // tạo câu hỏi ôn tập cho tập vừa xem và nhúng đáp án đúng vào token mã hóa ký HMAC
    @Transactional(readOnly = true)
    public RecapQuizResponse createQuiz(String episodeId) {
        Viewer viewer = requireChildViewer();
        if (episodeId == null || episodeId.isBlank()) {
            throw new InvalidRequestException("Thiếu mã tập phim để tạo câu hỏi ôn tập.");
        }
        ChildProfile profile = childProfileRepository.findByIdAndUserId(viewer.profileId(), viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        String cleanEpisodeId = episodeId.trim();
        CatalogEpisode episode = episodeRepository.findById(cleanEpisodeId)
                .filter(catalogVisibility::isEpisodeVisible)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tập phim: " + cleanEpisodeId));

        String targetEpisodeId = findNextEpisodeId(episode.getSeriesId(), episode.getId());
        BuiltQuiz built = buildQuizForEpisode(episode);
        String payload = built.type().ordinal()
                + "\n" + episode.getId()
                + "\n" + (targetEpisodeId == null ? "" : targetEpisodeId)
                + "\n" + built.expectedAnswer();
        PlayTokenCodec.IssuedToken issued = tokenCodec.issue(
                PlayTokenCodec.KIND_RECAP_QUIZ,
                profile.getId(),
                QUIZ_TTL,
                payload
        );
        LocalDateTime expiresAtUtc = LocalDateTime.ofInstant(issued.expiresAt(), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS);
        return new RecapQuizResponse(
                issued.token(),
                episode.getId(),
                cleanTitle(episode.getTitle()),
                targetEpisodeId,
                built.type(),
                built.prompt(),
                built.items(),
                expiresAtUtc,
                QUIZ_TTL.toSeconds()
        );
    }

    // kiểm tra đáp án ôn tập từ token ký HMAC, đúng thì mở khóa vĩnh viễn tập tiếp theo cho hồ sơ bé này
    @Transactional
    public RecapAnswerOutcome submitAnswer(RecapQuizAnswerRequest request) {
        Viewer viewer = requireChildViewer();
        Long profileId = viewer.profileId();
        String rawToken = request == null ? null : request.token();
        PlayTokenCodec.DecodedToken decoded = tokenCodec.decode(rawToken, PlayTokenCodec.KIND_RECAP_QUIZ)
                .orElseThrow(() -> new InvalidRequestException("Câu hỏi ôn tập không hợp lệ hoặc đã hết hạn. Bé lấy câu mới nhé."));
        Instant nowInstant = clock.instant();
        if (decoded.profileId() != profileId || !decoded.expiresAt().isAfter(nowInstant)) {
            throw new InvalidRequestException("Câu hỏi ôn tập đã hết hạn. Bé lấy câu mới nhé.");
        }
        if (tokenCodec.isUsed(decoded.tokenId(), nowInstant)) {
            throw new InvalidRequestException("Bé đã hoàn thành câu hỏi này rồi.");
        }

        ParsedQuizPayload parsed = parsePayload(decoded.payload());
        boolean correct = matchesAnswer(parsed, request);
        if (!correct) {
            return new RecapAnswerOutcome(
                    new RecapQuizAnswerResponse(false, parsed.episodeId(), parsed.targetEpisodeId(), false),
                    "Chưa đúng rồi, bé nhớ lại tập vừa xem và thử lại nhé!"
            );
        }

        if (!tokenCodec.tryRedeem(decoded.tokenId(), decoded.expiresAt(), nowInstant)) {
            throw new InvalidRequestException("Bé đã hoàn thành câu hỏi này rồi.");
        }

        childProfileRepository.findByIdAndUserIdForUpdate(profileId, viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);

        if (parsed.targetEpisodeId() != null && !parsed.targetEpisodeId().isBlank()) {
            unlockTargetEpisode(profileId, parsed.episodeId(), parsed.targetEpisodeId(), now);
        }
        return new RecapAnswerOutcome(
                new RecapQuizAnswerResponse(true, parsed.episodeId(), parsed.targetEpisodeId(), true),
                "Giỏi quá! Bé đã mở khóa tập tiếp theo rồi!"
        );
    }

    private void unlockTargetEpisode(Long profileId, String sourceEpisodeId, String targetEpisodeId, LocalDateTime now) {
        ChildEpisodeRecapGate existing = gateRepository.findByProfileIdAndTargetEpisodeId(profileId, targetEpisodeId)
                .orElse(null);
        if (existing != null) {
            existing.markUnlocked(now);
            gateRepository.saveAndFlush(existing);
            return;
        }
        String seriesId = episodeRepository.findById(sourceEpisodeId)
                .map(CatalogEpisode::getSeriesId)
                .orElse("");
        try {
            gateRepository.saveAndFlush(new ChildEpisodeRecapGate(
                    profileId,
                    seriesId,
                    sourceEpisodeId,
                    targetEpisodeId,
                    true,
                    now
            ));
        } catch (DataIntegrityViolationException ex) {
            gateRepository.findByProfileIdAndTargetEpisodeId(profileId, targetEpisodeId)
                    .ifPresent(gate -> {
                        gate.markUnlocked(now);
                        gateRepository.saveAndFlush(gate);
                    });
        }
    }

    private BuiltQuiz buildQuizForEpisode(CatalogEpisode episode) {
        List<String> orderedMoments = extractOrderedMoments(episode);
        if (orderedMoments.size() >= 3) {
            return buildOrderScenesQuiz(orderedMoments);
        }
        BuiltQuiz characterQuiz = tryBuildCharacterQuiz(episode);
        if (characterQuiz != null) {
            return characterQuiz;
        }
        return buildTitleChoiceQuiz(episode);
    }

    // ưu tiên các cảnh có mốc startMs, thiếu thì dùng phân đoạn theo thứ tự thời gian/position, cuối cùng mới rút từ plot_facts
    private List<String> extractOrderedMoments(CatalogEpisode episode) {
        List<String> fromScenes = sceneRepository.findByEpisodeIdOrderByStartMsAsc(episode.getId()).stream()
                .filter(CatalogRecord::isPublished)
                .map(RecapQuizService::describeScene)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (fromScenes.size() >= 3) {
            return pickSpreadMoments(fromScenes);
        }

        List<CatalogSegment> publishedSegments = segmentRepository.findByEpisodeIdOrderByPositionAsc(episode.getId())
                .stream()
                .filter(CatalogRecord::isPublished)
                .sorted(Comparator
                        .comparing(CatalogSegment::getEpisodeStartMs, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingInt(CatalogSegment::getPosition))
                .toList();
        List<String> fromSegments = publishedSegments.stream()
                .map(RecapQuizService::describeSegment)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (fromSegments.size() >= 3) {
            return pickSpreadMoments(fromSegments);
        }

        List<String> fromPlotFacts = extractPlotFacts(episode.getPlotFactsJson());
        if (fromPlotFacts.size() >= 3) {
            return pickSpreadMoments(fromPlotFacts);
        }
        return List.of();
    }

    // chọn 3 hoặc 4 mốc trải đều từ đầu đến cuối tập để bé dễ nhớ thứ tự cốt truyện
    private static List<String> pickSpreadMoments(List<String> moments) {
        if (moments.size() <= 4) {
            return moments;
        }
        int last = moments.size() - 1;
        Set<Integer> indices = new LinkedHashSet<>();
        indices.add(0);
        indices.add(last / 3);
        indices.add((last * 2) / 3);
        indices.add(last);
        List<String> picked = new ArrayList<>(4);
        for (int idx : indices) {
            picked.add(moments.get(idx));
        }
        return picked;
    }

    private BuiltQuiz buildOrderScenesQuiz(List<String> orderedTexts) {
        List<Integer> order = new ArrayList<>(orderedTexts.size());
        for (int i = 0; i < orderedTexts.size(); i++) {
            order.add(i);
        }
        // xáo vị trí hiển thị sao cho khác thứ tự đúng ban đầu, rồi mới gắn mã i1..i4 sau khi xáo để khỏi lộ đáp án
        for (int attempt = 0; attempt < 8; attempt++) {
            Collections.shuffle(order, random);
            if (!isIdentityOrder(order)) {
                break;
            }
        }
        if (isIdentityOrder(order) && order.size() >= 2) {
            Collections.swap(order, 0, order.size() - 1);
        }

        List<RecapQuizItemView> items = new ArrayList<>(order.size());
        String[] idByOriginalIndex = new String[orderedTexts.size()];
        for (int displayIdx = 0; displayIdx < order.size(); displayIdx++) {
            int originalIdx = order.get(displayIdx);
            String itemId = "i" + (displayIdx + 1);
            items.add(new RecapQuizItemView(itemId, orderedTexts.get(originalIdx)));
            idByOriginalIndex[originalIdx] = itemId;
        }
        String expected = String.join(",", idByOriginalIndex);
        return new BuiltQuiz(
                RecapQuizType.ORDER_SCENES,
                "Xếp lại các cảnh theo đúng thứ tự trong tập vừa xem nhé!",
                List.copyOf(items),
                expected
        );
    }

    private BuiltQuiz tryBuildCharacterQuiz(CatalogEpisode episode) {
        List<String> episodeCharIds = JsonText.stringList(episode.getCharacterIdsJson());
        if (episodeCharIds.isEmpty()) {
            return null;
        }
        List<CatalogCharacter> allCharacters = characterRepository.findAll();
        Map<String, CatalogCharacter> byId = new LinkedHashMap<>();
        for (CatalogCharacter character : allCharacters) {
            if (character.getName() != null && !character.getName().isBlank()) {
                byId.put(character.getId(), character);
            }
        }
        List<String> presentNames = new ArrayList<>();
        Set<String> presentIds = new LinkedHashSet<>();
        for (String id : episodeCharIds) {
            CatalogCharacter found = byId.get(id);
            if (found != null && presentIds.add(id)) {
                presentNames.add(found.getName().trim());
            }
        }
        if (presentNames.isEmpty()) {
            return null;
        }

        List<String> sameSeriesDistractors = new ArrayList<>();
        List<String> otherSeriesDistractors = new ArrayList<>();
        Set<String> seenNames = new LinkedHashSet<>(presentNames);
        for (CatalogCharacter candidate : allCharacters) {
            if (presentIds.contains(candidate.getId())) {
                continue;
            }
            String name = candidate.getName() == null ? "" : candidate.getName().trim();
            if (name.isEmpty() || !seenNames.add(name)) {
                continue;
            }
            if (JsonText.stringList(candidate.getSeriesIdsJson()).contains(episode.getSeriesId())) {
                sameSeriesDistractors.add(name);
            } else {
                otherSeriesDistractors.add(name);
            }
        }
        Collections.shuffle(sameSeriesDistractors, random);
        Collections.shuffle(otherSeriesDistractors, random);

        List<String> distractors = new ArrayList<>();
        distractors.addAll(sameSeriesDistractors);
        distractors.addAll(otherSeriesDistractors);
        if (distractors.size() < 2) {
            return null;
        }

        String correctName = presentNames.get(random.nextInt(presentNames.size()));
        int distractorCount = Math.min(3, distractors.size());
        List<String> optionTexts = new ArrayList<>(1 + distractorCount);
        optionTexts.add(correctName);
        for (int i = 0; i < distractorCount; i++) {
            optionTexts.add(distractors.get(i));
        }
        return buildSingleChoiceQuiz(
                RecapQuizType.CHARACTER_CHOICE,
                "Nhân vật nào đã xuất hiện trong tập vừa rồi?",
                correctName,
                optionTexts
        );
    }

    private BuiltQuiz buildTitleChoiceQuiz(CatalogEpisode episode) {
        String correctTitle = cleanTitle(episode.getTitle());
        List<String> distractors = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        seen.add(correctTitle.toLowerCase());

        List<CatalogEpisode> sameSeries = episodeRepository
                .findBySeriesIdAndPublicationStatusOrderByNumberAsc(episode.getSeriesId(), PublicationStatus.READY_VALUE);
        List<String> seriesTitles = new ArrayList<>();
        for (CatalogEpisode candidate : sameSeries) {
            if (candidate.getId().equals(episode.getId())) {
                continue;
            }
            String title = cleanTitle(candidate.getTitle());
            if (!title.isEmpty() && seen.add(title.toLowerCase())) {
                seriesTitles.add(title);
            }
        }
        Collections.shuffle(seriesTitles, random);
        distractors.addAll(seriesTitles);

        if (distractors.size() < 3) {
            List<String> catalogTitles = new ArrayList<>();
            for (SeriesEpisode candidate : catalogReadModelCache.current().seriesEpisodes(episode.getSeriesId())) {
                String title = cleanTitle(candidate.title());
                if (!title.isEmpty() && seen.add(title.toLowerCase())) {
                    catalogTitles.add(title);
                }
            }
            Collections.shuffle(catalogTitles, random);
            distractors.addAll(catalogTitles);
        }
        for (String fallback : List.of(
                "Chuyến phiêu lưu kỳ diệu",
                "Bí mật khu vườn nhỏ",
                "Ngày hội của những người bạn",
                "Cuộc đua vui nhộn"
        )) {
            if (distractors.size() >= 3) {
                break;
            }
            if (seen.add(fallback.toLowerCase())) {
                distractors.add(fallback);
            }
        }

        List<String> optionTexts = new ArrayList<>(4);
        optionTexts.add(correctTitle);
        for (int i = 0; i < Math.min(3, distractors.size()); i++) {
            optionTexts.add(distractors.get(i));
        }
        return buildSingleChoiceQuiz(
                RecapQuizType.TITLE_CHOICE,
                "Tập vừa rồi tên là gì?",
                correctTitle,
                optionTexts
        );
    }

    private BuiltQuiz buildSingleChoiceQuiz(
            RecapQuizType type,
            String prompt,
            String correctText,
            List<String> optionTexts
    ) {
        List<String> shuffled = new ArrayList<>(optionTexts);
        Collections.shuffle(shuffled, random);
        List<RecapQuizItemView> items = new ArrayList<>(shuffled.size());
        String correctId = "";
        for (int i = 0; i < shuffled.size(); i++) {
            String id = "c" + (i + 1);
            String text = shuffled.get(i);
            items.add(new RecapQuizItemView(id, text));
            if (text.equals(correctText) && correctId.isEmpty()) {
                correctId = id;
            }
        }
        return new BuiltQuiz(type, prompt, List.copyOf(items), correctId);
    }

    // tìm tập tiếp theo trong cùng bộ phim theo thứ tự mùa, số tập rồi mã tập
    private String findNextEpisodeId(String seriesId, String episodeId) {
        CatalogReadModel readModel = catalogReadModelCache.current();
        List<SeriesEpisode> allSeriesEpisodes = readModel.seriesEpisodes(seriesId);
        List<SeriesEpisode> accessible = allSeriesEpisodes.stream()
                .filter(SeriesEpisode::memberAccessible)
                .sorted(SERIES_EPISODE_ORDER)
                .toList();
        String nextFromAccessible = findNextInOrdered(accessible, episodeId);
        if (nextFromAccessible != null || containsEpisode(accessible, episodeId)) {
            return nextFromAccessible;
        }
        List<SeriesEpisode> allSorted = allSeriesEpisodes.stream()
                .sorted(SERIES_EPISODE_ORDER)
                .toList();
        return findNextInOrdered(allSorted, episodeId);
    }

    private static boolean containsEpisode(List<SeriesEpisode> list, String episodeId) {
        for (SeriesEpisode ep : list) {
            if (ep.id().equals(episodeId)) {
                return true;
            }
        }
        return false;
    }

    private static String findNextInOrdered(List<SeriesEpisode> ordered, String episodeId) {
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).id().equals(episodeId)) {
                return i + 1 < ordered.size() ? ordered.get(i + 1).id() : null;
            }
        }
        return null;
    }

    private static boolean matchesAnswer(ParsedQuizPayload parsed, RecapQuizAnswerRequest request) {
        if (request == null) {
            return false;
        }
        if (parsed.type() == RecapQuizType.ORDER_SCENES) {
            List<String> orderedIds = request.orderedIds();
            if (orderedIds == null || orderedIds.isEmpty()) {
                return false;
            }
            String supplied = orderedIds.stream()
                    .map(id -> id == null ? "" : id.trim())
                    .reduce((left, right) -> left + "," + right)
                    .orElse("");
            return supplied.equals(parsed.expectedAnswer());
        }
        String choiceId = request.choiceId() == null ? "" : request.choiceId().trim();
        return !choiceId.isEmpty() && choiceId.equals(parsed.expectedAnswer());
    }

    private static ParsedQuizPayload parsePayload(String payload) {
        String[] parts = payload.split("\n", 4);
        if (parts.length != 4) {
            throw new InvalidRequestException("Câu hỏi ôn tập không hợp lệ.");
        }
        int ordinal;
        try {
            ordinal = Integer.parseInt(parts[0]);
        } catch (NumberFormatException ex) {
            throw new InvalidRequestException("Câu hỏi ôn tập không hợp lệ.");
        }
        RecapQuizType[] values = RecapQuizType.values();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new InvalidRequestException("Câu hỏi ôn tập không hợp lệ.");
        }
        String target = parts[2].isEmpty() ? null : parts[2];
        return new ParsedQuizPayload(values[ordinal], parts[1], target, parts[3]);
    }

    private static boolean isIdentityOrder(List<Integer> order) {
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i) != i) {
                return false;
            }
        }
        return true;
    }

    private static String describeScene(CatalogScene scene) {
        String text = firstNonBlank(scene.getSummary(), scene.getVisualDescription(), scene.getTitle());
        return trimDescription(text);
    }

    private static String describeSegment(CatalogSegment segment) {
        String synopsis = firstNonBlank(segment.getSynopsis());
        String title = firstNonBlank(segment.getTitle());
        if (synopsis != null && title != null && !synopsis.toLowerCase().startsWith(title.toLowerCase())) {
            return trimDescription(title + ": " + synopsis);
        }
        return trimDescription(firstNonBlank(synopsis, title));
    }

    private static List<String> extractPlotFacts(String plotFactsJson) {
        JsonNode root = JsonText.parse(plotFactsJson).orElse(null);
        if (root == null || !root.isArray()) {
            return List.of();
        }
        List<String> facts = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode entry : root) {
            String raw = entry.isTextual()
                    ? entry.textValue()
                    : firstNonBlank(
                            entry.path("text").textValue(),
                            entry.path("summary").textValue(),
                            entry.path("fact").textValue(),
                            entry.path("description").textValue()
                    );
            String clean = trimDescription(raw);
            if (clean != null && seen.add(clean.toLowerCase())) {
                facts.add(clean);
            }
        }
        return facts;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String trimDescription(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String compact = text.trim().replaceAll("\\s+", " ");
        return compact.length() > 140 ? compact.substring(0, 137).trim() + "…" : compact;
    }

    private static String cleanTitle(String rawTitle) {
        if (rawTitle == null || rawTitle.isBlank()) {
            return "Tập phim hoạt hình";
        }
        String cleaned = rawTitle
                .replaceAll("\\s*\\[[A-Za-z0-9_-]{6,}\\](?=\\.\\w+$|$)", "")
                .replaceAll("(?i)\\.(mp4|mkv|webm|mov|avi)$", "")
                .replace("⧸", "/")
                .trim();
        return cleaned.isEmpty() ? rawTitle.trim() : cleaned;
    }

    private Viewer requireChildViewer() {
        Viewer viewer = currentViewer.require();
        if (!viewer.isChildProfile()) {
            throw new InvalidRequestException("Câu hỏi ôn tập qua tập chỉ áp dụng cho hồ sơ trẻ em.");
        }
        return viewer;
    }

    private record BuiltQuiz(RecapQuizType type, String prompt, List<RecapQuizItemView> items, String expectedAnswer) {
    }

    private record ParsedQuizPayload(RecapQuizType type, String episodeId, String targetEpisodeId, String expectedAnswer) {
    }

    public record RecapAnswerOutcome(RecapQuizAnswerResponse response, String message) {
    }
}
