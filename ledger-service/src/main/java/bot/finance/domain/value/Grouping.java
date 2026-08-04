package bot.finance.domain.value;

import bot.finance.domain.exception.InvalidGroupingException;
import java.util.Arrays;
import java.util.List;

public record Grouping(String name, List<Category> categories) {

    public Grouping {
        if (name == null || name.isBlank()) {
            throw new InvalidGroupingException("grouping has no name");
        }

        if (categories == null) {
            throw new InvalidGroupingException("grouping has no category list");
        }

        for (Category category : categories) {
            if (category == null) {
                throw new InvalidGroupingException("grouping has a null category");
            }
        }

        categories = List.copyOf(categories);
    }

    public static Grouping of(String name, String... categoryNames) {
        return new Grouping(
                name, Arrays.stream(categoryNames).map(Category::new).toList());
    }

    public static String catchAllName() {
        return "Miscellaneous";
    }

    public static List<Grouping> defaults() {
        return List.of(
                of("Housing", "Rent", "Mortgage", "HOA", "Property Tax", "Home Insurance", "Repairs", "Furniture"),
                of("Groceries", "Supermarkets", "Markets", "Household Supplies"),
                of("Dining", "Restaurants", "Cafés", "Fast Food", "Delivery"),
                of(
                        "Transportation",
                        "Fuel",
                        "Public Transport",
                        "Parking",
                        "Taxis/Uber",
                        "Car Maintenance",
                        "Car Insurance"),
                of("Utilities", "Electricity", "Gas", "Water", "Internet", "Mobile Phone"),
                of("Healthcare", "Doctors", "Pharmacy", "Dental", "Vision", "Health Insurance"),
                of("Education", "Tuition", "Books", "Courses", "Certifications"),
                of("Shopping", "Clothing", "Electronics", "Home Goods", "Gifts"),
                of("Entertainment", "Movies", "Games", "Hobbies"),
                of("Travel", "Hotels", "Flights", "Vacation", "Attractions"),
                of("Pets", "Food", "Vet", "Grooming"),
                of("Family & Children", "Childcare", "School Supplies", "Toys"),
                of("Financial", "Taxes", "Bank Fees", "Loan Payments", "Interest"),
                of("Investments", "Brokerage", "Retirement", "Crypto", "Savings Transfers"),
                of("Gifts & Donations", "Charity", "Birthday Gifts", "Holidays"),
                of("Work", "Office Supplies", "Business Expenses"),
                of("Insurance", "Life", "Home", "Vehicle", "Travel"),
                of("Personal Care", "Haircuts", "Cosmetics", "Gym", "Spa"),
                of("Subscriptions", "Streaming", "Music", "Cloud Storage", "Apps & Software"),
                of(catchAllName(), "Uncategorized Expenses"));
    }
}
