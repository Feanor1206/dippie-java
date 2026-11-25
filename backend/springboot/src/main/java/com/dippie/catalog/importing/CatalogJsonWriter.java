package com.dippie.catalog.importing;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.core.util.MinimalPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

final class CatalogJsonWriter {

    private static final JsonFactory FACTORY = JsonFactory.builder().disable(JsonWriteFeature.WRITE_HEX_UPPER_CASE).build();

    private static final MinimalPrettyPrinter SPACED = new MinimalPrettyPrinter() {
        @Override
        public void writeObjectFieldValueSeparator(JsonGenerator generator) throws IOException {
            generator.writeRaw(": ");
        }

        @Override
        public void writeObjectEntrySeparator(JsonGenerator generator) throws IOException {
            generator.writeRaw(", ");
        }

        @Override
        public void writeArrayValueSeparator(JsonGenerator generator) throws IOException {
            generator.writeRaw(", ");
        }
    };

    private CatalogJsonWriter() {
    }

    static String encode(JsonNode node, boolean canonical) {
        StringWriter output = new StringWriter();
        try (JsonGenerator generator = FACTORY.createGenerator(output)) {
            if (!canonical) {
                generator.setPrettyPrinter(SPACED);
            }
            write(node, generator, canonical);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return output.toString();
    }

    private static void write(JsonNode node, JsonGenerator generator, boolean canonical) throws IOException {
        switch (node.getNodeType()) {
            case OBJECT -> {
                List<String> keys = new ArrayList<>();
                node.fieldNames().forEachRemaining(keys::add);
                if (canonical) {
                    keys.sort(CatalogJson.CODE_POINT_ORDER);
                }
                generator.writeStartObject();
                for (String key : keys) {
                    generator.writeFieldName(key);
                    write(node.get(key), generator, canonical);
                }
                generator.writeEndObject();
            }
            case ARRAY -> {
                generator.writeStartArray();
                for (JsonNode item : node) {
                    write(item, generator, canonical);
                }
                generator.writeEndArray();
            }
            case NUMBER -> generator.writeNumber(node.isIntegralNumber()
                    ? node.bigIntegerValue().toString() : pythonFloat(node.doubleValue()));
            case BOOLEAN -> generator.writeBoolean(node.booleanValue());
            case STRING -> generator.writeString(node.textValue());
            case NULL, MISSING -> generator.writeNull();
            default -> throw new IllegalArgumentException("Unsupported JSON node: " + node.getNodeType());
        }
    }

    private static String pythonFloat(double value) {
        if (!Double.isFinite(value) || value == 0) {
            return Double.toString(value);
        }
        BigDecimal decimal = BigDecimal.valueOf(value).stripTrailingZeros();
        if (decimal.precision() == 2) {
            BigDecimal oneDigit = new BigDecimal(value).round(new MathContext(1, RoundingMode.HALF_EVEN));
            if (oneDigit.doubleValue() == value) {
                decimal = oneDigit;
            }
        }
        int exponent = decimal.precision() - decimal.scale() - 1;
        if (exponent >= -4 && exponent < 16) {
            String plain = decimal.toPlainString();
            return decimal.scale() > 0 ? plain : plain + ".0";
        }
        String digits = decimal.movePointLeft(exponent).stripTrailingZeros().toPlainString();
        int magnitude = Math.abs(exponent);
        return digits + (exponent < 0 ? "e-" : "e+") + (magnitude < 10 ? "0" : "") + magnitude;
    }
}
