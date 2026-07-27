package bot.finance.ai.common;

import bot.finance.ai.adapter.ai.AiIntentInferenceAdapter;
import bot.finance.ai.adapter.ai.ChatClientConfiguration;
import bot.finance.ai.adapter.logging.Slf4jLoggerFactory;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistrar;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Boots {@link AiIntentInferenceAdapter}, {@link ChatClientConfiguration} and Spring AI's OpenAI
 * autoconfiguration only — no gRPC server, no other adapter. The OpenAI chat autoconfiguration needs a {@code
 * ToolCallingManager}, so its autoconfiguration is listed too. {@code spring.ai.openai.base-url} is redirected to
 * {@link WireMockSupport}'s dynamic port through a {@link DynamicPropertyRegistrar} bean: {@code
 * @DynamicPropertySource} needs a static method inside a class body, which an annotation type cannot declare,
 * so this is the composed-annotation-compatible equivalent.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ActiveProfiles("test")
@SpringBootTest(classes = {AiIntentInferenceAdapter.class, ChatClientConfiguration.class, Slf4jLoggerFactory.class})
@ImportAutoConfiguration({
        OpenAiChatAutoConfiguration.class,
        ChatClientAutoConfiguration.class,
        ToolCallingAutoConfiguration.class
})
@Import(AiAdapterTest.WireMockBaseUrlConfiguration.class)
public @interface AiAdapterTest {

    @TestConfiguration(proxyBeanMethods = false)
    class WireMockBaseUrlConfiguration {

        @Bean
        DynamicPropertyRegistrar wireMockBaseUrl() {
            return registry -> registry.add("spring.ai.openai.base-url", WireMockSupport::baseUrl);
        }

    }

}
