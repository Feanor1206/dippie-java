package com.dippie.ai;

public class AiUpstreamException extends RuntimeException {

    private final int statusCode;
    private final String contentType;
    private final byte[] responseBody;

    public AiUpstreamException(int statusCode, String contentType, byte[] responseBody) {
        super("Upstream AI service responded with HTTP " + statusCode);
        this.statusCode = statusCode;
        this.contentType = contentType;
        this.responseBody = responseBody == null ? new byte[0] : responseBody.clone();
    }

    public int statusCode() {
        return statusCode;
    }

    public String contentType() {
        return contentType;
    }

    public byte[] responseBody() {
        return responseBody.clone();
    }
}
