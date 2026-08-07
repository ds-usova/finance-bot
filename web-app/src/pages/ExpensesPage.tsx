/**
 * TODO RU07/GU07: reads the auth context, calls listGroupings, listCategories and listExpenses once on mount,
 * renders ExpenseFilters over the tree it was answered and ExpenseList over the page, repeats only the expenses
 * call when the filter changes, shows an ApiError's message while leaving the list that was already there
 * standing, calls sessionExpired on the context when any of the three reads answers 401, and keeps the sign-out
 * control the deleted home page held.
 */
export function ExpensesPage() {
  return (
    <main>
      <h1>Expenses</h1>
    </main>
  );
}
