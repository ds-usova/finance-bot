package bot.finance.domain.value;

import bot.finance.domain.exception.InvalidCategoryException;
import java.util.Arrays;
import java.util.List;

public record Category(String name, List<Category> children) {

    public Category {
        if (name == null || name.isBlank()) {
            throw new InvalidCategoryException("category has no name");
        }

        if (children == null) {
            throw new InvalidCategoryException("category has no child list");
        }

        for (Category child : children) {
            if (child == null) {
                throw new InvalidCategoryException("category has a null child");
            }
            if (!child.children().isEmpty()) {
                throw new InvalidCategoryException("category tree exceeds two levels");
            }
        }

        children = List.copyOf(children);
    }

    public static Category leaf(String name) {
        return new Category(name, List.of());
    }

    public static Category group(String name, String... childNames) {
        return new Category(name, Arrays.stream(childNames).map(Category::leaf).toList());
    }

    public static String catchAllGroupingName() {
        return "Miscellaneous";
    }

    public static List<Category> defaults() {
        return List.of(
                group("Housing", "Rent", "Mortgage", "HOA", "Property Tax", "Home Insurance", "Repairs", "Furniture"),
                group("Groceries", "Supermarkets", "Markets", "Household Supplies"),
                group("Dining", "Restaurants", "Cafés", "Fast Food", "Delivery"),
                group(
                        "Transportation",
                        "Fuel",
                        "Public Transport",
                        "Parking",
                        "Taxis/Uber",
                        "Car Maintenance",
                        "Car Insurance"),
                group("Utilities", "Electricity", "Gas", "Water", "Internet", "Mobile Phone"),
                group("Healthcare", "Doctors", "Pharmacy", "Dental", "Vision", "Health Insurance"),
                group("Education", "Tuition", "Books", "Courses", "Certifications"),
                group("Shopping", "Clothing", "Electronics", "Home Goods", "Gifts"),
                group("Entertainment", "Movies", "Games", "Hobbies"),
                group("Travel", "Hotels", "Flights", "Vacation", "Attractions"),
                group("Pets", "Food", "Vet", "Grooming"),
                group("Family & Children", "Childcare", "School Supplies", "Toys"),
                group("Financial", "Taxes", "Bank Fees", "Loan Payments", "Interest"),
                group("Investments", "Brokerage", "Retirement", "Crypto", "Savings Transfers"),
                group("Gifts & Donations", "Charity", "Birthday Gifts", "Holidays"),
                group("Work", "Office Supplies", "Business Expenses"),
                group("Insurance", "Life", "Home", "Vehicle", "Travel"),
                group("Personal Care", "Haircuts", "Cosmetics", "Gym", "Spa"),
                group("Subscriptions", "Streaming", "Music", "Cloud Storage", "Apps & Software"),
                group(catchAllGroupingName(), "Uncategorized Expenses"));
    }
}
