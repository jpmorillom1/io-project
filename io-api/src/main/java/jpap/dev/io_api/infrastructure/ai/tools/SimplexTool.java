package jpap.dev.io_api.infrastructure.ai.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
 * Adaptador @Tool del método Simplex. Desde la introducción del Human-in-the-Loop
 * NO resuelve directamente: crea una solicitud de aprobación que el estudiante debe
 * confirmar en la interfaz. El solver solo se ejecuta cuando el humano aprueba
 * (compuerta estructural — el LLM no puede saltársela).
 */
@Slf4j
@Component
public class SimplexTool {

    private final AprobacionHumanaService aprobacionService;
    private final ChatContextStore contextStore;

    public SimplexTool(AprobacionHumanaService aprobacionService, ChatContextStore contextStore) {
        this.aprobacionService = aprobacionService;
        this.contextStore = contextStore;
    }

    /**
     * Tipo auxiliar para describir cada restricción al LLM.
     * @JsonIgnoreProperties ignora "tipo" si el LLM lo envía — SimplexSolver
     * siempre usa LEQ, así que el campo no aplica aquí.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RestriccionInput(
            @Description("Coeficientes de la restricción en el mismo orden que las variables de decisión")
            List<Double> coeficientes,
            @Description("Término independiente (lado derecho). Debe ser >= 0 para Simplex estándar")
            double rhs
    ) {}

    @Tool("""
            Solicita resolver un problema de Programación Lineal con el método Simplex estándar.
            INVOCA ESTA HERRAMIENTA SOLO cuando se cumplan AMBAS condiciones:
              1) El modelo esté completamente validado por el estudiante (variables, función objetivo y restricciones confirmadas).
              2) El estudiante haya pedido explícitamente resolver o calcular.
            EFECTO: NO resuelve inmediatamente — envía una solicitud de aprobación a la interfaz;
            el estudiante debe confirmar el modelo con el botón Aprobar antes de que el solver se ejecute.
            RESTRICCIONES TÉCNICAS: solo funciona con restricciones de tipo <= y término independiente >= 0.
            Si el modelo tiene >= o =, informa al estudiante que necesita Dos Fases o Gran M.
            """)
    public String resolverSimplex(
            @P("Lista de nombres de las variables de decisión, ej: [\"x1\", \"x2\"]")
            List<String> variables,

            @P("Coeficientes de la función objetivo en el mismo orden que las variables")
            List<Double> objetivoCoeficientes,

            @P("Tipo de optimización: MAXIMIZAR o MINIMIZAR")
            TipoObjetivo tipoObjetivo,

            @P("Lista de restricciones del modelo (todas de tipo <=). Cada restricción tiene 'coeficientes' y 'rhs'")
            List<RestriccionInput> restricciones
    ) {
        log.info("[TOOL] resolverSimplex — solicitud HITL, vars={}, tipo={}, restricciones={}",
                variables, tipoObjetivo, restricciones.size());

        List<Restriccion> restriccionesLP = restricciones.stream()
                .map(r -> new Restriccion(r.coeficientes(), TipoRestriccion.LEQ, r.rhs()))
                .toList();

        ModeloLP modelo = new ModeloLP(
                variables,
                new FuncionObjetivo(objetivoCoeficientes, tipoObjetivo),
                restriccionesLP
        );

        return SolicitudAprobacionHelper.solicitar(
                aprobacionService, contextStore, modelo, MetodoResolucion.SIMPLEX);
    }
}
