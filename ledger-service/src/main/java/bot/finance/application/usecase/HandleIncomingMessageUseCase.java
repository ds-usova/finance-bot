package bot.finance.application.usecase;

import bot.finance.application.dto.HandleIncomingMessageCommand;
import bot.finance.application.dto.InitializeUserCommand;
import bot.finance.application.dto.ProposalSummary;
import bot.finance.application.dto.ReportLocation;
import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.dto.SpendingSummary;
import bot.finance.application.dto.TurnReport;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.GroupingRepository;
import bot.finance.application.port.HandleIncomingMessagePort;
import bot.finance.application.port.InitializeUserPort;
import bot.finance.application.port.IntentExtractionPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.MessageDeliveryPort;
import bot.finance.application.port.ProposalReportRepository;
import bot.finance.application.port.SpendingQueryRepository;
import bot.finance.application.port.TurnMeters;
import bot.finance.application.port.UserPreferenceRepository;
import bot.finance.domain.exception.InvalidIncomingMessageException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.ProposalReport;
import bot.finance.domain.model.User;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.SpendingPeriod;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

public class HandleIncomingMessageUseCase implements HandleIncomingMessagePort {

    private final InitializeUserPort initializeUserPort;
    private final GroupingRepository groupingRepository;
    private final MessageDeliveryPort messageDeliveryPort;
    private final SpendingQueryRepository spendingQueryRepository;
    private final ExpenseRepository expenseRepository;
    private final ProposalReportRepository proposalReportRepository;
    private final TurnMeters turnMeters;
    private final Logger log;
    private final ExtractionTurn extractionTurn;

    public HandleIncomingMessageUseCase(
            InitializeUserPort initializeUserPort,
            GroupingRepository groupingRepository,
            IntentExtractionPort intentExtractionPort,
            MessageDeliveryPort messageDeliveryPort,
            Clock clock,
            SpendingQueryRepository spendingQueryRepository,
            ExpenseRepository expenseRepository,
            ProposalReportRepository proposalReportRepository,
            TurnMeters turnMeters,
            UserPreferenceRepository userPreferenceRepository,
            LoggerFactory loggerFactory) {
        this.initializeUserPort = initializeUserPort;
        this.groupingRepository = groupingRepository;
        this.messageDeliveryPort = messageDeliveryPort;
        this.spendingQueryRepository = spendingQueryRepository;
        this.expenseRepository = expenseRepository;
        this.proposalReportRepository = proposalReportRepository;
        this.turnMeters = turnMeters;
        this.log = loggerFactory.getLogger(HandleIncomingMessageUseCase.class);
        this.extractionTurn =
                new ExtractionTurn(userPreferenceRepository, intentExtractionPort, spendingQueryRepository, clock, log);
    }

    @Override
    public void handle(HandleIncomingMessageCommand command) {
        if (command == null) {
            throw new InvalidIncomingMessageException("incoming message is absent");
        }

        log.debug("handling message: {}", command.text());
        User user = initializeUserPort.initialize(new InitializeUserCommand(command.userExternalId()));
        Optional<CurrencyCode> defaultCurrency =
                extractionTurn.readDefaultCurrency(user.id().orElseThrow());
        List<String> categoryGroupings =
                groupingRepository.findNamesWithCategories(user.id().orElseThrow());

        IncomingMessageId reference = IncomingMessageId.of(command.conversationId(), command.inboundMessageId());
        String catchAll = extractionTurn.catchAllAmong(categoryGroupings);
        boolean extractionFailed = extractionTurn.extractionFailed(extractionTurn.request(
                command.text(),
                categoryGroupings,
                catchAll,
                defaultCurrency,
                user.id().orElseThrow(),
                reference));

        List<ProposalSummary> proposals =
                expenseRepository.findSummariesByMessageReference(user.id().orElseThrow(), reference);
        List<SpendingSummary> summaries = spendingSummaries(user.id().orElseThrow(), reference);

        ReportOutcome outcome = outcomeFor(extractionFailed, proposals, summaries);
        if (extractionFailed) {
            log.error("intent extraction failed for message {}, outcome {}", reference, outcome);
        }
        messageDeliveryPort
                .deliver(new TurnReport(
                        command.conversationId(), command.inboundMessageId(), outcome, proposals, summaries, reference))
                .ifPresent(location -> storeReport(user.id().orElseThrow(), reference, location));
        turnMeters.countTurn(outcome);
        log.info("delivered report for message {} to user {}", reference, user.externalId());

        if (!summaries.isEmpty()) {
            extractionTurn.discardPeriods(user.id().orElseThrow(), reference);
        }
    }

    private void storeReport(long userId, IncomingMessageId reference, ReportLocation location) {
        try {
            proposalReportRepository.store(ProposalReport.newProposalReport(
                    userId, reference, location.conversationId(), location.sentMessageId()));
        } catch (PersistenceFailedException e) {
            log.warn("failed to store report for message {}: {}", reference, e.getMessage());
        }
    }

    private List<SpendingSummary> spendingSummaries(long userId, IncomingMessageId reference) {
        List<SpendingPeriod> periods = spendingQueryRepository.findPeriodsByMessageReference(userId, reference);
        return periods.stream()
                .map(period -> new SpendingSummary(period, expenseRepository.totalsByCurrency(userId, period)))
                .toList();
    }

    private ReportOutcome outcomeFor(
            boolean extractionFailed, List<ProposalSummary> proposals, List<SpendingSummary> summaries) {
        if (extractionFailed) {
            return proposals.isEmpty() && summaries.isEmpty() ? ReportOutcome.FAILED : ReportOutcome.PARTIAL;
        }
        if (!proposals.isEmpty()) {
            return ReportOutcome.RECORDED;
        }
        return summaries.isEmpty() ? ReportOutcome.NOTHING_IDENTIFIED : ReportOutcome.ANSWERED;
    }
}
