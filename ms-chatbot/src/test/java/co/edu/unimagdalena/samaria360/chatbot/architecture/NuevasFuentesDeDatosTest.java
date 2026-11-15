package co.edu.unimagdalena.samaria360.chatbot.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * RNF07: integrar nuevas fuentes de datos sin modificar los módulos existentes. Cada fuente de datos
 * (MongoDB de catalog_db y GridFS) queda detrás de un puerto de salida; una fuente nueva es un adaptador nuevo que implementa el
 * puerto y se registra en infrastructure/config, sin tocar domain, application ni los adaptadores de entrada.
 */
@AnalyzeClasses(packages = "co.edu.unimagdalena.samaria360.chatbot",
        importOptions = ImportOption.DoNotIncludeTests.class)
class NuevasFuentesDeDatosTest {

    @ArchTest
    static final ArchRule puertosDeSalidaSonInterfaces = classes()
            .that().resideInAPackage("..application.port.out..")
            .and().haveSimpleNameEndingWith("Port")
            .should().beInterfaces()
            .because("un puerto de salida es un contrato que cualquier adaptador puede implementar (RNF07)");

    @ArchTest
    static final ArchRule adaptadoresDeSalidaImplementanUnPuerto = classes()
            .that().resideInAPackage("..infrastructure.adapter.out..")
            .and().haveSimpleNameEndingWith("Adapter")
            .should().implement(JavaClass.Predicates.resideInAPackage("..application.port.out.."))
            .because("cada adaptador de salida es intercambiable detrás de su puerto (RNF07)");

    @ArchTest
    static final ArchRule fuentesDeDatosSoloSeConectanEnConfig = noClasses()
            .that().resideOutsideOfPackages("..infrastructure.adapter.out.persistence..", "..infrastructure.config..")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure.adapter.out.persistence..")
            .because("solo BeanConfig conoce el adaptador concreto de una fuente de datos; cambiarla o agregar"
                    + " otra no modifica los demás módulos (RNF07)");
}
