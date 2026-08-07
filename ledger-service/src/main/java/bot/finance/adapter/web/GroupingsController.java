package bot.finance.adapter.web;

import bot.finance.api.GroupingsApi;
import bot.finance.api.model.ListGroupings200ResponseInner;
import bot.finance.application.port.BrowseGroupingsPort;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GroupingsController implements GroupingsApi {

    private final BrowseGroupingsPort browseGroupingsPort;

    public GroupingsController(BrowseGroupingsPort browseGroupingsPort) {
        this.browseGroupingsPort = browseGroupingsPort;
    }

    @Override
    public ResponseEntity<List<ListGroupings200ResponseInner>> listGroupings() {
        // TODO: take AuthenticatedCaller.authenticatedUserId(), call browseGroupingsPort.browse with a
        // BrowseGroupingsCommand, and map the answered entries through CategoryWebMapper.toGroupings
        return GroupingsApi.super.listGroupings();
    }
}
