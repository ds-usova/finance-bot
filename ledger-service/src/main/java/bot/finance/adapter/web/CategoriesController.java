package bot.finance.adapter.web;

import bot.finance.adapter.security.AuthenticatedCaller;
import bot.finance.api.CategoriesApi;
import bot.finance.api.model.ListCategories200ResponseInner;
import bot.finance.application.dto.BrowseCategoriesCommand;
import bot.finance.application.port.BrowseCategoriesPort;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CategoriesController implements CategoriesApi {

    private final BrowseCategoriesPort browseCategoriesPort;

    public CategoriesController(BrowseCategoriesPort browseCategoriesPort) {
        this.browseCategoriesPort = browseCategoriesPort;
    }

    @Override
    public ResponseEntity<List<ListCategories200ResponseInner>> listCategories(Long groupingId) {
        BrowseCategoriesCommand command =
                new BrowseCategoriesCommand(AuthenticatedCaller.authenticatedUserId(), groupingId);
        return ResponseEntity.ok(CategoryWebMapper.toCategories(browseCategoriesPort.browse(command)));
    }
}
