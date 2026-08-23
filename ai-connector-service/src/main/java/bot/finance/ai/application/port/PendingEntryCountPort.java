package bot.finance.ai.application.port;

/** The change stream's group's delivered-but-unacknowledged entry count. */
public interface PendingEntryCountPort {

    double count();
}
