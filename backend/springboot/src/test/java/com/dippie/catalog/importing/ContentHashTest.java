package com.dippie.catalog.importing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ContentHashTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void matchesPythonForJadooCatalog() throws Exception {
        byte[] fixture = Files.readAllBytes(Path.of("../../data/catalog-by-series/catalog.jadoo.json"));

        assertThat(ContentHash.of(mapper.readTree(fixture)).value())
                .isEqualTo("e0d552b7db7db2f44a15c6f3cf5074c92808ab1cba3748513332fbd7916d4319");
    }

    @Test
    void matchesPythonForUnicodeOrderingEscapesAndNumericFormatting() throws Exception {
        var input = mapper.readTree("""
                {"😀":-0.0,"\ue001":1e-7,"a":[5e-324,1e16,0.0001,1000000000000000.0,
                1e23,1234567890123456789012345,"\\u000b\\u000f"]}
                """);

        assertThat(ContentHash.of(input).value())
                .isEqualTo("d88064ab975ecffda86d9eda2b20128d67e9a9d51cc7d33007f2391927bfa8e0");
    }

    @Test
    void documentOwnsItsJsonTree() throws Exception {
        var input = mapper.createObjectNode();
        input.putObject("package").put("id", "original");
        var document = new CatalogDocument(input);

        input.withObjectProperty("package").put("id", "modified");
        document.json().withObjectProperty("package").put("ignored", true);

        assertThat(document.json()).isEqualTo(mapper.readTree("{\"package\":{\"id\":\"original\"}}"));
    }
}
