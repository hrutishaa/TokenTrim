package io.tokentrim.upstream;

public class UpstreamException extends RuntimeException {

    private final int status;
    private final String body;

    public UpstreamException(int status, String body) {
        super("Upstream responded with HTTP " + status);
        this.status = status;
        this.body = body == null ? "" : body;
    }

    public int status() {
        return status;
    }

    public String body() {
        return body;
    }
}
