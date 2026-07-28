package bot.finance.application.dto;

public record NewUser(String externalId) {

    public NewUser {
        // rejects an absent or blank external id with InvalidUserException
    }

}
