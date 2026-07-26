package bot.finance.ai.adapter.grpc;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.ai.domain.value.Intent;

import java.util.List;

public final class IntentProtoUtils {

    private IntentProtoUtils() {
    }

    public static ExtractIntentsResponse toResponse(List<Intent> intents) {
        // maps each domain Intent to a proto Intent via an exhaustive switch over the sealed hierarchy:
        // CategoryIntent and ExpenseIntent set their operation and their oneof payload (Money's minor
        // units + currency for an expense amount), UnknownIntent sets OPERATION_UNKNOWN and the reason
        // with no payload; assembles the repeated field preserving list order
        return null;
    }

}
