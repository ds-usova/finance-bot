package bot.finance.application.port;

public interface TurnHandOverDispatchPort {

    /** Asks the hand-over to look for work now. Never throws. */
    void dispatch();
}
