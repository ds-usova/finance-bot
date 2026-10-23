package bot.finance.eval.ledger;

public record WrittenMessage(long id, String state, String outcome) {

    public boolean done() {
        return "DONE".equals(state);
    }

    /** The id the ledger files the message's entries and MCP calls under. */
    public static String incomingMessageId(long writtenMessageId) {
        return "web:" + writtenMessageId;
    }
}
