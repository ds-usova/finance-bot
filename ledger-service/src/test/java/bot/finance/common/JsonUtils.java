package bot.finance.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtils() {}

    public static String readJsonResourceAsString(String fileName) {
        try (InputStream is = JsonUtils.class.getClassLoader().getResourceAsStream(fileName)) {
            if (is == null) {
                throw new IOException("Resource not found: " + fileName);
            }
            byte[] bytes = is.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read JSON resource: " + fileName, e);
        }
    }

    public static JsonNode readJsonResourceAsNode(String fileName) {
        String json = readJsonResourceAsString(fileName);
        try {
            return MAPPER.readTree(json);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse JSON resource: " + fileName, e);
        }
    }
}
