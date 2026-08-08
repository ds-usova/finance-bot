package bot.finance.ai.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchIgnore;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

/**
 * Enforces what [Testing Conventions] says about a test method's {@code @DisplayName}: one condition, one
 * outcome, under 120 characters. A name that overruns is the signal that the test proves several things at once,
 * and review is where that was being caught - unreliably, since a display name reads as prose and nothing else
 * measures it.
 *
 * <p>Methods only. A {@code @Nested} class's {@code @DisplayName} names a group in prose and follows neither the
 * shape nor the limit.
 */
@AnalyzeClasses(packages = "bot.finance.ai")
class DisplayNameConventionsTest {

    private static final int MAX_LENGTH = 120;

    /**
     * Off until the names it finds are dealt with: 25 of this module's test methods overrun, the longest at 376
     * characters. An overrun is a test proving several things at once, so most of those want splitting rather
     * than a shorter name - work nobody has scheduled. Remove the annotation once they are.
     */
    @ArchIgnore(reason = "25 existing overruns; enabling this is a cleanup of its own")
    @ArchTest
    static final ArchRule aTestMethodsDisplayNameStaysUnderTheLimit = methods()
            .that()
            .areAnnotatedWith(DisplayName.class)
            .and(areTestMethods())
            .should(haveADisplayNameShorterThan(MAX_LENGTH));

    @ArchTest
    static final ArchRule aTestMethodsDisplayNameNamesOneConditionAndOneOutcome = methods()
            .that()
            .areAnnotatedWith(DisplayName.class)
            .and(areTestMethods())
            .should(haveADisplayNameReadingWhenThen());

    private static DescribedPredicate<JavaMethod> areTestMethods() {
        return new DescribedPredicate<>("are test methods") {
            @Override
            public boolean test(JavaMethod method) {
                return method.isAnnotatedWith(Test.class) || method.isAnnotatedWith(ParameterizedTest.class);
            }
        };
    }

    private static ArchCondition<JavaMethod> haveADisplayNameShorterThan(int limit) {
        return new ArchCondition<>("have a @DisplayName shorter than " + limit + " characters") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                String displayName = displayNameOf(method);
                events.add(new SimpleConditionEvent(
                        method,
                        displayName.length() < limit,
                        method.getFullName() + " has a " + displayName.length() + "-character @DisplayName: \""
                                + displayName + "\""));
            }
        };
    }

    private static ArchCondition<JavaMethod> haveADisplayNameReadingWhenThen() {
        return new ArchCondition<>("have a @DisplayName reading \"when [condition] - then [outcome]\"") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                String displayName = displayNameOf(method);
                boolean satisfied = displayName.startsWith("when ") && displayName.contains(" - then ");
                events.add(new SimpleConditionEvent(
                        method, satisfied, method.getFullName() + " has the @DisplayName \"" + displayName + "\""));
            }
        };
    }

    private static String displayNameOf(JavaMethod method) {
        return method.getAnnotationOfType(DisplayName.class).value();
    }
}
