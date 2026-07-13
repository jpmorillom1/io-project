package jpap.dev.io_api.infrastructure.ai.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.output.structured.Description;
import jpap.dev.io_api.domain.entera.ModeloEntero;
import jpap.dev.io_api.domain.entera.TipoVariable;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Adaptador @Tool de Programación Lineal Entera (Branch &amp; Bound). Con Human-in-the-Loop:
 * crea una solicitud de aprobación en lugar de resolver — el solver corre solo tras el clic humano.
 *
 * La integralidad se comunica al LLM con dos listas de NOMBRES (variablesEnteras,
 * variablesBinarias) en vez de genéricos anidados, que LangChain4j 1.13.0 no sabe esquematizar.
 */
@Slf4j
@Component
public class EnteraTool {

    private final AprobacionHumanaService aprobacionService;
    private final ChatContextStore contextStore;

    public EnteraTool(AprobacionHumanaService aprobacionService, ChatContextStore contextStore) {
        this.aprobacionService = aprobacionService;
        this.contextStore = contextStore;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RestriccionInput(
            @Description("Coeficientes de la restricción en el mismo orden que las variables de decisión")
            List<Double> coeficientes,
            @Description("Tipo de restricción: LEQ (<=), GEQ (>=) o EQ (=)")
            TipoRestriccion tipo,
            @Description("Término independiente (lado derecho). Debe ser >= 0.")
            double rhs
    ) {}

    @Tool("""
            Solicita resolver un problema de Programación Lineal ENTERA con Branch & Bound.
            INVOCA ESTA HERRAMIENTA cuando se cumplan TODAS estas condiciones:
              1) El problema exija que una o más variables tomen valores ENTEROS (cantidades
                 indivisibles: máquinas, personas, camiones) o BINARIOS (decisiones sí/no:
                 seleccionar un proyecto, abrir/cerrar una sucursal, comprar o no un equipo,
                 asignar un turno, activar un centro de distribución, elegir una ruta).
              2) El modelo esté completamente validado por el estudiante.
              3) El estudiante haya pedido resolver explícitamente.
            EFECTO: NO resuelve inmediatamente — envía una solicitud de aprobación a la interfaz;
            el estudiante debe confirmar con el botón Aprobar antes de que el solver se ejecute.
            Indica qué variables son enteras y cuáles binarias:
              - variablesBinarias: nombres de las variables 0/1 (decisiones sí/no).
              - variablesEnteras: nombres de las variables enteras generales (0,1,2,...).
              - Cualquier variable que NO aparezca en ninguna de las dos listas se trata como continua.
            Si NINGUNA variable necesita ser entera o binaria, usa resolverSimplex / resolverDosFases / resolverGranM.
            Acepta restricciones LEQ (<=), GEQ (>=) y EQ (=). El término independiente debe ser >= 0.
            """)
    public String resolverEntera(
            @P("Lista de nombres de las variables de decisión, ej: [\"x1\", \"x2\"]")
            List<String> variables,

            @P("Coeficientes de la función objetivo en el mismo orden que las variables")
            List<Double> objetivoCoeficientes,

            @P("Tipo de optimización: MAXIMIZAR o MINIMIZAR")
            TipoObjetivo tipoObjetivo,

            @P("Lista de restricciones. Cada restricción tiene 'coeficientes', 'tipo' (LEQ/GEQ/EQ) y 'rhs'")
            List<RestriccionInput> restricciones,

            @P(value = "Nombres de las variables ENTERAS generales (0,1,2,...). OMITE este parámetro si no hay ninguna; NUNCA envíes null.", required = false)
            List<String> variablesEnteras,

            @P(value = "Nombres de las variables BINARIAS (0/1, decisiones sí/no). OMITE este parámetro si no hay ninguna; NUNCA envíes null.", required = false)
            List<String> variablesBinarias
    ) {
        List<String> enteras = variablesEnteras != null ? variablesEnteras : List.of();
        List<String> binarias = variablesBinarias != null ? variablesBinarias : List.of();

        log.info("[TOOL] resolverEntera — solicitud HITL, vars={}, tipo={}, restricciones={}, enteras={}, binarias={}",
                variables, tipoObjetivo, restricciones.size(), enteras, binarias);

        List<Restriccion> restriccionesLP = restricciones.stream()
                .map(r -> new Restriccion(r.coeficientes(), r.tipo(), r.rhs()))
                .toList();

        List<TipoVariable> tipos = new ArrayList<>(variables.size());
        for (String v : variables) {
            if (binarias.contains(v)) tipos.add(TipoVariable.BINARIA);
            else if (enteras.contains(v)) tipos.add(TipoVariable.ENTERA);
            else tipos.add(TipoVariable.CONTINUA);
        }

        ModeloEntero modelo = new ModeloEntero(
                new ModeloLP(variables, new FuncionObjetivo(objetivoCoeficientes, tipoObjetivo), restriccionesLP),
                tipos
        );

        return SolicitudAprobacionHelper.solicitar(
                aprobacionService, contextStore, modelo, MetodoResolucion.BRANCH_AND_BOUND);
    }
}
