package io.tokentrim.embed;

public final class VectorMath {

    private VectorMath() {
    }

    public static float[] normalize(float[] vector) {
        if (vector == null || vector.length == 0) {
            return vector == null ? new float[0] : vector;
        }
        double sum = 0;
        for (float value : vector) {
            sum += value * (double) value;
        }
        if (sum == 0) {
            return vector;
        }
        float inverse = (float) (1.0 / Math.sqrt(sum));
        float[] out = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            out[i] = vector[i] * inverse;
        }
        return out;
    }

    public static double cosine(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || left.length != right.length) {
            return 0;
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * (double) right[i];
            leftNorm += left[i] * (double) left[i];
            rightNorm += right[i] * (double) right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
