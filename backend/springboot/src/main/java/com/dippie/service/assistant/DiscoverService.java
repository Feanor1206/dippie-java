package com.dippie.service.assistant;

import com.dippie.dto.assistant.DiscoverRequest;
import com.dippie.dto.assistant.DiscoverResponse;
import com.dippie.model.assistant.SearchHit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

// Service của API khám phá. Controller chỉ việc chuyển yêu cầu vào đây,
// phần kiểm tra chỉ mục, chạy BM25 và đóng gói DiscoverResponse đều nằm ở tầng service này.
@Service
public class DiscoverService {

    private final LocalCatalogSearch catalog;
    private final ObjectMapper mapper;

    public DiscoverService(LocalCatalogSearch catalog, ObjectMapper mapper) {
        this.catalog = catalog;
        this.mapper = mapper;
    }

    // Chỉ mục chưa dựng xong thì báo 503 để client thử lại, còn lại thì tìm rồi trả kết quả.
    public DiscoverResponse discover(DiscoverRequest request) {
        var index = catalog.index();
        if (index == null) {
            throw new DiscoverRequest.Rejection(HttpStatus.SERVICE_UNAVAILABLE,
                    "Kho phim đang được cập nhật, bạn thử lại sau ít phút nhé.");
        }
        var matches = catalog.matches(index, request.query(), request.limit(), request.constraints());
        return toResponse(request.query(), matches);
    }

    // Đóng gói các Match của LexicalIndex thành DiscoverResponse, kèm gợi ý câu hỏi kế tiếp.
    public DiscoverResponse toResponse(String query, List<LexicalIndex.Match> matches) {
        List<SearchHit> hits = matches.stream().map(LexicalIndex.Match::hit).toList();
        return new DiscoverResponse(new DiscoverResponse.Intent("DISCOVERY", query),
                matches.isEmpty() ? "Chưa tìm thấy phim hay tập nào khớp yêu cầu của bạn." : "Đã tìm trong kho phim hiện có.",
                matches.stream().map(this::movieItem).toList(),
                FollowUpSuggestions.build(hits), new DiscoverResponse.PipelineStages("java-lexical", true));
    }

    // Gắn nhãn thể loại tiếng Việt và bổ sung mốc thời gian tệp nguồn vào evidence đầu tiên cho đúng schema client đang đọc.
    private ObjectNode movieItem(LexicalIndex.Match match) {
        SearchHit hit = match.hit();
        ObjectNode result = mapper.valueToTree(hit);
        result.set("genres", mapper.valueToTree(match.genreLabels()));
        ObjectNode evidence = (ObjectNode) result.path("evidence").get(0);
        evidence.put("sourceStartMs", hit.startMsSnake());
        evidence.put("sourceEndMs", hit.endMs());
        evidence.put("mediaAssetId", match.evidenceMediaAssetId());
        evidence.put("timeline", hit.episodeId() != null && hit.startMs() != null ? "episode" : "file");
        return result;
    }
}
