package bot.finance.ai.application.usecase;

import bot.finance.ai.application.dto.AcceptTurnCommand;
import bot.finance.ai.application.dto.TurnAcceptance;
import bot.finance.ai.application.port.AcceptTurnPort;
import bot.finance.ai.application.port.ExtractIntentsPort;
import bot.finance.ai.application.port.TurnEndPort;
import bot.finance.ai.application.port.TurnRunnerPort;

public class AcceptTurnUseCase implements AcceptTurnPort {

    private final ExtractIntentsPort extractIntentsPort;
    private final TurnRunnerPort turnRunnerPort;
    private final TurnEndPort turnEndPort;

    public AcceptTurnUseCase(
            ExtractIntentsPort extractIntentsPort, TurnRunnerPort turnRunnerPort, TurnEndPort turnEndPort) {
        this.extractIntentsPort = extractIntentsPort;
        this.turnRunnerPort = turnRunnerPort;
        this.turnEndPort = turnEndPort;
    }

    @Override
    public TurnAcceptance accept(AcceptTurnCommand command) {
        // stub-intent: hands the runner the command's turn key, a turn running the command's turn through
        // ExtractIntentsPort and mapping how it ended, and an end reported through TurnEndPort; answers BUSY when the
        // runner is full, ACCEPTED when it started the turn or one under that key is already running
        return null;
    }
}
