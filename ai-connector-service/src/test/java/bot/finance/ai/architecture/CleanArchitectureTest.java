package bot.finance.ai.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

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

}
