package bot.finance.adapter.web;

import bot.finance.adapter.security.AuthenticatedCaller;
import bot.finance.api.ExpensesApi;
import bot.finance.api.model.AcceptExpenses200Response;
import bot.finance.api.model.AcceptExpensesRequest;
import bot.finance.api.model.CategoryPatchOperation;
import bot.finance.api.model.ChangeExpenseCategory200Response;
import bot.finance.api.model.ListExpenses200Response;
import bot.finance.application.dto.AcceptExpensesCommand;
import bot.finance.application.dto.BrowseExpensesCommand;
import bot.finance.application.dto.ChangeExpenseCategoryCommand;
import bot.finance.application.port.AcceptExpensesPort;
import bot.finance.application.port.BrowseExpensesPort;
import bot.finance.application.port.ChangeExpenseCategoryPort;
import bot.finance.domain.value.ExpenseFilter;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExpensesController implements ExpensesApi {

    private final BrowseExpensesPort browseExpensesPort;
    private final AcceptExpensesPort acceptExpensesPort;
    private final ChangeExpenseCategoryPort changeExpenseCategoryPort;

    public ExpensesController(
            BrowseExpensesPort browseExpensesPort,
            AcceptExpensesPort acceptExpensesPort,
            ChangeExpenseCategoryPort changeExpenseCategoryPort) {
        this.browseExpensesPort = browseExpensesPort;
        this.acceptExpensesPort = acceptExpensesPort;
        this.changeExpenseCategoryPort = changeExpenseCategoryPort;
    }

    @Override
    public ResponseEntity<ListExpenses200Response> listExpenses(
            Integer limit, Integer offset, String status, Long categoryId, LocalDate from, LocalDate to) {
        ExpenseFilter filter = ExpenseWebMapper.toFilter(limit, offset, status, categoryId, from, to);
        BrowseExpensesCommand command = new BrowseExpensesCommand(AuthenticatedCaller.authenticatedUserId(), filter);
        return ResponseEntity.ok(ExpenseWebMapper.toResponse(browseExpensesPort.browse(command)));
    }

    @Override
    public ResponseEntity<AcceptExpenses200Response> acceptExpenses(AcceptExpensesRequest acceptExpensesRequest) {
        AcceptExpensesCommand command = ExpenseWebMapper.toAcceptExpensesCommand(
                acceptExpensesRequest, AuthenticatedCaller.authenticatedUserId());
        return ResponseEntity.ok(ExpenseWebMapper.toAcceptanceResponse(acceptExpensesPort.accept(command)));
    }

    @Override
    public ResponseEntity<ChangeExpenseCategory200Response> changeExpenseCategory(
            String status, Long id, List<CategoryPatchOperation> categoryPatchOperation) {
        ChangeExpenseCategoryCommand command = ExpenseWebMapper.toChangeExpenseCategoryCommand(
                status, id, categoryPatchOperation, AuthenticatedCaller.authenticatedUserId());
        return ResponseEntity.ok(
                ExpenseWebMapper.toChangeExpenseCategoryResponse(changeExpenseCategoryPort.change(command)));
    }
}
