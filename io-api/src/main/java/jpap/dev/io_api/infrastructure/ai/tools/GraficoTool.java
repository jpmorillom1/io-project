package jpap.dev.io_api.infrastructure.ai.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.output.structured.Description;
import jpap.dev.io_api.domain.lp.FuncionObjetivo;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.Restriccion;
import jpap.dev.io_api.domain.lp.TipoObjetivo;
import jpap.dev.io_api.domain.lp.TipoRestriccion;
import jpap.dev.io_api.infrastructure.ai.ChatContextStore;
import jpap.dev.io_api.infrastructure.ai.hitl.AprobacionHumanaService;
import jpap.dev.io_api.infrastructure.ai.hitl.MetodoResolucion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adaptador @Tool del método gráfico (solo 2 variables). Con Human-in-the-Loop:
 * crea una solicitud de aprobación en lugar de resolver — el solver corre solo
 * tras el clic humano.
 */
@Slf4j
@Component
public class GraficoTool {

    private final AprobacionHumanaService aprobacionService;
    private final ChatContextStore contextStore;

    public GraficoTool(AprobacionHumanaService aprobacionService, ChatContextStore contextStore) {
        this.aprobacionService = aprobacionService;
        this.contextStore = contextStore;
    }

    public record RestriccionInput(
            @Description("Coeficientes de la restricción en el mismo orden que las 2 variables de decisión")
            List<Double> coeficientes,
            @Description("Tipo de restricción: LEQ (<=), GEQ (>=) o EQ (=)")
            TipoRestriccion tipo,
            @Description("Término independiente (lado derecho). Debe ser >= 0.")
            double rhs
    ) {}

    @Tool("""
            Solicita resolver un problema de Programación Lineal con el MÉTODO GRÁFICO.
            INVOCA ESTA HERRAMIENTA cuando se cumplan TODAS estas condiciones:
              1) El modelo tenga EXACTAMENTE 2 variables de decisión — no más, no menos.
              2) El modelo esté completamente validado por el estudiante.
              3) El estudiante haya pedido resolver usando el método gráfico explícitamente,
                 o haya pedido ver la región factible, el gráfico, o la solución visual.
            EFECTO: NO resuelve inmediatamente — envía una solicitud de aprobación a la interfaz;
            el estudiante debe confirmar con el botón Aprobar antes de que el solver se ejecute.
            Cuando el estudiante apruebe, la interfaz mostrará el gráfico con la región factible
            sombreada y los vértices evaluados (el óptimo con una estrella).
            RESTRICCIÓN TÉCNICA: falla con más o menos de 2 variables — en ese caso informa
            al estudiante que el método gráfico solo aplica para 2 variables de decisión.
            Acepta restricciones LEQ (<=), GEQ (>=) y EQ (=).
            """)
    public String resolverGrafico(
            @P("Lista de EXACTAMENTE 2 nombres de variables de decisión, ej: [\"x1\", \"x2\"]")
            List<String> variables,

            @P("Coeficientes de la función objetivo en el mismo orden que las 2 variables")
            List<Double> objetivoCoeficientes,

            @P("Tipo de optimización: MAXIMIZAR o MINIMIZAR")
            TipoObjetivo tipoObjetivo,

            @P("Lista de restricciones. Cada restricción tiene 'coeficientes' (2 valores), 'tipo' (LEQ/GEQ/EQ) y 'rhs'")
            List<RestriccionInput> restricciones
    ) {
        log.info("[TOOL] resolverGrafico — solicitud HITL, vars={}, tipo={}, restricciones={}",
                variables, tipoObjetivo, restricciones.size());

        List<Restriccion> restriccionesLP = restricciones.stream()
                .map(r -> new Restriccion(r.coeficientes(), r.tipo(), r.rhs()))
                .toList();

        ModeloLP modelo = new ModeloLP(
                variables,
                new FuncionObjetivo(objetivoCoeficientes, tipoObjetivo),
                restriccionesLP
        );

        return SolicitudAprobacionHelper.solicitar(
                aprobacionService, contextStore, modelo, MetodoResolucion.GRAFICO);
    }
}
