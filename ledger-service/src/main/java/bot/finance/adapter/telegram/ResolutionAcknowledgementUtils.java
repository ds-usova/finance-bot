package bot.finance.adapter.telegram;

import bot.finance.application.dto.ResolutionAcknowledgement;

public final class ResolutionAcknowledgementUtils {

    private ResolutionAcknowledgementUtils() {}

    public static String render(ResolutionAcknowledgement ack) {
        return switch (ack.outcome()) {
            case ACCEPTED -> "Confirmed " + expenseCount(ack.count()) + ".";
            case DISCARDED -> "Deleted " + expenseCount(ack.count()) + ".";
            case ALREADY_ACCEPTED -> "Already confirmed: " + expenseCount(ack.count()) + ".";
            case NOTHING_TO_RESOLVE -> "There is nothing left to resolve.";
        };
    }

    private static String expenseCount(int count) {
        return count + " expense" + (count == 1 ? "" : "s");
    }
}
