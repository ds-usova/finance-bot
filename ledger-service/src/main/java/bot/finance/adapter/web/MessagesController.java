package bot.finance.adapter.web;

import bot.finance.api.MessagesApi;
import bot.finance.api.model.FileWrittenMessageRequest;
import bot.finance.api.model.ListWrittenMessages200ResponseInner;
import bot.finance.application.port.BrowseWrittenMessagesPort;
import bot.finance.application.port.FileWrittenMessagePort;
import bot.finance.application.port.ReadWrittenMessagePort;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MessagesController implements MessagesApi {

    private final FileWrittenMessagePort fileWrittenMessagePort;
    private final ReadWrittenMessagePort readWrittenMessagePort;
    private final BrowseWrittenMessagesPort browseWrittenMessagesPort;

    public MessagesController(
            FileWrittenMessagePort fileWrittenMessagePort,
            ReadWrittenMessagePort readWrittenMessagePort,
            BrowseWrittenMessagesPort browseWrittenMessagesPort) {
        this.fileWrittenMessagePort = fileWrittenMessagePort;
        this.readWrittenMessagePort = readWrittenMessagePort;
        this.browseWrittenMessagesPort = browseWrittenMessagesPort;
    }

    @Override
    public ResponseEntity<ListWrittenMessages200ResponseInner> fileWrittenMessage(
            FileWrittenMessageRequest fileWrittenMessageRequest) {
        // stub-intent: build the command with AuthenticatedCaller.authenticatedUserId(), call the filing port and
        // answer 202 with the stored message through the mapper.
        return null;
    }

    @Override
    public ResponseEntity<List<ListWrittenMessages200ResponseInner>> listWrittenMessages(String state) {
        // stub-intent: build the command with AuthenticatedCaller.authenticatedUserId(), call the browse port and
        // answer 200 with the unsettled messages through the mapper.
        return null;
    }

    @Override
    public ResponseEntity<ListWrittenMessages200ResponseInner> readWrittenMessage(Long id) {
        // stub-intent: build the command with AuthenticatedCaller.authenticatedUserId(), call the read port and
        // answer 200 with the message and its days through the mapper.
        return null;
    }
}
