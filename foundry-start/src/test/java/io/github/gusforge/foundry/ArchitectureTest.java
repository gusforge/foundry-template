package io.github.gusforge.foundry;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

/** 把依赖方向和命名约定变成会失败的测试：写在文档里是建议，写成 failing test 才是约束。 */
@AnalyzeClasses(
        packages = "io.github.gusforge.foundry",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule layers =
            layeredArchitecture()
                    .consideringOnlyDependenciesInLayers()
                    .layer("Web")
                    .definedBy("..controller..")
                    .layer("Service")
                    .definedBy("..service..")
                    .layer("Dao")
                    .definedBy("..repository..", "..mapper..")
                    .whereLayer("Web")
                    .mayNotBeAccessedByAnyLayer()
                    .whereLayer("Service")
                    .mayOnlyBeAccessedByLayers("Web")
                    .whereLayer("Dao")
                    .mayOnlyBeAccessedByLayers("Service");

    /** Entity 不得泄漏到对外契约层；这是 api 模块（第 5 步）拆出来之后契约隔离的前提。 */
    @ArchTest
    static final ArchRule entities_do_not_leak_to_web_or_model =
            noClasses()
                    .that()
                    .resideInAnyPackage("..controller..", "..support..", "..model..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..entity..");

    @ArchTest
    static final ArchRule service_does_not_depend_on_web =
            noClasses()
                    .that()
                    .resideInAPackage("..service..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..controller..", "..support..");

    @ArchTest
    static final ArchRule rest_controllers_are_named_controller =
            classes()
                    .that()
                    .areAnnotatedWith(RestController.class)
                    .should()
                    .haveSimpleNameEndingWith("Controller");
}
