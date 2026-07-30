package bot.finance.ai.architecture;

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

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(packages = "bot.finance.ai")
class CleanArchitectureTest {

    @ArchTest
    static final ArchRule layersRespectCleanArchitectureDependencies = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("bot.finance.ai.domain..")
            .layer("Application").definedBy("bot.finance.ai.application..")
            .layer("Adapter").definedBy("bot.finance.ai.adapter..")
            .whereLayer("Domain").mayNotAccessAnyLayer()
            .whereLayer("Application").mayOnlyAccessLayers("Domain")
            .whereLayer("Adapter").mayOnlyAccessLayers("Domain", "Application")
            .withOptionalLayers(true);

    @ArchTest
    static final ArchRule domainAndApplicationStayFrameworkAgnostic = noClasses()
            .that().resideInAnyPackage("bot.finance.ai.domain..", "bot.finance.ai.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "org.slf4j..", "io.grpc..", "com.google.protobuf..")
            .allowEmptyShould(true);

    /**
     * The core names the capability, the adapter names its external system: {@code IntentInferencePort}
     * lives in {@code application/port} while {@code AiIntentInferenceAdapter} lives in {@code adapter/ai}.
     * The list grows as each new adapter lands.
     */
    @ArchTest
    static final ArchRule coreTypesCarryNoExternalSystemName = noClasses()
            .that().resideInAnyPackage("bot.finance.ai.domain..", "bot.finance.ai.application..")
            .should().haveSimpleNameContaining("OpenAi")
            .orShould().haveSimpleNameContaining("Grpc")
            .orShould().haveSimpleNameContaining("Proto")
            .allowEmptyShould(true);

    /**
     * The layer rule permits {@code adapter} to access {@code application} wholesale, so without this rule
     * an inbound adapter could inject a use-case class directly instead of its port and still compile.
     */
    @ArchTest
    static final ArchRule adaptersReachUseCasesThroughPorts = noClasses()
            .that().resideInAnyPackage("bot.finance.ai.adapter..")
            .and().resideOutsideOfPackage("bot.finance.ai.adapter.config..")
            .should().dependOnClassesThat().resideInAnyPackage("bot.finance.ai.application.usecase..")
            .allowEmptyShould(true);

    /**
     * Reads the port, not the {@code dto} package: {@code RawIntent} lives in {@code dto} too and must not be
     * named {@code ...Command}, since it is an outbound port's result, not an inbound port's command. An
     * interface no {@code application/usecase} class implements is outbound and is left unchecked.
     */
    @ArchTest
    static final ArchRule inboundPortCommandsAreNamedAfterTheirUseCase = classes()
            .that().resideInAPackage("bot.finance.ai.application.port")
            .and().areInterfaces()
            .should(nameCommandParametersAfterTheirUseCase())
            .allowEmptyShould(true);

    private static ArchCondition<JavaClass> nameCommandParametersAfterTheirUseCase() {
        return new ArchCondition<>("name their application.dto parameters <UseCase>Command") {
            @Override
            public void check(JavaClass port, ConditionEvents events) {
                Set<JavaClass> implementingUseCases = port.getAllSubclasses().stream()
                        .filter(clazz -> clazz.getPackageName().equals("bot.finance.ai.application.usecase"))
                        .collect(Collectors.toSet());
                for (JavaClass useCase : implementingUseCases) {
                    String expectedCommandName = useCase.getSimpleName().replaceFirst("UseCase$", "") + "Command";
                    for (JavaMethod method : port.getMethods()) {
                        for (JavaClass parameterType : method.getRawParameterTypes()) {
                            if (parameterType.getPackageName().equals("bot.finance.ai.application.dto")) {
                                boolean satisfied = parameterType.getSimpleName().equals(expectedCommandName);
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

}
