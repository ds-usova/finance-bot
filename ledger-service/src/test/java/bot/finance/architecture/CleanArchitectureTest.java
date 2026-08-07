package bot.finance.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import bot.finance.domain.model.Entity;
import bot.finance.domain.value.AuthenticatedUserId;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;
import java.util.stream.Collectors;

@AnalyzeClasses(packages = "bot.finance")
class CleanArchitectureTest {

    @ArchTest
    static final ArchRule layersRespectCleanArchitectureDependencies = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain")
            .definedBy("bot.finance.domain..")
            .layer("Application")
            .definedBy("bot.finance.application..")
            .layer("Adapter")
            .definedBy("bot.finance.adapter..")
            .whereLayer("Domain")
            .mayNotAccessAnyLayer()
            .whereLayer("Application")
            .mayOnlyAccessLayers("Domain")
            .whereLayer("Adapter")
            .mayOnlyAccessLayers("Domain", "Application")
            .withOptionalLayers(true);

    @ArchTest
    static final ArchRule domainAndApplicationStayFrameworkAgnostic = noClasses()
            .that()
            .resideInAnyPackage("bot.finance.domain..", "bot.finance.application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta..",
                    "org.slf4j..",
                    "com.pengrad..",
                    "io.grpc..",
                    "com.google.protobuf..",
                    "bot.finance.ai..",
                    "bot.finance.api..",
                    "io.modelcontextprotocol..")
            .allowEmptyShould(true);

    /**
     * The core names the capability, the adapter names its external system: {@code HandleIncomingMessagePort}
     * lives in {@code application/port} while {@code TelegramUpdateListener} lives in {@code adapter/telegram}.
     * The list is seeded from the external systems in the C3 diagram and grows as each new adapter lands.
     */
    @ArchTest
    static final ArchRule coreTypesCarryNoExternalSystemName = noClasses()
            .that()
            .resideInAnyPackage("bot.finance.domain..", "bot.finance.application..")
            .should()
            .haveSimpleNameContaining("Telegram")
            .orShould()
            .haveSimpleNameContaining("Whisper")
            .orShould()
            .haveSimpleNameContaining("Postgres")
            .orShould()
            .haveSimpleNameContaining("AiConnector")
            .orShould()
            .haveSimpleNameContaining("Grpc")
            .orShould()
            .haveSimpleNameContaining("Proto")
            .orShould()
            .haveSimpleNameContaining("Mcp")
            .orShould()
            .haveSimpleNameContaining("Jwt")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule everyDomainModelClassIsAnEntity = classes()
            .that()
            .resideInAPackage("bot.finance.domain.model")
            .and()
            .areTopLevelClasses()
            .and()
            .doNotHaveSimpleName("package-info")
            .and()
            .haveSimpleNameNotEndingWith("Test")
            .should()
            .beAssignableTo(Entity.class);

    /**
     * Reads the port, not the {@code dto} package: {@code IntentExtractionRequest} lives in {@code dto} too and
     * must not be named {@code ...Command}, since it is an outbound port's input, not an inbound port's command.
     * An interface no {@code application/usecase} class implements is outbound and is left unchecked.
     */
    @ArchTest
    static final ArchRule inboundPortCommandsAreNamedAfterTheirUseCase = classes()
            .that()
            .resideInAPackage("bot.finance.application.port")
            .and()
            .areInterfaces()
            .should(nameCommandParametersAfterTheirUseCase())
            .allowEmptyShould(true);

    private static ArchCondition<JavaClass> nameCommandParametersAfterTheirUseCase() {
        return new ArchCondition<>("name their application.dto parameters <UseCase>Command") {
            @Override
            public void check(JavaClass port, ConditionEvents events) {
                Set<JavaClass> implementingUseCases = port.getAllSubclasses().stream()
                        .filter(clazz -> clazz.getPackageName().equals("bot.finance.application.usecase"))
                        .collect(Collectors.toSet());
                for (JavaClass useCase : implementingUseCases) {
                    String expectedCommandName = useCase.getSimpleName().replaceFirst("UseCase$", "") + "Command";
                    for (JavaMethod method : port.getMethods()) {
                        for (JavaClass parameterType : method.getRawParameterTypes()) {
                            if (parameterType.getPackageName().equals("bot.finance.application.dto")) {
                                boolean satisfied =
                                        parameterType.getSimpleName().equals(expectedCommandName);
                                events.add(new SimpleConditionEvent(
                                        port,
                                        satisfied,
                                        String.format(
                                                "%s's parameter %s should be named %s, matching the use case "
                                                        + "implementing it, %s",
                                                port.getName(),
                                                parameterType.getSimpleName(),
                                                expectedCommandName,
                                                useCase.getSimpleName())));
                            }
                        }
                    }
                }
            }
        };
    }

    /**
     * {@code @AnalyzeClasses(packages = "bot.finance")} scans test classes too, so a fixture that builds an
     * {@link AuthenticatedUserId} for a test is excluded rather than flagged: a class whose top-level name ends
     * with {@code Test}, and any class in {@code bot.finance.common}.
     */
    @ArchTest
    static final ArchRule authenticatedUserIdIsConstructedOnlyBySecurityAdapter = noClasses()
            .that()
            .resideOutsideOfPackage("bot.finance.adapter.security..")
            .and(DescribedPredicate.not(topLevelClassNameEndingWithTest()))
            .and()
            .resideOutsideOfPackage("bot.finance.common..")
            .should()
            .callConstructor(AuthenticatedUserId.class)
            .allowEmptyShould(true);

    private static DescribedPredicate<JavaClass> topLevelClassNameEndingWithTest() {
        return new DescribedPredicate<>("have a top-level class name ending with Test") {
            @Override
            public boolean test(JavaClass javaClass) {
                JavaClass topLevelClass = javaClass;
                while (topLevelClass.getEnclosingClass().isPresent()) {
                    topLevelClass = topLevelClass.getEnclosingClass().get();
                }
                return topLevelClass.getSimpleName().endsWith("Test");
            }
        };
    }
}
