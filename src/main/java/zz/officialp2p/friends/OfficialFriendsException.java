package zz.officialp2p.friends;

public final class OfficialFriendsException extends RuntimeException {
    private final int statusCode;
    private final String operation;

    public OfficialFriendsException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.operation = "request";
    }

    private OfficialFriendsException(String operation, int statusCode, String body) {
        super("Official friends " + operation + " failed with HTTP " + statusCode + ": " + body);
        this.statusCode = statusCode;
        this.operation = operation;
    }

    public static OfficialFriendsException fromHttpStatus(String operation, int statusCode, String body) {
        return new OfficialFriendsException(operation, statusCode, body);
    }

    public String userMessage() {
        return switch (statusCode) {
            case 400 -> operation + " failed: name or profile does not exist";
            case 401 -> operation + " failed: login token was rejected";
            case 403 -> operation + " failed: account/session is forbidden by official friends service";
            case 429 -> operation + " failed: official friends service rate limit";
            case 500, 502, 503, 504 -> operation + " failed: official friends service is unavailable";
            default -> getMessage();
        };
    }
}
