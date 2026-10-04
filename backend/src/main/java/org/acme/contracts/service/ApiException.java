package org.acme.contracts.service;

/** 业务规则被违反时抛出，映射为 HTTP 400/409。 */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public static ApiException badRequest(String msg) {
        return new ApiException(400, msg);
    }

    public static ApiException conflict(String msg) {
        return new ApiException(409, msg);
    }

    public static ApiException forbidden(String msg) {
        return new ApiException(403, msg);
    }

    public static ApiException notFound(String msg) {
        return new ApiException(404, msg);
    }

    public int status() {
        return status;
    }
}
