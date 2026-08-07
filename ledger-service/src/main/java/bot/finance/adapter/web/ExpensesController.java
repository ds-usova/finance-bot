package bot.finance.adapter.web;

import bot.finance.api.ExpensesApi;
import bot.finance.api.model.ListExpenses200Response;
import bot.finance.application.port.BrowseExpensesPort;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExpensesController implements ExpensesApi {

    private final BrowseExpensesPort browseExpensesPort;

    public ExpensesController(BrowseExpensesPort browseExpensesPort) {
        this.browseExpensesPort = browseExpensesPort;
    }

    @Override
    public ResponseEntity<ListExpenses200Response> listExpenses(
            Integer limit, Integer offset, String status, Long categoryId, LocalDate from, LocalDate to) {
        // TODO: take AuthenticatedCaller.authenticatedUserId(), build the filter through
        // ExpenseWebMapper.toFilter, call browseExpensesPort.browse with a BrowseExpensesCommand, and map the
        // answered page through ExpenseWebMapper.toResponse
        return ExpensesApi.super.listExpenses(limit, offset, status, categoryId, from, to);
    }
}
