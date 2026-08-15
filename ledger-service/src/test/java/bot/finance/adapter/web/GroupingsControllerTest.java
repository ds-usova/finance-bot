package bot.finance.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bot.finance.application.dto.BrowseGroupingsCommand;
import bot.finance.application.dto.GroupingEntry;
import bot.finance.application.port.BrowseGroupingsPort;
import bot.finance.common.boot.WebAdapterTest;
import bot.finance.common.fixtures.BrowserSessions;
import bot.finance.common.fixtures.JsonUtils;
import bot.finance.domain.value.AuthenticatedUserId;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration test for the inbound HTTP adapter. Enters through the protocol - a MockMvc GET against
 * {@code /api/v1/groupings} - never by calling {@link GroupingsController}'s method directly, since binding lives
 * in the generated {@link bot.finance.api.GroupingsApi} interface. Only {@link BrowseGroupingsPort} is mocked.
 */
@WebAdapterTest
@WebMvcTest(GroupingsController.class)
class GroupingsControllerTest {

    private static final String PATH = "/api/v1/groupings";
    private static final long USER_ID = 334455667L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrowseGroupingsPort browseGroupingsPort;

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("when the request carries a valid session cookie - then the port is called with a command "
                + "carrying its subject")
        void whenValidSessionCookie_thenPortCalledWithCommandCarryingTheCookiesSubject() throws Exception {
            browseWithSessionCookie();

            ArgumentCaptor<BrowseGroupingsCommand> command = ArgumentCaptor.forClass(BrowseGroupingsCommand.class);
            verify(browseGroupingsPort).browse(command.capture());
            assertThat(command.getValue().userId()).isEqualTo(new AuthenticatedUserId(USER_ID));
        }

        @Test
        @DisplayName("when the port answers two groupings - then the response is 200 with both, unpaged")
        void whenPortAnswersTwoGroupings_thenResponseIs200WithBothUnpaged() throws Exception {
            MvcResult result = browseWithSessionCookie();

            JsonNode items = JsonUtils.readJson(result.getResponse().getContentAsString());
            assertThat(items).hasSize(2);
            assertThat(items.get(0).get("name").asText()).isEqualTo("Groceries");
            assertThat(items.get(1).get("name").asText()).isEqualTo("Housing");
        }

        /** A GET carrying a valid session cookie, the port answering two groupings. */
        private MvcResult browseWithSessionCookie() throws Exception {
            when(browseGroupingsPort.browse(any()))
                    .thenReturn(List.of(new GroupingEntry(1L, "Groceries"), new GroupingEntry(2L, "Housing")));

            return mockMvc.perform(get(PATH).cookie(sessionCookie()))
                    .andExpect(status().isOk())
                    .andReturn();
        }

        @Test
        @DisplayName("when the port answers an empty list - then the response is 200 with an empty array")
        void whenPortAnswersEmptyList_thenResponseIs200WithEmptyArray() throws Exception {
            when(browseGroupingsPort.browse(any())).thenReturn(List.of());

            MvcResult result = mockMvc.perform(get(PATH).cookie(sessionCookie()))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(JsonUtils.readJson(result.getResponse().getContentAsString()))
                    .isEmpty();
        }
    }

    private static Cookie sessionCookie() {
        return BrowserSessions.cookieFor(USER_ID);
    }
}
