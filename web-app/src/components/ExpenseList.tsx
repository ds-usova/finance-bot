import type { ExpensePage } from '../api/expenses';

export type ExpenseListProps = {
  page: ExpensePage;
  /** Each category's name by its id. A row whose category is missing renders unnamed rather than failing. */
  categoryNames: Map<number, string>;
};

function decimalAmount(minorUnits: number): string {
  return (minorUnits / 100).toFixed(2);
}

export function ExpenseList({ page, categoryNames }: ExpenseListProps) {
  if (page.items.length === 0) {
    return <p className="expense-list-note">No expenses to show.</p>;
  }

  return (
    <table className="expense-list">
      <thead>
        <tr>
          <th scope="col">Description</th>
          <th scope="col">Merchant</th>
          <th scope="col">Category</th>
          <th scope="col">Amount</th>
          <th scope="col">Currency</th>
          <th scope="col">Status</th>
        </tr>
      </thead>
      <tbody>
        {page.items.map((expense) => (
          <tr key={`${expense.status}-${expense.id}`}>
            <td>{expense.description}</td>
            <td>{expense.merchant}</td>
            <td>{categoryNames.get(expense.categoryId)}</td>
            <td className="amount">{decimalAmount(expense.amountMinorUnits)}</td>
            <td>{expense.currency}</td>
            <td>{expense.status}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
