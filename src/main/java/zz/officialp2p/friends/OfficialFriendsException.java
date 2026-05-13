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
            case 400 -> "玩家名或档案不存在";
            case 401 -> "登录令牌被拒绝，请重新登录";
            case 403 -> "账号或隐私设置不允许使用官方好友服务";
            case 429 -> "官方好友服务请求过快，请等一会再刷新";
            case 500, 502, 503, 504 -> "官方好友服务暂时不可用";
            default -> getMessage();
        };
    }
}
