package bot.finance.ai.application.port;

public interface ChangeAttemptStorePort {

    int countFailure(String deliveryId, String error);

    void clear(String deliveryId);
}
