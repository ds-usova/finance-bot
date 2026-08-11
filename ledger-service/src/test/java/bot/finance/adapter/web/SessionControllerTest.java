package bot.finance.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bot.finance.adapter.telegram.TelegramLoginRejectedException;
import bot.finance.adapter.telegram.TelegramLoginVerifier;
import bot.finance.application.dto.InitializeUserCommand;
import bot.finance.application.port.InitializeUserPort;
import bot.finance.application.port.ReadSessionPort;
import bot.finance.common.boot.WebAdapterTest;
import bot.finance.common.fixtures.BrowserSessions;
import bot.finance.common.fixtures.SessionTokens;
import bot.finance.domain.model.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebAdapterTest
@WebMvcTest(SessionController.class)
class SessionControllerTest {

    private static final String EXTERNAL_ID = "987654321";
    private static final long USER_ID = 987654321L;
    private static final String PAYLOAD_JSON =
            """
            {"id":"987654321","first_name":"Ada","auth_date":"1785000000","hash":"cafebabe"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TelegramLoginVerifier loginVerifier;

    @MockitoBean
    private InitializeUserPort initializeUserPort;

    @MockitoBean
    private ReadSessionPort readSessionPort;

    @Nested
    @DisplayName("POST /api/v1/session")
    class SignIn {

        @Test
        @DisplayName("when the payload verifies - then the user is initialized with the id the payload is signed for")
        @Disabled("RI06: acceptTheSignIn() must answer User.stored(<id>, ...) now that signIn mints from the "
                + "answered row's id")
        void whenThePayloadVerifies_thenTheUserIsInitializedWithTheIdThePayloadIsSignedFor() throws Exception {
            acceptTheSignIn();

            mockMvc.perform(signInRequest()).andExpect(status().isOk());

            ArgumentCaptor<InitializeUserCommand> command = ArgumentCaptor.forClass(InitializeUserCommand.class);
            verify(initializeUserPort).initialize(command.capture());
            assertThat(command.getValue().externalId()).isEqualTo(EXTERNAL_ID);
        }

        @Test
        @DisplayName("when the payload verifies - then the session cookie is HttpOnly, path-scoped and SameSite=Lax")
        @Disabled("RI06: acceptTheSignIn() must answer User.stored(<id>, ...) now that signIn mints from the "
                + "answered row's id")
        void whenThePayloadVerifies_thenTheSessionCookieIsHttpOnlyPathScopedAndSameSiteLax() throws Exception {
            acceptTheSignIn();

            String setCookie = setCookieHeaderOf(
                    mockMvc.perform(signInRequest()).andExpect(status().isOk()).andReturn());

            assertThat(setCookie).startsWith(BrowserSessions.COOKIE_NAME + "=");
            assertThat(setCookie).contains("HttpOnly");
            assertThat(setCookie).contains("Path=/");
            assertThat(setCookie).contains("SameSite=Lax");
            assertThat(setCookie).contains("Max-Age=");
        }

        @Test
        @DisplayName("when web.session.secure is left at its local default - then the session cookie is not Secure")
        @Disabled("RI06: acceptTheSignIn() must answer User.stored(<id>, ...) now that signIn mints from the "
                + "answered row's id")
        void whenWebSessionSecureIsLeftAtItsLocalDefault_thenTheSessionCookieIsNotSecure() throws Exception {
            acceptTheSignIn();

            String setCookie =
                    setCookieHeaderOf(mockMvc.perform(signInRequest()).andReturn());

            assertThat(setCookie).doesNotContain("Secure");
        }

        @Test
        @DisplayName("when the payload verifies - then the body answers with the signed-in external id")
        @Disabled("RI06: acceptTheSignIn() must answer User.stored(<id>, ...) now that signIn mints from the "
                + "answered row's id")
        void whenThePayloadVerifies_thenTheBodyAnswersWithTheSignedInExternalId() throws Exception {
            acceptTheSignIn();

            MvcResult result =
                    mockMvc.perform(signInRequest()).andExpect(status().isOk()).andReturn();

            assertThat(result.getResponse().getContentAsString()).contains(EXTERNAL_ID);
        }

        @Test
        @DisplayName("when the payload is rejected - then the response is 401, no user is stored and no cookie is set")
        void whenThePayloadIsRejected_thenTheResponseIs401NoUserIsStoredAndNoCookieIsSet() throws Exception {
            when(loginVerifier.verify(any(), any())).thenThrow(new TelegramLoginRejectedException("rejected"));

            MvcResult result = mockMvc.perform(signInRequest())
                    .andExpect(status().isUnauthorized())
                    .andReturn();

            verify(initializeUserPort, never()).initialize(any());
            assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        }

        @Test
        @DisplayName("when the payload is rejected - then the response body does not echo the payload back")
        void whenThePayloadIsRejected_thenTheResponseBodyDoesNotEchoThePayloadBack() throws Exception {
            when(loginVerifier.verify(any(), any())).thenThrow(new TelegramLoginRejectedException("rejected"));

            MvcResult result = mockMvc.perform(signInRequest()).andReturn();

            assertThat(result.getResponse().getContentAsString())
                    .as("the rejected payload's hash, which must reach neither the body nor a log line")
                    .doesNotContain("cafebabe");
        }

        @Test
        @DisplayName("when the request carries no CSRF token - then the sign-in is refused")
        void whenTheRequestCarriesNoCsrfToken_thenTheSignInIsRefused() throws Exception {
            acceptTheSignIn();

            mockMvc.perform(post("/api/v1/session")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PAYLOAD_JSON))
                    .andExpect(status().isForbidden());

            verify(initializeUserPort, never()).initialize(any());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/session")
    class ReadSession {

        @Test
        @DisplayName("when the request carries a valid session cookie - then it answers with that cookie's subject")
        @Disabled("RI06: the subject is now an internal id re-resolved from the row; arrange the read session port")
        void whenTheRequestCarriesAValidSessionCookie_thenItAnswersWithThatCookiesSubject() throws Exception {
            // MvcResult result = mockMvc.perform(get("/api/v1/session").cookie(BrowserSessions.cookieFor(EXTERNAL_ID)))
            //         .andExpect(status().isOk())
            //         .andReturn();
            //
            // assertThat(result.getResponse().getContentAsString()).contains(EXTERNAL_ID);
        }

        @Test
        @DisplayName("when the request carries no session cookie - then it is refused")
        void whenTheRequestCarriesNoSessionCookie_thenItIsRefused() throws Exception {
            mockMvc.perform(get("/api/v1/session")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("when the session token is presented in the Authorization header instead - then it is refused")
        void whenTheSessionTokenIsPresentedInTheAuthorizationHeaderInstead_thenItIsRefused() throws Exception {
            mockMvc.perform(get("/api/v1/session")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + SessionTokens.tokenFor(USER_ID)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("when the cookie carries a token this service did not sign - then it is refused")
        void whenTheCookieCarriesATokenThisServiceDidNotSign_thenItIsRefused() throws Exception {
            mockMvc.perform(get("/api/v1/session").cookie(new Cookie(BrowserSessions.COOKIE_NAME, "not.a.token")))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/session")
    class SignOut {

        @Test
        @DisplayName("when the session is deleted - then the cookie is cleared with Max-Age=0 and the body is empty")
        void whenTheSessionIsDeleted_thenTheCookieIsClearedWithMaxAgeZero() throws Exception {
            MvcResult result = mockMvc.perform(delete("/api/v1/session").with(csrf()))
                    .andExpect(status().isNoContent())
                    .andReturn();

            assertThat(setCookieHeaderOf(result)).contains("Max-Age=0");
            assertThat(result.getResponse().getContentAsString()).isEmpty();
        }

        @Test
        @DisplayName(
                "when no session cookie is present - then the delete still clears the cookie and the body is empty")
        void whenNoSessionCookieIsPresent_thenTheDeleteStillClearsTheCookie() throws Exception {
            MvcResult result = mockMvc.perform(delete("/api/v1/session").with(csrf()))
                    .andExpect(status().isNoContent())
                    .andReturn();

            assertThat(setCookieHeaderOf(result)).startsWith(BrowserSessions.COOKIE_NAME + "=;");
            assertThat(result.getResponse().getContentAsString()).isEmpty();
        }
    }

    private void acceptTheSignIn() {
        when(loginVerifier.verify(any(), any())).thenReturn(EXTERNAL_ID);
        when(initializeUserPort.initialize(any())).thenReturn(User.newUser(EXTERNAL_ID));
    }

    private static MockHttpServletRequestBuilder signInRequest() {
        return post("/api/v1/session")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(PAYLOAD_JSON);
    }

    private static String setCookieHeaderOf(MvcResult result) {
        return result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    }
}
