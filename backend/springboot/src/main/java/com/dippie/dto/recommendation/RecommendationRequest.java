package com.dippie.dto.recommendation;

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

// body cho /api/recommend, điền sẵn mặc định chỗ bỏ trống rồi chặn đầu vào quá giới hạn
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

    // deserializer riêng cho limit: bỏ trống thì lấy 4, còn gửi chuỗi, số thực hay null hẳn thì báo lỗi
    public static final class LimitDeserializer extends StdScalarDeserializer<Integer> {

        public LimitDeserializer() {
            super(Integer.class);
        }

        // chỉ nhận đúng số nguyên JSON, chặn Jackson tự ép "5" hay 4.5
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

        // client cố tình gửi {"limit": null} thì chặn luôn chứ không lặng lẽ rơi về mặc định
        @Override
        public Integer getNullValue(DeserializationContext context) throws JsonMappingException {
            return context.reportInputMismatch(Integer.class, "Nếu có truyền số lượng gợi ý thì không được để trống.");
        }
    }
}
