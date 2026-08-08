package bot.finance.adapter.web;

import bot.finance.adapter.security.AuthenticatedCaller;
import bot.finance.api.ExpensesApi;
import bot.finance.api.model.ListExpenses200Response;
import bot.finance.application.dto.BrowseExpensesCommand;
import bot.finance.application.port.BrowseExpensesPort;
import bot.finance.domain.value.ExpenseFilter;
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
        ExpenseFilter filter = ExpenseWebMapper.toFilter(limit, offset, status, categoryId, from, to);
        BrowseExpensesCommand command = new BrowseExpensesCommand(AuthenticatedCaller.authenticatedUserId(), filter);
        return ResponseEntity.ok(ExpenseWebMapper.toResponse(browseExpensesPort.browse(command)));
    }
}
