package io.tokentrim.compress;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentExtractorTest {

    private final DocumentExtractor extractor = new DocumentExtractor();

    @Test
    void pullsDocumentAndContextTags() {
        DocumentExtractor.Extraction extraction = extractor.extract("""
                <document title="notes">Alpha body</document>
                Question?
                <context>Beta body</context>
                """);
        assertThat(extraction.documents()).containsExactly("Alpha body", "Beta body");
        assertThat(extraction.remaining()).contains("Question?");
        assertThat(extraction.remaining()).doesNotContain("Alpha body");
    }
}
