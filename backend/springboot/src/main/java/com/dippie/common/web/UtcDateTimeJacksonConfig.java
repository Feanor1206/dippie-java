package com.dippie.common.web;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

// Cấu hình Jackson ép mọi LocalDateTime xuất ra JSON theo chuẩn ISO-8601 UTC có đuôi Z.
// Việt đừng bỏ module này vì thiếu Z là trình duyệt sẽ hiểu nhầm thành giờ máy người dùng thay vì quy đổi từ UTC.
@Configuration
public class UtcDateTimeJacksonConfig {

    @Bean
    public Module utcLocalDateTimeModule() {
        SimpleModule module = new SimpleModule("DippieUtcLocalDateTimeModule");
        module.addSerializer(LocalDateTime.class, new UtcLocalDateTimeSerializer());
        module.addDeserializer(LocalDateTime.class, new UtcLocalDateTimeDeserializer());
        return module;
    }

    // Gắn offset UTC khi xuất LocalDateTime để chuỗi JSON luôn kết thúc bằng Z.
    private static final class UtcLocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {

        @Override
        public void serialize(LocalDateTime dateTime, JsonGenerator generator, SerializerProvider serializers)
                throws IOException {
            if (dateTime == null) {
                generator.writeNull();
                return;
            }
            generator.writeString(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(dateTime.atOffset(ZoneOffset.UTC)));
        }
    }

    // Nhận cả chuỗi ISO-8601 có offset/Z lẫn chuỗi không múi giờ, rồi quy hết về LocalDateTime theo UTC.
    private static final class UtcLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            String text = parser.getValueAsString();
            if (text == null || text.isBlank()) {
                return null;
            }
            String trimmed = text.trim();
            try {
                return OffsetDateTime.parse(trimmed, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        .withOffsetSameInstant(ZoneOffset.UTC)
                        .toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                try {
                    return LocalDateTime.ofInstant(Instant.parse(trimmed), ZoneOffset.UTC);
                } catch (DateTimeParseException ignoredAgain) {
                    return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                }
            }
        }
    }
}
