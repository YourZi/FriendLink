package zz.officialp2p.diagnostics;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public record P2PStatus(
    String playerName,
    String profileId,
    boolean hasAccessToken,
    boolean hasXuid,
    boolean hasClientId,
    boolean webRtcApiPresent,
    List<String> missingClasses
) {
    public List<String> toChatLines() {
        List<String> lines = new ArrayList<>();
        lines.add("[OfficialP2P] player=" + playerName + " uuid=" + profileId);
        lines.add("[OfficialP2P] accessToken=" + yesNo(hasAccessToken) + " xuid=" + yesNo(hasXuid) + " clientId=" + yesNo(hasClientId));
        lines.add("[OfficialP2P] WebRTC api=" + yesNo(webRtcApiPresent));
        if (!missingClasses.isEmpty()) {
            lines.add("[OfficialP2P] missing=" + String.join(", ", missingClasses));
        }
        return lines;
    }

    public void log(Logger logger) {
        for (String line : toChatLines()) {
            logger.info(line);
        }
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
