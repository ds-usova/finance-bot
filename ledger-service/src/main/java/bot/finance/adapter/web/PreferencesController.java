package bot.finance.adapter.web;

import bot.finance.adapter.security.AuthenticatedCaller;
import bot.finance.api.PreferencesApi;
import bot.finance.api.model.ReadPreferences200Response;
import bot.finance.api.model.ReplacePreferencesRequest;
import bot.finance.application.dto.ReadPreferencesCommand;
import bot.finance.application.dto.ReplacePreferencesCommand;
import bot.finance.application.port.ReadPreferencesPort;
import bot.finance.application.port.ReplacePreferencesPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PreferencesController implements PreferencesApi {

    private final ReadPreferencesPort readPreferencesPort;
    private final ReplacePreferencesPort replacePreferencesPort;

    public PreferencesController(
            ReadPreferencesPort readPreferencesPort, ReplacePreferencesPort replacePreferencesPort) {
        this.readPreferencesPort = readPreferencesPort;
        this.replacePreferencesPort = replacePreferencesPort;
    }

    @Override
    public ResponseEntity<ReadPreferences200Response> readPreferences() {
        ReadPreferencesCommand command = new ReadPreferencesCommand(AuthenticatedCaller.authenticatedUserId());
        return ResponseEntity.ok(PreferencesWebMapper.toResponse(readPreferencesPort.read(command)));
    }

    @Override
    public ResponseEntity<ReadPreferences200Response> replacePreferences(
            ReplacePreferencesRequest replacePreferencesRequest) {
        ReplacePreferencesCommand command = PreferencesWebMapper.toReplacePreferencesCommand(
                replacePreferencesRequest, AuthenticatedCaller.authenticatedUserId());
        return ResponseEntity.ok(PreferencesWebMapper.toResponse(replacePreferencesPort.replace(command)));
    }
}
