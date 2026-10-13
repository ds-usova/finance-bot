package bot.finance.adapter.logging;

import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import java.util.LinkedHashMap;
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
        // stub-intent: where enabled, logs one turn_call line naming the operation, the way it arrived, the
        // outcome, the authenticated caller and message or none, and the arguments as JSON; otherwise nothing
    }

    public static Map<String, String> arguments(String... namesAndValues) {
        Map<String, String> arguments = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            arguments.put(namesAndValues[i], namesAndValues[i + 1]);
        }
        return arguments;
    }
}
