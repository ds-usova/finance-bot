package bot.finance.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bot.finance.application.dto.Preferences;
import bot.finance.application.dto.ReadPreferencesCommand;
import bot.finance.application.dto.ReplacePreferencesCommand;
import bot.finance.application.port.ReadPreferencesPort;
import bot.finance.application.port.ReplacePreferencesPort;
import bot.finance.common.boot.WebAdapterTest;
import bot.finance.common.fixtures.BrowserSessions;
import bot.finance.common.fixtures.JsonUtils;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.CurrencyCode;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration test for the inbound HTTP adapter. Enters through the protocol - MockMvc requests against
 * {@code /api/v1/preferences} - never by calling {@link PreferencesController}'s methods directly, since binding
 * lives in the generated {@link bot.finance.api.PreferencesApi} interface. Both {@link ReadPreferencesPort} and
 * {@link ReplacePreferencesPort} are mocked. It owns what this endpoint accepts and refuses;
 * {@link WebExceptionHandlerTest} owns only the mappings every controller shares.
 */
@WebAdapterTest
@WebMvcTest(PreferencesController.class)
class PreferencesControllerTest {

    private static final String PATH = "/api/v1/preferences";
    private static final long USER_ID = 445566778L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReadPreferencesPort readPreferencesPort;

    @MockitoBean
    private ReplacePreferencesPort replacePreferencesPort;

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("when the read port answers EUR - then the response is 200 with defaultCurrency EUR")
        void whenReadPortAnswersEur_thenResponseIs200WithEurAndPortCalledForAuthenticatedCaller() throws Exception {
            when(readPreferencesPort.read(any())).thenReturn(new Preferences(Optional.of(CurrencyCode.of("EUR"))));

            MvcResult result = mockMvc.perform(get(PATH).cookie(sessionCookie()))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(JsonUtils.readJson(result.getResponse().getContentAsString())
                            .get("defaultCurrency")
                            .asText())
                    .isEqualTo("EUR");

            ArgumentCaptor<ReadPreferencesCommand> command = ArgumentCaptor.forClass(ReadPreferencesCommand.class);
            verify(readPreferencesPort).read(command.capture());
            assertThat(command.getValue().userId()).isEqualTo(new AuthenticatedUserId(USER_ID));
        }

        @Test
        @DisplayName("when the read port answers preferences carrying no currency - then the response is 200 with "
                + "defaultCurrency null")
        void whenReadPortAnswersNoCurrency_thenResponseIs200WithDefaultCurrencyNull() throws Exception {
            when(readPreferencesPort.read(any())).thenReturn(new Preferences(Optional.empty()));

            MvcResult result = mockMvc.perform(get(PATH).cookie(sessionCookie()))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(JsonUtils.readJson(result.getResponse().getContentAsString())
                            .get("defaultCurrency")
                            .isNull())
                    .isTrue();
        }

        @Test
        @DisplayName("when the preferences are replaced with eur - then the response is 200 with defaultCurrency EUR")
        void whenReplacedWithEur_thenResponseIs200WithEurAndPortReceivedCommandCarryingEur() throws Exception {
            when(replacePreferencesPort.replace(any()))
                    .thenReturn(new Preferences(Optional.of(CurrencyCode.of("EUR"))));

            MvcResult result = mockMvc.perform(put(PATH)
                            .with(csrf())
                            .cookie(sessionCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"defaultCurrency":"eur"}"""))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(JsonUtils.readJson(result.getResponse().getContentAsString())
                            .get("defaultCurrency")
                            .asText())
                    .isEqualTo("EUR");

            ArgumentCaptor<ReplacePreferencesCommand> command =
                    ArgumentCaptor.forClass(ReplacePreferencesCommand.class);
            verify(replacePreferencesPort).replace(command.capture());
            assertThat(command.getValue().defaultCurrency()).isEqualTo(CurrencyCode.of("EUR"));
        }
    }

    @Nested
    @DisplayName("Error Mapping")
    class ErrorMapping {

        @Test
        @DisplayName("when the read port throws PersistenceFailedException - then the response is 503 and the "
                + "body carries a message")
        void whenReadPortThrowsPersistenceFailedException_thenResponseIs503AndBodyCarriesMessage() throws Exception {
            when(readPreferencesPort.read(any()))
                    .thenThrow(new PersistenceFailedException("the read statement failed", new RuntimeException()));

            MvcResult result = mockMvc.perform(get(PATH).cookie(sessionCookie()))
                    .andExpect(status().isServiceUnavailable())
                    .andReturn();

            assertThat(messageOf(result)).isNotBlank();
        }

        @Test
        @DisplayName("when the replace port throws PersistenceFailedException - then the response is 503 and the "
                + "body carries a message")
        void whenReplacePortThrowsPersistenceFailedException_thenResponseIs503AndBodyCarriesMessage() throws Exception {
            when(replacePreferencesPort.replace(any()))
                    .thenThrow(new PersistenceFailedException("the replace statement failed", new RuntimeException()));

            MvcResult result = mockMvc.perform(put(PATH)
                            .with(csrf())
                            .cookie(sessionCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"defaultCurrency":"EUR"}"""))
                    .andExpect(status().isServiceUnavailable())
                    .andReturn();

            assertThat(messageOf(result)).isNotBlank();
        }

        @Test
        @DisplayName("when defaultCurrency is a code ISO 4217 does not know - then the response is 400 naming the "
                + "code, and no port is called")
        void whenDefaultCurrencyIsUnknownCode_thenResponseIs400NamingCodeAndNoPortCalled() throws Exception {
            MvcResult result = mockMvc.perform(put(PATH)
                            .with(csrf())
                            .cookie(sessionCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"defaultCurrency":"ZZZ"}"""))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(messageOf(result)).containsIgnoringCase("ZZZ");
            verify(replacePreferencesPort, never()).replace(any());
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @ParameterizedTest(name = "{0}")
        @MethodSource("bot.finance.adapter.web.PreferencesControllerTest#defaultCurrencyViolations")
        @DisplayName("when defaultCurrency breaks its own constraint - then the response is 400 naming defaultCurrency")
        void whenDefaultCurrencyBreaksItsOwnConstraint_thenResponseIs400NamingDefaultCurrencyAndPortNeverCalled(
                String description, String body) throws Exception {
            MvcResult result = mockMvc.perform(put(PATH)
                            .with(csrf())
                            .cookie(sessionCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(messageOf(result)).containsIgnoringCase("defaultCurrency");
            verify(replacePreferencesPort, never()).replace(any());
        }
    }

    static Stream<Arguments> defaultCurrencyViolations() {
        return Stream.of(
                arguments("absent from the body", "{}"),
                arguments("null", """
                        {"defaultCurrency":null}"""),
                arguments("blank", """
                        {"defaultCurrency":""}"""),
                arguments("two letters", """
                        {"defaultCurrency":"EU"}"""),
                arguments("four letters", """
                        {"defaultCurrency":"EURO"}"""),
                arguments(
                        "three characters that are not letters",
                        """
                        {"defaultCurrency":"123"}"""));
    }

    private static String messageOf(MvcResult result) throws Exception {
        return JsonUtils.readJson(result.getResponse().getContentAsString())
                .get("message")
                .asText();
    }

    private static Cookie sessionCookie() {
        return BrowserSessions.cookieFor(USER_ID);
    }
}
