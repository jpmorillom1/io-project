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
 * Adaptador @Tool del método Gran M. Con Human-in-the-Loop: crea una solicitud
 * de aprobación en lugar de resolver — el solver corre solo tras el clic humano.
 */
@Slf4j
@Component
public class GranMTool {

    private final AprobacionHumanaService aprobacionService;
    private final ChatContextStore contextStore;

    public GranMTool(AprobacionHumanaService aprobacionService, ChatContextStore contextStore) {
        this.aprobacionService = aprobacionService;
        this.contextStore = contextStore;
    }

    public record RestriccionInput(
            @Description("Coeficientes de la restricción en el mismo orden que las variables de decisión")
            List<Double> coeficientes,
            @Description("Tipo de restricción: LEQ (<=), GEQ (>=) o EQ (=)")
            TipoRestriccion tipo,
            @Description("Término independiente (lado derecho). Debe ser >= 0.")
            double rhs
    ) {}

    @Tool("""
            Solicita resolver un problema de Programación Lineal con el método Gran M (penalidad).
            INVOCA ESTA HERRAMIENTA cuando se cumplan TODAS estas condiciones:
              1) El modelo tenga restricciones >= (GEQ) y/o = (EQ), Y el estudiante haya pedido
                 específicamente el método Gran M, O esté aprendiendo el método de penalidad.
              2) El modelo esté completamente validado por el estudiante.
              3) El estudiante haya pedido resolver explícitamente.
            EFECTO: NO resuelve inmediatamente — envía una solicitud de aprobación a la interfaz;
            el estudiante debe confirmar con el botón Aprobar antes de que el solver se ejecute.
            Si el modelo solo tiene <= y el estudiante no pidió Gran M, usa resolverSimplex.
            Si el modelo tiene >= o = pero el estudiante no pidió Gran M específicamente, usa resolverDosFases.
            Acepta restricciones LEQ (<=), GEQ (>=) y EQ (=). El término independiente debe ser >= 0.
            """)
    public String resolverGranM(
            @P("Lista de nombres de las variables de decisión, ej: [\"x1\", \"x2\"]")
            List<String> variables,

            @P("Coeficientes de la función objetivo en el mismo orden que las variables")
            List<Double> objetivoCoeficientes,

            @P("Tipo de optimización: MAXIMIZAR o MINIMIZAR")
            TipoObjetivo tipoObjetivo,

            @P("Lista de restricciones. Cada restricción tiene 'coeficientes', 'tipo' (LEQ/GEQ/EQ) y 'rhs'")
            List<RestriccionInput> restricciones
    ) {
        log.info("[TOOL] resolverGranM — solicitud HITL, vars={}, tipo={}, restricciones={}",
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
                aprobacionService, contextStore, modelo, MetodoResolucion.GRAN_M);
    }
}
