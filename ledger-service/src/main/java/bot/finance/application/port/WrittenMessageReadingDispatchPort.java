package bot.finance.application.port;

public interface WrittenMessageReadingDispatchPort {

    /** Asks the reader to look for work now. Never throws. */
    void dispatch();
}
