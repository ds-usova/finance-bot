package bot.finance.adapter.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import bot.finance.adapter.telegram.ProposalCallbackData.ParsedCallback;
import bot.finance.application.dto.ProposalResolution;
import bot.finance.domain.value.MessageReference;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProposalCallbackDataTest {

    private static final MessageReference REFERENCE = MessageReference.newReference();

    @Nested
    @DisplayName("rendering a resolution and a reference into a callback payload")
    class Render {

        @Test
        @DisplayName(
                "when ACCEPT and a reference are rendered - then returns accept: followed by that reference's canonical UUID text")
        void whenAcceptAndReferenceAreRendered_thenReturnsAcceptFollowedByCanonicalUuidText() {
            String payload = ProposalCallbackData.render(ProposalResolution.ACCEPT, REFERENCE);

            assertThat(payload).isEqualTo("accept:" + REFERENCE.value());
        }

        @Test
        @DisplayName(
                "when DISCARD and a reference are rendered - then returns discard: followed by that reference's canonical UUID text")
        void whenDiscardAndReferenceAreRendered_thenReturnsDiscardFollowedByCanonicalUuidText() {
            String payload = ProposalCallbackData.render(ProposalResolution.DISCARD, REFERENCE);

            assertThat(payload).isEqualTo("discard:" + REFERENCE.value());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("resolutionsWithExpectedByteLength")
        @DisplayName(
                "when either resolution and a reference are rendered - then the result is at most 64 bytes, the Bot API's callback_data limit")
        void whenEitherResolutionAndReferenceAreRendered_thenResultIsAtMost64Bytes(
                ProposalResolution resolution, int expectedByteLength) {
            String payload = ProposalCallbackData.render(resolution, REFERENCE);

            int byteLength = payload.getBytes(StandardCharsets.UTF_8).length;
            assertThat(byteLength).isEqualTo(expectedByteLength);
            assertThat(byteLength).isLessThanOrEqualTo(64);
        }

        static Stream<Arguments> resolutionsWithExpectedByteLength() {
            return Stream.of(Arguments.of(ProposalResolution.ACCEPT, 43), Arguments.of(ProposalResolution.DISCARD, 44));
        }
    }

    @Nested
    @DisplayName("parsing a callback payload back into a resolution and a reference")
    class Parse {

        @Test
        @DisplayName(
                "when parsing the payload render(ACCEPT, reference) produced - then returns a ParsedCallback carrying ACCEPT and that same reference")
        void whenParsingPayloadRenderAcceptProduced_thenReturnsParsedCallbackCarryingAcceptAndSameReference() {
            String payload = ProposalCallbackData.render(ProposalResolution.ACCEPT, REFERENCE);

            Optional<ParsedCallback> parsed = ProposalCallbackData.parse(payload);

            assertThat(parsed).contains(new ParsedCallback(ProposalResolution.ACCEPT, REFERENCE));
        }

        @Test
        @DisplayName(
                "when parsing the payload render(DISCARD, reference) produced - then returns a ParsedCallback carrying DISCARD and that same reference")
        void whenParsingPayloadRenderDiscardProduced_thenReturnsParsedCallbackCarryingDiscardAndSameReference() {
            String payload = ProposalCallbackData.render(ProposalResolution.DISCARD, REFERENCE);

            Optional<ParsedCallback> parsed = ProposalCallbackData.parse(payload);

            assertThat(parsed).contains(new ParsedCallback(ProposalResolution.DISCARD, REFERENCE));
        }

        @Test
        @DisplayName("when parsing accept:not-a-uuid - then returns empty and throws nothing")
        void whenParsingAcceptColonNotAUuid_thenReturnsEmptyAndThrowsNothing() {
            assertThatNoException().isThrownBy(() -> {
                Optional<ParsedCallback> parsed = ProposalCallbackData.parse("accept:not-a-uuid");
                assertThat(parsed).isEmpty();
            });
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
