package com.dippie.recommendation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.IOException;
import java.util.List;

// Payload cho API /api/recommend, điền sẵn giá trị mặc định cho các trường bỏ trống và kiểm tra giới hạn đầu vào.
public record RecommendationRequest(
        @Size(max = 20, message = "Bạn chỉ chọn được tối đa 20 thể loại.")
        List<@NotNull(message = "Thể loại không được để trống.")
                @Size(max = 100, message = "Tên thể loại tối đa 100 ký tự.") String> genres,
        @Size(max = 100, message = "Bạn chỉ chọn được tối đa 100 phim yêu thích.")
        List<@NotNull(message = "Mã phim không được để trống.")
                @Size(max = 100, message = "Mã phim tối đa 100 ký tự.") String> likedMovieIds,
        @Size(max = 500, message = "Mô tả tâm trạng tối đa 500 ký tự.") String mood,
        String moodPreset,
        @Size(max = 100, message = "Mã bộ phim tối đa 100 ký tự.") String seriesId,
        @JsonDeserialize(using = LimitDeserializer.class)
        @Min(value = 1, message = "Số lượng gợi ý phải từ 1 đến 100.")
        @Max(value = 100, message = "Số lượng gợi ý phải từ 1 đến 100.")
        Integer limit
) {
    private static final int DEFAULT_LIMIT = 4;

    public RecommendationRequest {
        genres = genres == null ? List.of() : genres;
        likedMovieIds = likedMovieIds == null ? List.of() : likedMovieIds;
        seriesId = seriesId == null || seriesId.isBlank() ? null : seriesId.strip();
        limit = limit == null ? DEFAULT_LIMIT : limit;
    }

    // Deserializer riêng cho trường limit: không truyền thì lấy 4, còn truyền chuỗi, số thực hay null rõ ràng thì báo lỗi luôn.
    static final class LimitDeserializer extends StdScalarDeserializer<Integer> {

        LimitDeserializer() {
            super(Integer.class);
        }

        // Chỉ nhận đúng số nguyên JSON, chặn kiểu ép chuỗi "5" hay số thực 4.5 mặc định của Jackson.
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return parser.getIntValue();
            }
            return context.reportInputMismatch(Integer.class, "Số lượng gợi ý phải là số nguyên.");
        }

        @Override
        public Integer getAbsentValue(DeserializationContext context) {
            return DEFAULT_LIMIT;
        }

        // Client cố tình gửi {"limit": null} thì từ chối thay vì âm thầm rơi về mặc định.
        @Override
        public Integer getNullValue(DeserializationContext context) throws JsonMappingException {
            return context.reportInputMismatch(Integer.class, "Nếu có truyền số lượng gợi ý thì không được để trống.");
        }
    }
}
