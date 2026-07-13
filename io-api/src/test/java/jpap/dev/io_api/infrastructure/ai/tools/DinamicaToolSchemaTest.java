package jpap.dev.io_api.infrastructure.ai.tools;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Igual que RedToolSchemaTest: AiServices.tools() genera el esquema JSON del @Tool via
 * ToolSpecifications, y un parametro con genericos anidados rompe el arranque del bean
 * tutorAiService. Aqui las listas de estructuras van como List de un record plano
 * (ActividadInput, ArticuloInput, EtapaInput, ArcoInput, EdadInput) y los opcionales
 * llevan required=false: nada de genericos anidados como parametro directo.
 */
class DinamicaToolSchemaTest {

    @Test
    void los_cinco_esquemas_de_dinamica_se_generan_sin_error() {
        List<ToolSpecification> specs =
                ToolSpecifications.toolSpecificationsFrom(new DinamicaTool(null, null));

        assertEquals(5, specs.size(), "DinamicaTool expone una @Tool por submodelo");
        for (String nombre : List.of("resolverPdAsignacionRecursos", "resolverPdMochila",
                "resolverPdRutaEtapas", "resolverPdPlanificacionProduccion",
                "resolverPdReemplazoEquipos")) {
            assertTrue(specs.stream().anyMatch(s -> s.name().equals(nombre)),
                    "debe generarse la especificacion de " + nombre + " sin lanzar excepcion");
        }
    }
}
