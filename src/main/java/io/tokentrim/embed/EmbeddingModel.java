package io.tokentrim.embed;

public interface EmbeddingModel {

    float[] embed(String text);

    int dimensions();

    String name();
}
