package bot.finance.adapter.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.common.fixtures.TelegramLoginPayloads;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TelegramLoginVerifierTest {

    private static final String BOT_TOKEN = "123456:AA-login-widget-test-token";
    private static final String EXTERNAL_ID = "987654321";
    private static final Duration MAX_AGE = Duration.ofDays(1);

    private final Instant now = Instant.parse("2026-08-06T12:00:00Z");

    private final TelegramLoginVerifier verifier = new TelegramLoginVerifier(
            new TelegramBotProperties(BOT_TOKEN, "https://api.telegram.org/bot", null),
            new TelegramLoginProperties(MAX_AGE));

    @Nested
    @DisplayName("accepting a genuine payload")
    class Accept {

        @Test
        @DisplayName("when the payload is signed with the bot token - then verify() returns the Telegram user id")
        void whenThePayloadIsSignedWithTheBotToken_thenVerifyReturnsTheTelegramUserId() {
            Map<String, String> payload = TelegramLoginPayloads.signedPayload(BOT_TOKEN, EXTERNAL_ID, now);

            assertThat(verifier.verify(payload, now)).isEqualTo(EXTERNAL_ID);
        }

        @Test
        @DisplayName(
                "when the payload carries a field this service does not know - then it is signed over and accepted")
        void whenThePayloadCarriesAFieldThisServiceDoesNotKnow_thenItIsSignedOverAndAccepted() {
            Map<String, String> fields = mutablePayload();
            fields.put("some_future_field", "a value nothing here understands");

            Map<String, String> payload = TelegramLoginPayloads.sign(BOT_TOKEN, fields);

            assertThat(verifier.verify(payload, now)).isEqualTo(EXTERNAL_ID);
        }

        @Test
        @DisplayName("when the payload is signed just inside telegram.login.max-age - then it is accepted")
        void whenThePayloadIsSignedJustInsideMaxAge_thenItIsAccepted() {
            Instant signedAt = now.minus(MAX_AGE).plusSeconds(1);

            Map<String, String> payload = TelegramLoginPayloads.signedPayload(BOT_TOKEN, EXTERNAL_ID, signedAt);

            assertThat(verifier.verify(payload, now)).isEqualTo(EXTERNAL_ID);
        }
    }

    @Nested
    @DisplayName("rejecting a payload that is not Telegram's")
    class RejectSignature {

        @Test
        @DisplayName("when a field is changed after signing - then verify() rejects the payload")
        void whenAFieldIsChangedAfterSigning_thenVerifyRejectsThePayload() {
            Map<String, String> payload = mutableSignedPayload();
            payload.put("first_name", "Grace");

            assertThatThrownBy(() -> verifier.verify(payload, now)).isInstanceOf(TelegramLoginRejectedException.class);
        }

        @Test
        @DisplayName("when the payload was signed with another bot token - then verify() rejects the payload")
        void whenThePayloadWasSignedWithAnotherBotToken_thenVerifyRejectsThePayload() {
            Map<String, String> payload =
                    TelegramLoginPayloads.signedPayload("999999:BB-some-other-bot-token", EXTERNAL_ID, now);

            assertThatThrownBy(() -> verifier.verify(payload, now)).isInstanceOf(TelegramLoginRejectedException.class);
        }

        @Test
        @DisplayName("when an unknown field is added after signing - then verify() rejects the payload")
        void whenAnUnknownFieldIsAddedAfterSigning_thenVerifyRejectsThePayload() {
            Map<String, String> payload = mutableSignedPayload();
            payload.put("injected_field", "not covered by the signature");

            assertThatThrownBy(() -> verifier.verify(payload, now)).isInstanceOf(TelegramLoginRejectedException.class);
        }

        @Test
        @DisplayName("when the payload carries no hash - then verify() rejects the payload")
        void whenThePayloadCarriesNoHash_thenVerifyRejectsThePayload() {
            Map<String, String> payload = mutablePayload();

            assertThatThrownBy(() -> verifier.verify(payload, now)).isInstanceOf(TelegramLoginRejectedException.class);
        }

        @Test
        @DisplayName("when the hash is not hexadecimal - then verify() rejects the payload")
        void whenTheHashIsNotHexadecimal_thenVerifyRejectsThePayload() {
            Map<String, String> payload = mutableSignedPayload();
            payload.put("hash", "not a hash at all");

            assertThatThrownBy(() -> verifier.verify(payload, now)).isInstanceOf(TelegramLoginRejectedException.class);
        }
    }

    @Nested
    @DisplayName("rejecting a payload that is no longer usable")
    class RejectFreshness {

        @Test
        @DisplayName("when the payload is older than telegram.login.max-age - then verify() rejects it")
        void whenThePayloadIsOlderThanMaxAge_thenVerifyRejectsIt() {
            Instant signedAt = now.minus(MAX_AGE).minusSeconds(1);

            Map<String, String> payload = TelegramLoginPayloads.signedPayload(BOT_TOKEN, EXTERNAL_ID, signedAt);

            assertThatThrownBy(() -> verifier.verify(payload, now)).isInstanceOf(TelegramLoginRejectedException.class);
        }

        @Test
        @DisplayName("when the payload is dated in the future - then verify() rejects it")
        void whenThePayloadIsDatedInTheFuture_thenVerifyRejectsIt() {
            Map<String, String> payload =
                    TelegramLoginPayloads.signedPayload(BOT_TOKEN, EXTERNAL_ID, now.plusSeconds(60));

            assertThatThrownBy(() -> verifier.verify(payload, now)).isInstanceOf(TelegramLoginRejectedException.class);
        }

        @Test
        @DisplayName("when auth_date is not epoch seconds - then verify() rejects the payload")
        void whenAuthDateIsNotEpochSeconds_thenVerifyRejectsThePayload() {
            Map<String, String> fields = mutablePayload();
            fields.put("auth_date", "yesterday");

            Map<String, String> payload = TelegramLoginPayloads.sign(BOT_TOKEN, fields);

            assertThatThrownBy(() -> verifier.verify(payload, now)).isInstanceOf(TelegramLoginRejectedException.class);
        }

        @Test
        @DisplayName("when the payload carries no auth_date - then verify() rejects it")
        void whenThePayloadCarriesNoAuthDate_thenVerifyRejectsIt() {
            Map<String, String> fields = mutablePayload();
            fields.remove("auth_date");

            Map<String, String> payload = TelegramLoginPayloads.sign(BOT_TOKEN, fields);

            assertThatThrownBy(() -> verifier.verify(payload, now)).isInstanceOf(TelegramLoginRejectedException.class);
        }
    }

    @Nested
    @DisplayName("rejecting a payload that identifies nobody")
    class RejectIdentity {

        @Test
        @DisplayName("when the signed payload's id is blank - then verify() rejects it")
        void whenTheSignedPayloadsIdIsBlank_thenVerifyRejectsIt() {
            Map<String, String> fields = mutablePayload();
            fields.put("id", "  ");

            Map<String, String> payload = TelegramLoginPayloads.sign(BOT_TOKEN, fields);

            assertThatThrownBy(() -> verifier.verify(payload, now)).isInstanceOf(TelegramLoginRejectedException.class);
        }

        @Test
        @DisplayName("when the signed payload carries no id - then verify() rejects it")
        void whenTheSignedPayloadCarriesNoId_thenVerifyRejectsIt() {
            Map<String, String> fields = mutablePayload();
            fields.remove("id");

            Map<String, String> payload = TelegramLoginPayloads.sign(BOT_TOKEN, fields);

            assertThatThrownBy(() -> verifier.verify(payload, now)).isInstanceOf(TelegramLoginRejectedException.class);
        }
    }

    private Map<String, String> mutablePayload() {
        Map<String, String> fields = new LinkedHashMap<>(mutableSignedPayload());
        fields.remove("hash");
        return fields;
    }

    private Map<String, String> mutableSignedPayload() {
        return new LinkedHashMap<>(TelegramLoginPayloads.signedPayload(BOT_TOKEN, EXTERNAL_ID, now));
    }
}
