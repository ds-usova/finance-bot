# Grouping

A heading a user's [categories](category.md) are filed under. Spending is filed under a category, never under
the grouping holding it.

## Invariants

| Field                             | Bound                              |
|-----------------------------------|------------------------------------|
| `name`                            | mandatory, non-blank               |
| [`categories`](category.md)       | may be empty, fixed at construction |

One name is designated the catch-all, where spending falls when no other grouping fits. Every user's catalogue
carries a grouping under that name.

## The starting catalogue

The groupings and [categories](category.md) every new user is given.

| Grouping          | Categories                                                                  |
|-------------------|-----------------------------------------------------------------------------|
| Housing           | Rent, Mortgage, HOA, Property Tax, Home Insurance, Repairs, Furniture       |
| Groceries         | Supermarkets, Markets, Household Supplies                                   |
| Dining            | Restaurants, Cafés, Fast Food, Delivery                                     |
| Transportation    | Fuel, Public Transport, Parking, Taxis/Uber, Car Maintenance, Car Insurance |
| Utilities         | Electricity, Gas, Water, Internet, Mobile Phone                             |
| Healthcare        | Doctors, Pharmacy, Dental, Vision, Health Insurance                         |
| Education         | Tuition, Books, Courses, Certifications                                     |
| Shopping          | Clothing, Electronics, Home Goods, Gifts                                    |
| Entertainment     | Movies, Games, Hobbies                                                      |
| Travel            | Hotels, Flights, Vacation, Attractions                                      |
| Pets              | Food, Vet, Grooming                                                         |
| Family & Children | Childcare, School Supplies, Toys                                            |
| Financial         | Taxes, Bank Fees, Loan Payments, Interest                                   |
| Investments       | Brokerage, Retirement, Crypto, Savings Transfers                            |
| Gifts & Donations | Charity, Birthday Gifts, Holidays                                           |
| Work              | Office Supplies, Business Expenses                                          |
| Insurance         | Life, Home, Vehicle, Travel                                                 |
| Personal Care     | Haircuts, Cosmetics, Gym, Spa                                               |
| Subscriptions     | Streaming, Music, Cloud Storage, Apps & Software                            |
| Miscellaneous     | Uncategorized Expenses                                                      |

## Made of / held by

A name and the categories filed under it.

- [Category](category.md) — what a grouping holds.
- [User](user.md) — a catalogue of groupings is stored with each one.
- [Initialize a new user](../usecases/initialize-a-new-user.md) — stores the starting catalogue with a new user.
- [Act on a user's message](../usecases/handle-incoming-message.md) — reads the designated catch-all when it
  tells the connector which grouping to fall back on.
- [List a grouping's categories](../usecases/list-categories.md) — answers one grouping's categories by name.
- Which names may repeat under which parent
  ([ADR 0003](../adr/0003-a-category-is-unique-per-user-and-parent-not-per-user.md)).
