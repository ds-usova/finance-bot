package bot.finance.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bot.finance.application.dto.BrowseCategoriesCommand;
import bot.finance.application.dto.CategoryEntry;
import bot.finance.application.port.BrowseCategoriesPort;
import bot.finance.common.boot.WebAdapterTest;
import bot.finance.common.fixtures.BrowserSessions;
import bot.finance.common.fixtures.JsonUtils;
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
 * {@code /api/v1/categories} - never by calling {@link CategoriesController}'s method directly, since binding
 * lives in the generated {@link bot.finance.api.CategoriesApi} interface. Only {@link BrowseCategoriesPort} is
 * mocked. The 400 a non-numeric {@code groupingId} answers belongs to {@link WebExceptionHandlerTest}.
 */
@WebAdapterTest
@WebMvcTest(CategoriesController.class)
class CategoriesControllerTest {

    private static final String PATH = "/api/v1/categories";
    private static final String EXTERNAL_ID = "223344556";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrowseCategoriesPort browseCategoriesPort;

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("when the request carries a valid session cookie and no grouping id - then the port is "
                + "called with a command carrying no grouping id, and the response is 200 with all three "
                + "categories, each naming its grouping's id and name")
        void whenNoGroupingId_thenPortCalledWithNoGroupingIdAndResponseIs200WithAllThree() throws Exception {
            when(browseCategoriesPort.browse(any()))
                    .thenReturn(List.of(
                            new CategoryEntry(1L, "Supermarkets", 100L, "Groceries"),
                            new CategoryEntry(2L, "Fuel", 200L, "Auto"),
                            new CategoryEntry(3L, "Rent", 300L, "Housing")));

            MvcResult result = mockMvc.perform(get(PATH).cookie(sessionCookie()))
                    .andExpect(status().isOk())
                    .andReturn();

            ArgumentCaptor<BrowseCategoriesCommand> command = ArgumentCaptor.forClass(BrowseCategoriesCommand.class);
            verify(browseCategoriesPort).browse(command.capture());
            assertThat(command.getValue().groupingId()).isNull();

            JsonNode items = JsonUtils.readJson(result.getResponse().getContentAsString());
            assertThat(items).hasSize(3);
            assertThat(items.get(0).get("name").asText()).isEqualTo("Supermarkets");
            assertThat(items.get(0).get("groupingId").asLong()).isEqualTo(100L);
            assertThat(items.get(0).get("groupingName").asText()).isEqualTo("Groceries");
        }

        @Test
        @DisplayName("when the request carries a grouping id - then the port is called with a command carrying "
                + "that grouping id")
        void whenGroupingIdIsGiven_thenPortCalledWithCommandCarryingThatGroupingId() throws Exception {
            when(browseCategoriesPort.browse(any()))
                    .thenReturn(List.of(new CategoryEntry(1L, "Supermarkets", 100L, "Groceries")));

            mockMvc.perform(get(PATH).cookie(sessionCookie()).param("groupingId", "100"));

            ArgumentCaptor<BrowseCategoriesCommand> command = ArgumentCaptor.forClass(BrowseCategoriesCommand.class);
            verify(browseCategoriesPort).browse(command.capture());
            assertThat(command.getValue().groupingId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("when the request carries a grouping id that matches nothing - then the response is 200 with "
                + "an empty array, never 404")
        void whenGroupingIdMatchesNothing_thenResponseIs200WithEmptyArray() throws Exception {
            when(browseCategoriesPort.browse(any())).thenReturn(List.of());

            MvcResult result = mockMvc.perform(get(PATH).cookie(sessionCookie()).param("groupingId", "999"))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(JsonUtils.readJson(result.getResponse().getContentAsString()))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("when groupingId is a number - then it binds and reaches the port as the command's grouping id")
        void whenGroupingIdIsANumber_thenItBindsAndReachesThePortAsTheCommandsGroupingId() throws Exception {
            when(browseCategoriesPort.browse(any())).thenReturn(List.of());

            mockMvc.perform(get(PATH).cookie(sessionCookie()).param("groupingId", "77"));

            ArgumentCaptor<BrowseCategoriesCommand> command = ArgumentCaptor.forClass(BrowseCategoriesCommand.class);
            verify(browseCategoriesPort).browse(command.capture());
            assertThat(command.getValue().groupingId()).isEqualTo(77L);
        }
    }

    private static Cookie sessionCookie() {
        return BrowserSessions.cookieFor(EXTERNAL_ID);
    }
}
