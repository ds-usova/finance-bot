package bot.finance.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(packages = "bot.finance")
class CleanArchitectureTest {

    @ArchTest
    static final ArchRule layersRespectCleanArchitectureDependencies = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("bot.finance.domain..")
            .layer("Application").definedBy("bot.finance.application..")
            .layer("Adapter").definedBy("bot.finance.adapter..")
            .whereLayer("Domain").mayNotAccessAnyLayer()
            .whereLayer("Application").mayOnlyAccessLayers("Domain")
            .whereLayer("Adapter").mayOnlyAccessLayers("Domain", "Application")
            .withOptionalLayers(true);

    @ArchTest
    static final ArchRule domainAndApplicationStayFrameworkAgnostic = noClasses()
            .that().resideInAnyPackage("bot.finance.domain..", "bot.finance.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "org.slf4j..", "com.pengrad..")
            .allowEmptyShould(true);

    /**
     * The core names the capability, the adapter names its external system: {@code HandleIncomingMessagePort}
     * lives in {@code application/port} while {@code TelegramUpdateListener} lives in {@code adapter/telegram}.
     * The list is seeded from the external systems in the C3 diagram and grows as each new adapter lands.
     */
    @ArchTest
    static final ArchRule coreTypesCarryNoExternalSystemName = noClasses()
            .that().resideInAnyPackage("bot.finance.domain..", "bot.finance.application..")
            .should().haveSimpleNameContaining("Telegram")
            .orShould().haveSimpleNameContaining("Whisper")
            .orShould().haveSimpleNameContaining("Postgres")
            .allowEmptyShould(true);

}
