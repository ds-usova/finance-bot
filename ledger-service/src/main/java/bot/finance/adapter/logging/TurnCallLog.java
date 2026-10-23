package bot.finance.adapter.logging;

import bot.finance.adapter.security.AuthenticatedCaller;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TurnCallLog {

    public enum Via {
        MCP,
        GRPC
    }

    private final TurnCallLogProperties properties;
    private final JsonMapper jsonMapper;
    private final Logger log;

    public TurnCallLog(TurnCallLogProperties properties, JsonMapper jsonMapper, LoggerFactory loggerFactory) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.log = loggerFactory.getLogger(TurnCallLog.class);
    }

    public void write(String operation, Via via, boolean accepted, Object arguments) {
        if (!properties.enabled()) {
            return;
        }
        log.debug(
                "turn_call operation={} via={} outcome={} userId={} message={} arguments={}",
                operation,
                via.name().toLowerCase(Locale.ROOT),
                accepted ? "ok" : "rejected",
                callerOrNone(),
                messageOrNone(),
                jsonMapper.writeValueAsString(arguments));
    }

    private static String callerOrNone() {
        try {
            return String.valueOf(AuthenticatedCaller.authenticatedUserId().userId());
        } catch (RuntimeException e) {
            return "none";
        }
    }

    private static String messageOrNone() {
        try {
            return AuthenticatedCaller.incomingMessageId().value();
        } catch (RuntimeException e) {
            return "none";
        }
    }

    public static Map<String, String> arguments(String... namesAndValues) {
        Map<String, String> arguments = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            arguments.put(namesAndValues[i], namesAndValues[i + 1]);
        }
        return arguments;
    }
}
