package bot.finance.adapter.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.adapter.telegram.ProposalCallbackData.ParsedCallback;
import bot.finance.application.dto.ProposalResolution;
import bot.finance.domain.value.IncomingMessageId;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProposalCallbackDataTest {

    private static final IncomingMessageId REFERENCE = IncomingMessageId.of("conversation-1", "42");

    @Nested
    @DisplayName("rendering a resolution and a reference into a callback payload")
    class Render {

        @Test
        @DisplayName(
                "when ACCEPT and a reference are rendered - then returns accept: followed by that reference's derived value")
        void whenAcceptAndReferenceAreRendered_thenReturnsAcceptFollowedByCanonicalUuidText() {
            String payload = ProposalCallbackData.render(ProposalResolution.ACCEPT, REFERENCE);

            assertThat(payload).isEqualTo("accept:" + REFERENCE.value());
        }

        @Test
        @DisplayName(
                "when DISCARD and a reference are rendered - then returns discard: followed by that reference's derived value")
        void whenDiscardAndReferenceAreRendered_thenReturnsDiscardFollowedByCanonicalUuidText() {
            String payload = ProposalCallbackData.render(ProposalResolution.DISCARD, REFERENCE);

            assertThat(payload).isEqualTo("discard:" + REFERENCE.value());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("resolutionsWithExpectedByteLength")
        @DisplayName(
                "when either resolution is rendered - then the payload fits the Bot API's 64-byte callback_data limit")
        void whenEitherResolutionAndReferenceAreRendered_thenResultIsAtMost64Bytes(
                ProposalResolution resolution, int expectedByteLength) {
            String payload = ProposalCallbackData.render(resolution, REFERENCE);

            int byteLength = payload.getBytes(StandardCharsets.UTF_8).length;
            assertThat(byteLength).isEqualTo(expectedByteLength);
            assertThat(byteLength).isLessThanOrEqualTo(64);
        }

        static Stream<Arguments> resolutionsWithExpectedByteLength() {
            return Stream.of(Arguments.of(ProposalResolution.ACCEPT, 24), Arguments.of(ProposalResolution.DISCARD, 25));
        }
    }

    @Nested
    @DisplayName("parsing a callback payload back into a resolution and a reference")
    class Parse {

        @Test
        @DisplayName(
                "when parsing render(ACCEPT, reference)'s output - then returns ACCEPT and the same reference, derived or legacy")
        void whenParsingRenderAcceptOutput_thenReturnsAcceptAndSameReferenceDerivedOrLegacy() {
            String payload = ProposalCallbackData.render(ProposalResolution.ACCEPT, REFERENCE);

            Optional<ParsedCallback> parsed = ProposalCallbackData.parse(payload);

            assertThat(parsed).contains(new ParsedCallback(ProposalResolution.ACCEPT, REFERENCE));

            IncomingMessageId legacyReference =
                    IncomingMessageId.of(UUID.randomUUID().toString());
            String legacyPayload = ProposalCallbackData.render(ProposalResolution.ACCEPT, legacyReference);

            Optional<ParsedCallback> parsedLegacy = ProposalCallbackData.parse(legacyPayload);

            assertThat(parsedLegacy).contains(new ParsedCallback(ProposalResolution.ACCEPT, legacyReference));
        }

        @Test
        @DisplayName(
                "when parsing what render(DISCARD, reference) produced - then returns DISCARD and that same reference")
        void whenParsingPayloadRenderDiscardProduced_thenReturnsParsedCallbackCarryingDiscardAndSameReference() {
            String payload = ProposalCallbackData.render(ProposalResolution.DISCARD, REFERENCE);

            Optional<ParsedCallback> parsed = ProposalCallbackData.parse(payload);

            assertThat(parsed).contains(new ParsedCallback(ProposalResolution.DISCARD, REFERENCE));
        }

        @Test
        @DisplayName("when parsing accept:777:123 - then returns ACCEPT and that id, and a payload with no colon "
                + "at all still answers empty")
        void whenParsingAcceptColonNotAUuid_thenReturnsEmptyAndThrowsNothing() {
            Optional<ParsedCallback> parsed = ProposalCallbackData.parse("accept:777:123");

            assertThat(parsed).contains(new ParsedCallback(ProposalResolution.ACCEPT, IncomingMessageId.of("777:123")));

            assertThat(ProposalCallbackData.parse("no-colon-here")).isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"resolve:", "ACCEPT:"})
        @DisplayName("when the payload's verb is neither accept nor discard - then returns empty")
        void whenPayloadVerbIsNeitherAcceptNorDiscard_thenReturnsEmpty(String verb) {
            Optional<ParsedCallback> parsed = ProposalCallbackData.parse(verb + REFERENCE.value());

            assertThat(parsed).isEmpty();
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("unusablePayloads")
        @DisplayName("when the payload is null, blank, has no colon or is a bare accept: - then returns empty")
        void whenPayloadIsNullBlankHasNoColonOrIsBareAccept_thenReturnsEmpty(String payload) {
            Optional<ParsedCallback> parsed = ProposalCallbackData.parse(payload);

            assertThat(parsed).isEmpty();
        }

        static Stream<Arguments> unusablePayloads() {
            return Stream.of(
                    Arguments.of((Object) null),
                    Arguments.of(""),
                    Arguments.of("   "),
                    Arguments.of("no-colon-here"),
                    Arguments.of("accept:"));
        }
    }
}
