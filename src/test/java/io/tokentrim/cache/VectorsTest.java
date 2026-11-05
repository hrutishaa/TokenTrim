package io.tokentrim.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorsTest {

    @Test
    void formatsAPostgresVectorLiteral() {
        assertThat(Vectors.toLiteral(new float[]{1.5f, -0.25f, 0f})).isEqualTo("[1.5,-0.25,0.0]");
    }

    @Test
    void rejectsNonFiniteComponents() {
        assertThatThrownBy(() -> Vectors.toLiteral(new float[]{Float.NaN}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
