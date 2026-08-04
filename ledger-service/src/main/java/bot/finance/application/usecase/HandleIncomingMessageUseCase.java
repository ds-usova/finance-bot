package bot.finance.application.usecase;

import bot.finance.application.dto.HandleIncomingMessageCommand;
import bot.finance.application.dto.InitializeUserCommand;
import bot.finance.application.dto.IntentExtractionRequest;
import bot.finance.application.dto.ProposalReport;
import bot.finance.application.dto.ProposalSummary;
import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.port.ExpenseProposalRepository;
import bot.finance.application.port.GroupingRepository;
import bot.finance.application.port.HandleIncomingMessagePort;
import bot.finance.application.port.InitializeUserPort;
import bot.finance.application.port.IntentExtractionPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.MessageDeliveryPort;
import bot.finance.domain.exception.CatchAllGroupingMissingException;
import bot.finance.domain.exception.IntentExtractionFailedException;
import bot.finance.domain.exception.InvalidIncomingMessageException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.Grouping;
import bot.finance.domain.value.MessageReference;
import java.util.List;
import java.util.Optional;

public class HandleIncomingMessageUseCase implements HandleIncomingMessagePort {

    private final InitializeUserPort initializeUserPort;
    private final GroupingRepository groupingRepository;
    private final IntentExtractionPort intentExtractionPort;
    private final ExpenseProposalRepository expenseProposalRepository;
    private final MessageDeliveryPort messageDeliveryPort;
    private final Logger log;

    public HandleIncomingMessageUseCase(
            InitializeUserPort initializeUserPort,
            GroupingRepository groupingRepository,
            IntentExtractionPort intentExtractionPort,
            ExpenseProposalRepository expenseProposalRepository,
            MessageDeliveryPort messageDeliveryPort,
            LoggerFactory loggerFactory) {
        this.initializeUserPort = initializeUserPort;
        this.groupingRepository = groupingRepository;
        this.intentExtractionPort = intentExtractionPort;
        this.expenseProposalRepository = expenseProposalRepository;
        this.messageDeliveryPort = messageDeliveryPort;
        this.log = loggerFactory.getLogger(HandleIncomingMessageUseCase.class);
    }

    @Override
    public void handle(HandleIncomingMessageCommand command) {
        if (command == null) {
            throw new InvalidIncomingMessageException("incoming message is absent");
        }

        log.debug("handling message: {}", command.text());
        User user = initializeUserPort.initialize(new InitializeUserCommand(command.userExternalId()));
        List<String> categoryGroupings =
                groupingRepository.findNamesWithCategories(user.id().orElseThrow());

        MessageReference reference = MessageReference.newReference();
        boolean extractionFailed = extract(command, categoryGroupings, user, reference);

        List<ProposalSummary> proposals = expenseProposalRepository.findSummariesByMessageReference(
                user.id().orElseThrow(), reference);
        ReportOutcome outcome = outcomeFor(extractionFailed, proposals);
        if (extractionFailed) {
            log.error("intent extraction failed for message {}, outcome {}", reference, outcome);
        }
        messageDeliveryPort.deliver(new ProposalReport(
                command.conversationId(), command.inboundMessageId(), outcome, proposals, reference));
        log.info("delivered report for message {} to user {}", reference, user.externalId());
    }

    private boolean extract(
            HandleIncomingMessageCommand command,
            List<String> categoryGroupings,
            User user,
            MessageReference reference) {
        try {
            intentExtractionPort.extract(new IntentExtractionRequest(
                    command.text(),
                    categoryGroupings,
                    catchAllGrouping(categoryGroupings),
                    Optional.empty(),
                    user.externalId(),
                    reference));
            return false;
        } catch (IntentExtractionFailedException e) {
            return true;
        }
    }

    private String catchAllGrouping(List<String> categoryGroupings) {
        String designated = Grouping.catchAllName();
        if (!categoryGroupings.contains(designated)) {
            throw new CatchAllGroupingMissingException("no grouping named " + designated + " is stored for this user");
        }
        return designated;
    }

    private ReportOutcome outcomeFor(boolean extractionFailed, List<ProposalSummary> proposals) {
        if (extractionFailed) {
            return proposals.isEmpty() ? ReportOutcome.FAILED : ReportOutcome.PARTIAL;
        }
        return proposals.isEmpty() ? ReportOutcome.NOTHING_IDENTIFIED : ReportOutcome.RECORDED;
    }
}
