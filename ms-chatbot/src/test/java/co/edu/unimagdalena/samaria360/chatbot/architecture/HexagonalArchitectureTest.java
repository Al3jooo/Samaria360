package co.edu.unimagdalena.samaria360.chatbot.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Mismas reglas de domain que ms-auth, más el driver y BSON de MongoDB, que este servicio sí tiene en el classpath. */
@AnalyzeClasses(packages = "co.edu.unimagdalena.samaria360.chatbot",
        importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule dominioSinFrameworks = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "org.hibernate..",
                    "com.fasterxml.jackson..",
                    "lombok..",
                    "org.bson..",
                    "com.mongodb..")
            .because("domain debe ser Java puro");

    @ArchTest
    static final ArchRule dominioNoDependeDeOtrasCapas = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..")
            .because("en la arquitectura hexagonal las dependencias apuntan hacia domain");

    @ArchTest
    static final ArchRule aplicacionSinInfraestructuraNiFrameworks = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..infrastructure..",
                    "org.springframework..",
                    "jakarta.persistence..",
                    "org.hibernate..",
                    "org.bson..",
                    "com.mongodb..")
            .because("los casos de uso solo dependen de domain y de sus puertos");

    @ArchTest
    static final ArchRule adaptadoresDeEntradaSoloUsanPuertosDeEntrada = noClasses()
            .that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat().resideInAnyPackage("..application.service..", "..application.port.out..")
            .because("los controladores invocan los casos de uso solo a través de los puertos de entrada");
}
