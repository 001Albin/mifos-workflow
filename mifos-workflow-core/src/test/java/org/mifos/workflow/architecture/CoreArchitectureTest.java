package org.mifos.workflow.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture enforcement test for mifos-workflow-core.
 *
 * This test runs on every mvn test and fails the build if anyone
 * adds an engine-specific import to the core module.
 *
 * RULE: Core must remain engine-agnostic forever.
 * No Flowable imports. No Conductor imports. No exceptions.
 *
 * If this test fails it means someone tried to couple the
 * neutral contract layer to a specific engine implementation.
 * The fix is to move that code to the appropriate adapter module.
 */
@AnalyzeClasses(
        packages = "org.mifos.workflow",
        importOptions = ImportOption.DoNotIncludeTests.class
)
public class CoreArchitectureTest {

    /**
     * No class in core may import anything from Flowable.
     *
     * Flowable code belongs exclusively in:
     * mifos-workflow-adapter-flowable
     *
     * If this rule fails, move the offending class to adapter-flowable.
     */
    @ArchTest
    static final ArchRule no_flowable_imports_in_core =
            noClasses()
                    .that().resideInAPackage("org.mifos.workflow..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("org.flowable..")
                    .because("Core must be engine-agnostic. " +
                            "Flowable code belongs in mifos-workflow-adapter-flowable.");

    /**
     * No class in core may import anything from Netflix Conductor.
     *
     * Conductor code belongs exclusively in:
     * mifos-workflow-adapter-conductor
     *
     * If this rule fails, move the offending class to adapter-conductor.
     */
    @ArchTest
    static final ArchRule no_conductor_imports_in_core =
            noClasses()
                    .that().resideInAPackage("org.mifos.workflow..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.netflix.conductor..")
                    .because("Core must be engine-agnostic. " +
                            "Conductor code belongs in mifos-workflow-adapter-conductor.");

    /**
     * No class in core may import anything from Orkes Conductor client.
     * Orkes is the commercial distribution of Netflix Conductor.
     */
    @ArchTest
    static final ArchRule no_orkes_imports_in_core =
            noClasses()
                    .that().resideInAPackage("org.mifos.workflow..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("io.orkes.conductor..")
                    .because("Core must be engine-agnostic. " +
                            "Conductor code belongs in mifos-workflow-adapter-conductor.");

    /**
     * No class in core may import Spring Web classes.
     *
     * Spring Web (RestController, RequestMapping etc.) belongs in:
     * mifos-workflow-app
     *
     * Core is a plain library — it must never start an HTTP server.
     */
    @ArchTest
    static final ArchRule no_spring_web_in_core =
            noClasses()
                    .that().resideInAPackage("org.mifos.workflow..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("org.springframework.web..")
                    .because("Spring Web belongs in mifos-workflow-app. " +
                            "Core must not contain HTTP server code.");
}