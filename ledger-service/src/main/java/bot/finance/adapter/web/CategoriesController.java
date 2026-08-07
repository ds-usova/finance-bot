package bot.finance.adapter.web;

import bot.finance.api.CategoriesApi;
import bot.finance.api.model.ListCategories200ResponseInner;
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
        // TODO: take AuthenticatedCaller.authenticatedUserId(), call browseCategoriesPort.browse with a
        // BrowseCategoriesCommand, and map the answered entries through CategoryWebMapper.toCategories
        return CategoriesApi.super.listCategories(groupingId);
    }
}
