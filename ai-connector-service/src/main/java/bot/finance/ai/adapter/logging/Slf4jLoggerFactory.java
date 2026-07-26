package bot.finance.ai.adapter.logging;

import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Slf4jLoggerFactory implements LoggerFactory {

    @Override
    public Logger getLogger(Class<?> clazz) {
        return new Slf4jLogger(org.slf4j.LoggerFactory.getLogger(clazz));
    }

}
