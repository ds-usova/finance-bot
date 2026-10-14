package bot.finance.ai.application.usecase;

import bot.finance.ai.application.dto.AcceptTurnCommand;
import bot.finance.ai.application.dto.ExtractIntentsCommand;
import bot.finance.ai.application.dto.TurnAcceptance;
import bot.finance.ai.application.dto.TurnStart;
import bot.finance.ai.application.port.AcceptTurnPort;
import bot.finance.ai.application.port.ExtractIntentsPort;
import bot.finance.ai.application.port.TurnEndPort;
import bot.finance.ai.application.port.TurnRunnerPort;
import bot.finance.ai.domain.exception.ExpenseRecordingFailedException;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.TurnEnd;

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
        if (command == null) {
            throw new InvalidValueException("Accept turn command must not be null");
        }

        TurnStart start = turnRunnerPort.start(command.turnKey(), () -> runTurn(command.turn()), turnEndPort::report);

        return start == TurnStart.BUSY ? TurnAcceptance.BUSY : TurnAcceptance.ACCEPTED;
    }

    private TurnEnd runTurn(ExtractIntentsCommand turn) {
        try {
            extractIntentsPort.extractIntents(turn);
            return TurnEnd.ACTED_ON;
        } catch (ExpenseRecordingFailedException e) {
            return TurnEnd.UNAVAILABLE;
        } catch (RuntimeException e) {
            return TurnEnd.FAILED;
        }
    }
}
