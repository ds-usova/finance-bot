package bot.finance.adapter.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import bot.finance.application.dto.ResolutionAcknowledgement;
import bot.finance.application.dto.ResolutionOutcome;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class ResolutionAcknowledgementRendererTest {

    private static final String CONVERSATION_ID = "555";
    private static final String REPORT_MESSAGE_ID = "1";
    private static final String INTERACTION_ID = "callback-query-id";

    @Nested
    @DisplayName("rendering an acknowledgement into its wording")
    class Render {

        @ParameterizedTest(name = "{0}")
        @MethodSource("acceptedAcknowledgements")
        @DisplayName("when ACCEPTED carries a count of 2, and one of 1 - then pluralises Confirmed n expense(s).")
        void whenAcceptedAcknowledgementCarriesCountOf2And1_thenReturnsConfirmedExpensesPluralAndSingular(
                int count, String expected) {
            ResolutionAcknowledgement ack = new ResolutionAcknowledgement(
                    CONVERSATION_ID, REPORT_MESSAGE_ID, INTERACTION_ID, ResolutionOutcome.ACCEPTED, count);

            String text = ResolutionAcknowledgementRenderer.render(ack);

            assertThat(text).isEqualTo(expected);
        }

        static Stream<Arguments> acceptedAcknowledgements() {
            return Stream.of(arguments(2, "Confirmed 2 expenses."), arguments(1, "Confirmed 1 expense."));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("discardedAcknowledgements")
        @DisplayName("when DISCARDED carries a count of 2, and one of 1 - then pluralises Deleted n expense(s).")
        void whenDiscardedAcknowledgementCarriesCountOf2And1_thenReturnsDeletedExpensesPluralAndSingular(
                int count, String expected) {
            ResolutionAcknowledgement ack = new ResolutionAcknowledgement(
                    CONVERSATION_ID, REPORT_MESSAGE_ID, INTERACTION_ID, ResolutionOutcome.DISCARDED, count);

            String text = ResolutionAcknowledgementRenderer.render(ack);

            assertThat(text).isEqualTo(expected);
        }

        static Stream<Arguments> discardedAcknowledgements() {
            return Stream.of(arguments(2, "Deleted 2 expenses."), arguments(1, "Deleted 1 expense."));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("alreadyAcceptedAcknowledgements")
        @DisplayName(
                "when ALREADY_ACCEPTED carries a count of 2, and one of 1 - then pluralises Already confirmed: n expense(s).")
        void whenAlreadyAcceptedAcknowledgementCarriesCountOf2And1_thenReturnsAlreadyConfirmedExpensesPluralAndSingular(
                int count, String expected) {
            ResolutionAcknowledgement ack = new ResolutionAcknowledgement(
                    CONVERSATION_ID, REPORT_MESSAGE_ID, INTERACTION_ID, ResolutionOutcome.ALREADY_ACCEPTED, count);

            String text = ResolutionAcknowledgementRenderer.render(ack);

            assertThat(text).isEqualTo(expected);
        }

        static Stream<Arguments> alreadyAcceptedAcknowledgements() {
            return Stream.of(
                    arguments(2, "Already confirmed: 2 expenses."), arguments(1, "Already confirmed: 1 expense."));
        }

        @Test
        @DisplayName(
                "when a NOTHING_TO_RESOLVE acknowledgement carries a count of 0 - then returns There is nothing left to resolve.")
        void whenNothingToResolveAcknowledgementCarriesCountOf0_thenReturnsThereIsNothingLeftToResolve() {
            ResolutionAcknowledgement ack = new ResolutionAcknowledgement(
                    CONVERSATION_ID, REPORT_MESSAGE_ID, INTERACTION_ID, ResolutionOutcome.NOTHING_TO_RESOLVE, 0);

            String text = ResolutionAcknowledgementRenderer.render(ack);

            assertThat(text).isEqualTo("There is nothing left to resolve.");
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(ResolutionOutcome.class)
        @DisplayName(
                "when every outcome carries a count of 999 - then the wording fits answerCallbackQuery's 200-character limit")
        void whenEveryResolutionOutcomeCarriesCountOf999_thenResultIsAtMost200Characters(ResolutionOutcome outcome) {
            ResolutionAcknowledgement ack =
                    new ResolutionAcknowledgement(CONVERSATION_ID, REPORT_MESSAGE_ID, INTERACTION_ID, outcome, 999);

            String text = ResolutionAcknowledgementRenderer.render(ack);

            assertThat(text.length()).isLessThanOrEqualTo(200);
        }
    }
}
