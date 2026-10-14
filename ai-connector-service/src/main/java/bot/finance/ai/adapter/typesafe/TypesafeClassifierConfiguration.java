package bot.finance.ai.adapter.typesafe;

import java.net.http.HttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = "extraction.mode", havingValue = "classifier")
@EnableConfigurationProperties(TypesafeClassifierProperties.class)
public class TypesafeClassifierConfiguration {

    @Bean
    RestClient classifierRestClient(TypesafeClassifierProperties properties) {
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .defaultHeaders(headers -> headers.setBearerAuth(properties.apiKey()))
                .requestFactory(requestFactory)
                .build();
    }
}
