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

public record RecommendationRequest(
        @Size(max = 20, message = "Chỉ được chọn tối đa 20 thể loại.")
        List<@NotNull(message = "Thể loại không được để trống.")
                @Size(max = 100, message = "Tên thể loại không được vượt quá 100 ký tự.") String> genres,
        @Size(max = 100, message = "Chỉ được chọn tối đa 100 phim yêu thích.")
        List<@NotNull(message = "Mã phim không được để trống.")
                @Size(max = 100, message = "Mã phim không được vượt quá 100 ký tự.") String> likedMovieIds,
        @Size(max = 500, message = "Tâm trạng không được vượt quá 500 ký tự.") String mood,
        @JsonDeserialize(using = LimitDeserializer.class)
        @Min(value = 1, message = "Số lượng gợi ý phải từ 1 đến 100.")
        @Max(value = 100, message = "Số lượng gợi ý phải từ 1 đến 100.")
        Integer limit
) {
    private static final int DEFAULT_LIMIT = 4;

    public RecommendationRequest {
        genres = genres == null ? List.of() : genres;
        likedMovieIds = likedMovieIds == null ? List.of() : likedMovieIds;
        limit = limit == null ? DEFAULT_LIMIT : limit;
    }

    static final class LimitDeserializer extends StdScalarDeserializer<Integer> {

        LimitDeserializer() {
            super(Integer.class);
        }

        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return parser.getIntValue();
            }
            return context.reportInputMismatch(Integer.class, "Recommendation limit must be an integer");
        }

        @Override
        public Integer getAbsentValue(DeserializationContext context) {
            return DEFAULT_LIMIT;
        }

        @Override
        public Integer getNullValue(DeserializationContext context) throws JsonMappingException {
            return context.reportInputMismatch(Integer.class, "Recommendation limit is required when provided");
        }
    }
}
