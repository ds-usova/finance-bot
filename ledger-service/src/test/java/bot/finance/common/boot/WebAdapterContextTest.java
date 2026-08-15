package bot.finance.common.boot;

import bot.finance.adapter.telegram.TelegramLoginVerifier;
import bot.finance.adapter.web.SessionController;
import bot.finance.application.port.InitializeUserPort;
import bot.finance.application.port.ReadSessionPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves {@link WebAdapterTest} itself boots a working context - compiling says nothing about whether the
 * security filter chain, the signing keys and the minter actually come up together in a {@code @WebMvcTest}
 * slice. Throwaway: it asserts nothing beyond the autowiring succeeding.
 */
@WebAdapterTest
@WebMvcTest(SessionController.class)
class WebAdapterContextTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TelegramLoginVerifier loginVerifier;

    @MockitoBean
    private InitializeUserPort initializeUserPort;

    @MockitoBean
    private ReadSessionPort readSessionPort;

    @Test
    void contextLoads() {}
}
