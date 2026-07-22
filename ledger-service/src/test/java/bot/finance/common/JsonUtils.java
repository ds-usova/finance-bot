package bot.finance.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtils() {
        // private constructor to prevent instantiation
    }

    @SneakyThrows
    public static String readJsonResourceAsString(String fileName) {
        try (InputStream is = JsonUtils.class.getClassLoader().getResourceAsStream(fileName)) {
            if (is == null) {
                throw new IOException("Resource not found: " + fileName);
            }
            byte[] bytes = is.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    @SneakyThrows
    public static JsonNode readJsonResourceAsNode(String fileName) {
        String json = readJsonResourceAsString(fileName);
        return MAPPER.readTree(json);
    }

}
