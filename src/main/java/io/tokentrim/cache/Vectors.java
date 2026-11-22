package io.tokentrim.cache;

import java.math.BigDecimal;

public final class Vectors {

    private Vectors() {
    }

    public static int safeDimensions(int dimensions) {
        if (dimensions < 8 || dimensions > 4096) {
            throw new IllegalArgumentException("embedding dimensions must be between 8 and 4096");
        }
        return dimensions;
    }

    /** Postgres vector literal. Components are numeric only, so this is safe to cast with {@code ::vector}. */
    public static String toLiteral(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("embedding is empty");
        }
        StringBuilder builder = new StringBuilder(vector.length * 8 + 2);
        builder.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            float value = vector[i];
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("embedding contains a non-finite component");
            }
            builder.append(new BigDecimal(Float.toString(value)).toPlainString());
        }
        builder.append(']');
        return builder.toString();
    }
}
