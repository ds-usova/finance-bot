package bot.finance.domain.value;

public record AuthenticatedUserId(String externalId) {

    public AuthenticatedUserId {
        // rejects an absent, empty or whitespace-only external id with InvalidUserException
    }
}
