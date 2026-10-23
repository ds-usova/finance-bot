package bot.finance.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bot.finance.application.dto.BrowseWrittenMessagesCommand;
import bot.finance.application.dto.FileWrittenMessageCommand;
import bot.finance.application.dto.ReadWrittenMessageCommand;
import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.dto.WrittenMessageAnswer;
import bot.finance.application.dto.WrittenMessageView;
import bot.finance.application.port.BrowseWrittenMessagesPort;
import bot.finance.application.port.FileWrittenMessagePort;
import bot.finance.application.port.ReadWrittenMessagePort;
import bot.finance.common.boot.WebAdapterTest;
import bot.finance.common.fixtures.BrowserSessions;
import bot.finance.common.fixtures.JsonUtils;
import bot.finance.domain.exception.InvalidWrittenMessageException;
import bot.finance.domain.exception.WrittenMessageInProgressException;
import bot.finance.domain.exception.WrittenMessageNotFoundException;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.WrittenMessageStatus;
import io.restassured.path.json.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Integration test for the inbound HTTP adapter. Enters through the protocol - MockMvc requests against
 * {@code /api/v1/messages} - never by calling {@link MessagesController}'s methods directly. The three message
 * ports are mocked. It owns what these endpoints accept and refuse; {@link WebExceptionHandlerTest} owns only the
 * mappings every controller shares.
 */
@WebAdapterTest
@WebMvcTest(MessagesController.class)
class MessagesControllerTest {

    private static final String PATH = "/api/v1/messages";
    private static final String BY_ID_PATH = "/api/v1/messages/{id}";
    private static final long USER_ID = 778899001L;
    private static final String TEXT = "coffee 3.40, taxi home 14";
    private static final String OUTSIDE_THE_BMP = new String(Character.toChars(0x1F600));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileWrittenMessagePort fileWrittenMessagePort;

    @MockitoBean
    private ReadWrittenMessagePort readWrittenMessagePort;

    @MockitoBean
    private BrowseWrittenMessagesPort browseWrittenMessagesPort;

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("when a text is posted with a session and a CSRF token - then 202 with the message as READING")
        void whenTextIsPosted_thenAccepted202WithTheMessage() throws Exception {
            when(fileWrittenMessagePort.file(any())).thenReturn(view(42L, WrittenMessageStatus.WAITING, null));

            MvcResult result = mockMvc.perform(filing(TEXT))
                    .andExpect(status().isAccepted())
                    .andReturn();

            JsonPath json = JsonPath.from(result.getResponse().getContentAsString());
            assertThat(json.getLong("id")).isEqualTo(42L);
            assertThat(json.getString("state")).isEqualTo("READING");
            assertThat(json.getString("text")).isEqualTo(TEXT);
            ArgumentCaptor<FileWrittenMessageCommand> command =
                    ArgumentCaptor.forClass(FileWrittenMessageCommand.class);
            verify(fileWrittenMessagePort).file(command.capture());
            assertThat(command.getValue().userId()).isEqualTo(new AuthenticatedUserId(USER_ID));
            assertThat(command.getValue().text()).isEqualTo(TEXT);
        }

        @Test
        @DisplayName("when a DONE message is asked for by id - then 200 with its state, outcome, text and days")
        void whenDoneMessageIsAskedFor_thenOkWithOutcomeAndDays() throws Exception {
            WrittenMessageView done = view(42L, WrittenMessageStatus.DONE, ReportOutcome.RECORDED);
            when(readWrittenMessagePort.read(any()))
                    .thenReturn(new WrittenMessageAnswer(done, List.of(LocalDate.of(2026, 10, 1))));

            MvcResult result = mockMvc.perform(get(BY_ID_PATH, "42").cookie(sessionCookie()))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonPath json = JsonPath.from(result.getResponse().getContentAsString());
            assertThat(json.getLong("id")).isEqualTo(42L);
            assertThat(json.getString("state")).isEqualTo("DONE");
            assertThat(json.getString("outcome")).isEqualTo("RECORDED");
            assertThat(json.getString("text")).isEqualTo(TEXT);
            assertThat(json.getList("days")).containsExactly("2026-10-01");
            ArgumentCaptor<ReadWrittenMessageCommand> command =
                    ArgumentCaptor.forClass(ReadWrittenMessageCommand.class);
            verify(readWrittenMessagePort).read(command.capture());
            assertThat(command.getValue().userId()).isEqualTo(new AuthenticatedUserId(USER_ID));
            assertThat(command.getValue().messageId()).isEqualTo(42L);
        }

        @Test
        @DisplayName("when a READING message is asked for by id - then 200 with a null outcome and no days")
        void whenReadingMessageIsAskedFor_thenOkWithNullOutcomeAndNoDays() throws Exception {
            WrittenMessageView reading = view(42L, WrittenMessageStatus.READING, null);
            when(readWrittenMessagePort.read(any())).thenReturn(new WrittenMessageAnswer(reading, List.of()));

            MvcResult result = mockMvc.perform(get(BY_ID_PATH, "42").cookie(sessionCookie()))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonPath json = JsonPath.from(result.getResponse().getContentAsString());
            assertThat(json.getString("state")).isEqualTo("READING");
            assertThat(json.getString("text")).isEqualTo(TEXT);
            assertThat(json.getString("outcome")).isNull();
            assertThat(json.getList("days")).isEmpty();
        }

        @Test
        @DisplayName("when the READING list is asked for - then 200 with the one message, for the session's user")
        void whenReadingListIsAskedFor_thenOkWithTheMessage() throws Exception {
            when(browseWrittenMessagesPort.browse(any()))
                    .thenReturn(List.of(view(42L, WrittenMessageStatus.READING, null)));

            MvcResult result = mockMvc.perform(get(PATH).cookie(sessionCookie()).param("state", "READING"))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonPath json = JsonPath.from(result.getResponse().getContentAsString());
            assertThat(json.getList("$")).hasSize(1);
            assertThat(json.getLong("[0].id")).isEqualTo(42L);
            assertThat(json.getString("[0].state")).isEqualTo("READING");
            ArgumentCaptor<BrowseWrittenMessagesCommand> command =
                    ArgumentCaptor.forClass(BrowseWrittenMessagesCommand.class);
            verify(browseWrittenMessagesPort).browse(command.capture());
            assertThat(command.getValue().userId()).isEqualTo(new AuthenticatedUserId(USER_ID));
        }
    }

    @Nested
    @DisplayName("Error Mapping")
    class ErrorMapping {

        @Test
        @DisplayName("when the caller already has a text in progress - then 429 with a message")
        void whenTextIsAlreadyInProgress_thenTooManyRequests() throws Exception {
            when(fileWrittenMessagePort.file(any()))
                    .thenThrow(new WrittenMessageInProgressException("a text of yours is already waiting to be read"));

            MvcResult result = mockMvc.perform(filing(TEXT))
                    .andExpect(status().isTooManyRequests())
                    .andReturn();

            assertThat(messageOf(result)).isEqualTo("a text of yours is already waiting to be read");
        }

        @Test
        @DisplayName("when a text of only ideographic spaces is refused by the port - then 400 with its message")
        void whenPortRefusesTheText_thenBadRequestWithItsMessage() throws Exception {
            when(fileWrittenMessagePort.file(any()))
                    .thenThrow(new InvalidWrittenMessageException("text must not be blank"));

            MvcResult result = mockMvc.perform(filing("　　　"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(messageOf(result)).isEqualTo("text must not be blank");
        }

        @Test
        @DisplayName("when the message is not the caller's - then 404 with the message's own wording")
        void whenMessageIsNotFound_thenNotFoundWithTheExceptionsMessage() throws Exception {
            when(readWrittenMessagePort.read(any()))
                    .thenThrow(new WrittenMessageNotFoundException("no text of yours has the id 42"));

            MvcResult result = mockMvc.perform(get(BY_ID_PATH, "42").cookie(sessionCookie()))
                    .andExpect(status().isNotFound())
                    .andReturn();

            assertThat(messageOf(result)).isEqualTo("no text of yours has the id 42");
        }

        @Test
        @DisplayName("when a text is posted without a session cookie - then 401 and no port is called")
        void whenPostedWithoutSession_thenUnauthorized() throws Exception {
            mockMvc.perform(post(PATH)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(TEXT)))
                    .andExpect(status().isUnauthorized());

            verifyNoPortCalled();
        }

        @Test
        @DisplayName("when a message is asked for by id without a session cookie - then 401 and no port is called")
        void whenReadWithoutSession_thenUnauthorized() throws Exception {
            mockMvc.perform(get(BY_ID_PATH, "42")).andExpect(status().isUnauthorized());

            verifyNoPortCalled();
        }

        @Test
        @DisplayName("when the list is asked for without a session cookie - then 401 and no port is called")
        void whenListedWithoutSession_thenUnauthorized() throws Exception {
            mockMvc.perform(get(PATH).param("state", "READING")).andExpect(status().isUnauthorized());

            verifyNoPortCalled();
        }

        @Test
        @DisplayName("when a text is posted with a session and no CSRF token - then 403 and the port is never called")
        void whenPostedWithoutCsrfToken_thenForbidden() throws Exception {
            mockMvc.perform(post(PATH)
                            .cookie(sessionCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(TEXT)))
                    .andExpect(status().isForbidden());

            verify(fileWrittenMessagePort, never()).file(any());
        }

        private void verifyNoPortCalled() {
            verifyNoInteractions(fileWrittenMessagePort, readWrittenMessagePort, browseWrittenMessagesPort);
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @ParameterizedTest(name = "{0}")
        @MethodSource("bot.finance.adapter.web.MessagesControllerTest#refusedBodies")
        @DisplayName("when the text is refused - then 400 naming text and the port is never called")
        void whenTextIsRefused_thenBadRequestNamingTextAndPortNeverCalled(String description, String body)
                throws Exception {
            MvcResult result = mockMvc.perform(post(PATH)
                            .with(csrf())
                            .cookie(sessionCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .characterEncoding("UTF-8")
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(messageOf(result)).containsIgnoringCase("text");
            verify(fileWrittenMessagePort, never()).file(any());
        }

        @Test
        @DisplayName("when the body is not JSON - then 400 and the port is never called")
        void whenBodyIsNotJson_thenBadRequestAndPortNeverCalled() throws Exception {
            mockMvc.perform(post(PATH)
                            .with(csrf())
                            .cookie(sessionCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("this is not json"))
                    .andExpect(status().isBadRequest());

            verify(fileWrittenMessagePort, never()).file(any());
        }

        @Test
        @DisplayName("when the text is 1000 UTF-16 code units ending outside the BMP - then it is accepted")
        void whenTextIsAtTheLimitInCodeUnits_thenAccepted() throws Exception {
            String text = "a".repeat(998) + OUTSIDE_THE_BMP;
            when(fileWrittenMessagePort.file(any()))
                    .thenReturn(new WrittenMessageView(
                            42L, USER_ID, text, WrittenMessageStatus.WAITING, Optional.empty(), Instant.EPOCH));

            mockMvc.perform(filing(text)).andExpect(status().isAccepted());

            ArgumentCaptor<FileWrittenMessageCommand> command =
                    ArgumentCaptor.forClass(FileWrittenMessageCommand.class);
            verify(fileWrittenMessagePort).file(command.capture());
            assertThat(command.getValue().text()).isEqualTo(text);
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-1", "abc"})
        @DisplayName("when the id is below 1 or not a number - then 400 and the port is never called")
        void whenIdIsInvalid_thenBadRequestAndPortNeverCalled(String id) throws Exception {
            mockMvc.perform(get(BY_ID_PATH, id).cookie(sessionCookie())).andExpect(status().isBadRequest());

            verify(readWrittenMessagePort, never()).read(any());
        }

        @Test
        @DisplayName("when state is absent - then 400 and the port is never called")
        void whenStateIsAbsent_thenBadRequestAndPortNeverCalled() throws Exception {
            mockMvc.perform(get(PATH).cookie(sessionCookie())).andExpect(status().isBadRequest());

            verify(browseWrittenMessagesPort, never()).browse(any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"DONE", "reading"})
        @DisplayName("when state is anything but READING - then 400 and the port is never called")
        void whenStateIsNotReading_thenBadRequestAndPortNeverCalled(String state) throws Exception {
            mockMvc.perform(get(PATH).cookie(sessionCookie()).param("state", state))
                    .andExpect(status().isBadRequest());

            verify(browseWrittenMessagesPort, never()).browse(any());
        }
    }

    static Stream<Arguments> refusedBodies() {
        return Stream.of(
                arguments("an absent text", "{}"),
                arguments("an empty text", body("")),
                arguments(
                        "a text of only spaces and line breaks",
                        """
                        {"text":"   \\n  \\n "}"""),
                arguments(
                        "a text of 1001 UTF-16 code units ending outside the BMP",
                        body("a".repeat(999) + OUTSIDE_THE_BMP)));
    }

    private static String body(String text) {
        return "{\"text\":\"" + text + "\"}";
    }

    private static MockHttpServletRequestBuilder filing(String text) {
        return post(PATH)
                .with(csrf())
                .cookie(sessionCookie())
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content(body(text));
    }

    private static WrittenMessageView view(long id, WrittenMessageStatus status, ReportOutcome outcome) {
        return new WrittenMessageView(id, USER_ID, TEXT, status, Optional.ofNullable(outcome), Instant.EPOCH);
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
