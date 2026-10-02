package bot.finance.adapter.web;

import bot.finance.adapter.security.AuthenticatedCaller;
import bot.finance.api.MessagesApi;
import bot.finance.api.model.FileWrittenMessageRequest;
import bot.finance.api.model.ListWrittenMessages200ResponseInner;
import bot.finance.application.dto.BrowseWrittenMessagesCommand;
import bot.finance.application.dto.FileWrittenMessageCommand;
import bot.finance.application.dto.ReadWrittenMessageCommand;
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
        FileWrittenMessageCommand command = WrittenMessageWebMapper.toFileWrittenMessageCommand(
                fileWrittenMessageRequest, AuthenticatedCaller.authenticatedUserId());
        return ResponseEntity.accepted().body(WrittenMessageWebMapper.toResponse(fileWrittenMessagePort.file(command)));
    }

    @Override
    public ResponseEntity<List<ListWrittenMessages200ResponseInner>> listWrittenMessages(String state) {
        BrowseWrittenMessagesCommand command = WrittenMessageWebMapper.toBrowseWrittenMessagesCommand(
                state, AuthenticatedCaller.authenticatedUserId());
        return ResponseEntity.ok(WrittenMessageWebMapper.toResponses(browseWrittenMessagesPort.browse(command)));
    }

    @Override
    public ResponseEntity<ListWrittenMessages200ResponseInner> readWrittenMessage(Long id) {
        ReadWrittenMessageCommand command =
                WrittenMessageWebMapper.toReadWrittenMessageCommand(id, AuthenticatedCaller.authenticatedUserId());
        return ResponseEntity.ok(WrittenMessageWebMapper.toResponse(readWrittenMessagePort.read(command)));
    }
}
