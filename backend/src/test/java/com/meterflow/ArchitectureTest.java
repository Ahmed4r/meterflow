package com.meterflow;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "com.meterflow", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
  @ArchTest
  static final ArchRule controllersDoNotAccessPersistence =
      noClasses()
          .that()
          .areAnnotatedWith(RestController.class)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework.jdbc..", "jakarta.persistence..")
          .because(
              "HTTP controllers delegate transactions and persistence to application services");

  @ArchTest
  static final ArchRule controllersDoNotUseEntities =
      noClasses()
          .that()
          .areAnnotatedWith(RestController.class)
          .should()
          .dependOnClassesThat()
          .areAnnotatedWith(jakarta.persistence.Entity.class)
          .because("HTTP APIs expose validated DTOs, not JPA entities");

  @ArchTest
  static final ArchRule pricingHasNoFrameworkDependencies =
      noClasses()
          .that()
          .haveSimpleName("Pricing")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "com.openai..")
          .because("financial arithmetic must remain deterministic and independently testable");

  @ArchTest
  static final ArchRule aiProviderCannotAccessPersistence =
      noClasses()
          .that()
          .haveSimpleName("AnalystProvider")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework.jdbc..",
              "org.springframework.data..",
              "jakarta.persistence..",
              "com.meterflow.billing..",
              "com.meterflow.subscriptions..")
          .because(
              "the external provider consumes approved aggregate evidence and has no database or billing access");
}
