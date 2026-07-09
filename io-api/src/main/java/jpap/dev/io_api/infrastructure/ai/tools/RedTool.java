package jpap.dev.io_api.infrastructure.ai.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.output.structured.Description;
import jpap.dev.io_api.domain.redes.Arista;
import jpap.dev.io_api.domain.redes.MetodoRed;
import jpap.dev.io_api.domain.redes.ModeloRed;
import jpap.dev.io_api.infrastructure.ai.ChatContextStore;
import jpap.dev.io_api.infrastructure.ai.hitl.AprobacionHumanaService;
import jpap.dev.io_api.infrastructure.ai.hitl.MetodoResolucion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adaptador @Tool de los problemas de redes. Con Human-in-the-Loop:
 * crea una solicitud de aprobación en lugar de resolver — el solver corre solo
 * tras el clic humano.
 *
 * Dos tools: resolverRed (Dijkstra/Kruskal/Edmonds-Karp/MCF, entrada = grafo) y
 * resolverAsignacion (entrada = matriz de costos). El submétodo concreto viaja
 * dentro del ModeloRed; para el chat siempre se usa MetodoResolucion.REDES.
 */
@Slf4j
@Component
public class RedTool {

    private final AprobacionHumanaService aprobacionService;
    private final ChatContextStore contextStore;

    public RedTool(AprobacionHumanaService aprobacionService, ChatContextStore contextStore) {
        this.aprobacionService = aprobacionService;
        this.contextStore = contextStore;
    }

    /**
     * Una arista del grafo. Se usa un record plano porque LangChain4j 1.13.0 no genera
     * el esquema JSON de un parámetro con genéricos anidados (List&lt;List&lt;Double&gt;&gt;).
     *
     * peso/capacidad/costo llevan @JsonProperty(required = false): Groq valida la tool call
     * generada contra el esquema y rechaza tanto null como la ausencia de un campo requerido
     * de tipo number — el campo debe ser opcional en el esquema y OMITIRSE cuando no aplique.
     * ignoreUnknown: si el LLM inventa un campo extra, no debe tumbar el turno entero.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AristaInput(
            @Description("Nodo origen de la arista")
            String origen,
            @Description("Nodo destino de la arista")
            String destino,
            @JsonProperty(required = false)
            @Description("Peso/distancia de la arista — obligatorio para DIJKSTRA y KRUSKAL; en los demás métodos OMITE este campo por completo (nunca envíes null)")
            Double peso,
            @JsonProperty(required = false)
            @Description("Capacidad del arco — obligatoria para EDMONDS_KARP y FLUJO_COSTO_MINIMO; en los demás métodos OMITE este campo por completo (nunca envíes null)")
            Double capacidad,
            @JsonProperty(required = false)
            @Description("Costo unitario del arco — obligatorio para FLUJO_COSTO_MINIMO; en los demás métodos OMITE este campo por completo (nunca envíes null)")
            Double costo
    ) {}

    /** Una fila de la matriz de costos de asignación (un agente hacia cada tarea). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FilaCostos(
            @Description("Costos de este agente para cada tarea, en el orden de 'tareas'")
            List<Double> costos
    ) {}

    @Tool("""
            Solicita resolver un PROBLEMA DE REDES sobre un grafo: ruta más corta, árbol de
            expansión mínima, flujo máximo o flujo de costo mínimo.
            INVOCA ESTA HERRAMIENTA cuando se cumplan TODAS estas condiciones:
              1) El problema esté definido como un grafo: nodos y aristas/arcos con sus
                 pesos, capacidades o costos.
              2) El modelo esté completamente validado por el estudiante.
              3) El estudiante haya pedido resolver explícitamente.
            ELECCIÓN DEL MÉTODO (parámetro 'metodo'):
              - DIJKSTRA → ruta más corta entre dos nodos (aristas con 'peso' ≥ 0; usa
                'fuente' como origen y 'sumidero' como destino de la ruta).
              - KRUSKAL → árbol de expansión mínima: conectar TODOS los nodos con el menor
                peso total (grafo no dirigido; aristas con 'peso'; no lleva fuente/sumidero).
              - EDMONDS_KARP → flujo máximo entre 'fuente' y 'sumidero' (arcos con 'capacidad').
              - FLUJO_COSTO_MINIMO → enviar el máximo flujo de 'fuente' a 'sumidero' al menor
                costo total (arcos con 'capacidad' Y 'costo').
            Para ASIGNACIÓN (agentes a tareas con matriz de costos) usa resolverAsignacion, NO esta.
            IMPORTANTE: en cada arista incluye SOLO los campos numéricos que el método usa
            (peso para DIJKSTRA/KRUSKAL; capacidad para EDMONDS_KARP; capacidad y costo para
            FLUJO_COSTO_MINIMO) y OMITE los demás — nunca envíes un campo con valor null.
            EFECTO: NO resuelve inmediatamente — envía una solicitud de aprobación a la interfaz;
            el estudiante debe confirmar con el botón Aprobar antes de que el solver se ejecute.
            """)
    public String resolverRed(
            @P("Nombres de los nodos del grafo, ej: [\"A\", \"B\", \"C\"]")
            List<String> nodos,

            @P("Aristas del grafo; en cada una llena solo los campos que el método necesita (peso, capacidad y/o costo)")
            List<AristaInput> aristas,

            @P(value = "true si el grafo es dirigido (arcos), false si no dirigido; KRUSKAL siempre lo trata como no dirigido", required = false)
            Boolean dirigido,

            @P(value = "Método: DIJKSTRA (por defecto), KRUSKAL, EDMONDS_KARP o FLUJO_COSTO_MINIMO", required = false)
            MetodoRed metodo,

            @P(value = "Nodo fuente/origen — obligatorio salvo en KRUSKAL (en KRUSKAL omítelo)", required = false)
            String fuente,

            @P(value = "Nodo sumidero/destino — obligatorio en EDMONDS_KARP y FLUJO_COSTO_MINIMO; en DIJKSTRA es el destino de la ruta; en KRUSKAL omítelo", required = false)
            String sumidero
    ) {
        MetodoRed metodoEfectivo = metodo != null ? metodo : MetodoRed.DIJKSTRA;
        Boolean dirigidoEfectivo = dirigido != null ? dirigido : true;
        if (metodoEfectivo == MetodoRed.ASIGNACION) {
            return "ERROR: para un problema de ASIGNACIÓN usa la herramienta resolverAsignacion "
                    + "con agentes, tareas y la matriz de costos.";
        }
        log.info("[TOOL] resolverRed — solicitud HITL, nodos={}, aristas={}, metodo={}",
                nodos != null ? nodos.size() : 0, aristas != null ? aristas.size() : 0, metodoEfectivo);

        List<Arista> lista = aristas == null ? List.of() : aristas.stream()
                .map(a -> new Arista(a.origen(), a.destino(), a.peso(), a.capacidad(), a.costo()))
                .toList();
        boolean esDirigido = dirigido != null ? dirigido : metodoEfectivo != MetodoRed.KRUSKAL;
        ModeloRed modelo = new ModeloRed(nodos, lista, esDirigido, metodoEfectivo, fuente, sumidero,
                null, null, null);

        return SolicitudAprobacionHelper.solicitar(
                aprobacionService, contextStore, modelo, MetodoResolucion.REDES);
    }

    @Tool("""
            Solicita resolver un PROBLEMA DE ASIGNACIÓN: n agentes (trabajadores, máquinas...)
            deben asignarse uno-a-uno a m tareas minimizando el costo total, dada la matriz
            de costos agente×tarea. Se resuelve por reducción a red de flujo de costo mínimo.
            INVOCA ESTA HERRAMIENTA cuando se cumplan TODAS estas condiciones:
              1) El problema tenga agentes, tareas y una matriz de costos agente→tarea.
              2) El modelo esté completamente validado por el estudiante.
              3) El estudiante haya pedido resolver explícitamente.
            EFECTO: NO resuelve inmediatamente — envía una solicitud de aprobación a la interfaz;
            el estudiante debe confirmar con el botón Aprobar antes de que el solver se ejecute.
            NOTA: si hay distinto número de agentes y tareas el sistema balancea automáticamente
            con un agente/tarea ficticio de costo 0; no hace falta que lo balancees tú.
            """)
    public String resolverAsignacion(
            @P("Nombres de los agentes (filas de la matriz), ej: [\"Obrero 1\", \"Obrero 2\"]")
            List<String> agentes,

            @P("Nombres de las tareas (columnas de la matriz), ej: [\"Tarea A\", \"Tarea B\"]")
            List<String> tareas,

            @P("Lista PLANA de costos unitarios concatenados fila por fila (de cada agente hacia cada tarea). Ejemplo para 2 agentes y 2 tareas: [10, 15, 12, 8]. IMPORTANTE: DEBE ser un arreglo plano 1D de números, NO una matriz 2D [[...]] ni objetos.")
            List<Double> matrizCostos
    ) {
        log.info("[TOOL] resolverAsignacion — solicitud HITL, agentes={}, tareas={}",
                agentes != null ? agentes.size() : 0, tareas != null ? tareas.size() : 0);

        List<List<Double>> matriz = new java.util.ArrayList<>();
        int m = agentes != null ? agentes.size() : 0;
        int n = tareas != null ? tareas.size() : 0;
        if (matrizCostos != null && m > 0 && n > 0) {
            for (int i = 0; i < m; i++) {
                List<Double> fila = new java.util.ArrayList<>();
                for (int j = 0; j < n; j++) {
                    int idx = i * n + j;
                    fila.add(idx < matrizCostos.size() ? matrizCostos.get(idx) : 0.0);
                }
                matriz.add(fila);
            }
        }

        ModeloRed modelo = new ModeloRed(null, null, true, MetodoRed.ASIGNACION, null, null,
                agentes, tareas, matriz);

        return SolicitudAprobacionHelper.solicitar(
                aprobacionService, contextStore, modelo, MetodoResolucion.REDES);
    }
}
