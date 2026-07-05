package jpap.dev.io_api.infrastructure.ai.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.output.structured.Description;
import jpap.dev.io_api.domain.transporte.MetodoTransporte;
import jpap.dev.io_api.domain.transporte.ModeloTransporte;
import jpap.dev.io_api.infrastructure.ai.ChatContextStore;
import jpap.dev.io_api.infrastructure.ai.hitl.AprobacionHumanaService;
import jpap.dev.io_api.infrastructure.ai.hitl.MetodoResolucion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adaptador @Tool de los métodos de transporte. Con Human-in-the-Loop:
 * crea una solicitud de aprobación en lugar de resolver — el solver corre solo
 * tras el clic humano.
 *
 * El submétodo concreto (Esquina Noroeste, Costo Mínimo, Vogel o MODI) viaja
 * dentro del ModeloTransporte; para el chat siempre se usa MetodoResolucion.TRANSPORTE.
 */
@Slf4j
@Component
public class TransporteTool {

    private final AprobacionHumanaService aprobacionService;
    private final ChatContextStore contextStore;

    public TransporteTool(AprobacionHumanaService aprobacionService, ChatContextStore contextStore) {
        this.aprobacionService = aprobacionService;
        this.contextStore = contextStore;
    }

    /**
     * Una fila de la matriz de costos (un origen hacia cada destino).
     * Se envuelve la lista en un record porque LangChain4j 1.13.0 no genera el esquema
     * JSON de un parámetro con genéricos anidados (List&lt;List&lt;Double&gt;&gt;).
     */
    public record FilaCostos(
            @Description("Costos unitarios de este origen hacia cada destino, en el orden de 'destinos'")
            List<Double> costos
    ) {}

    @Tool("""
            Solicita resolver un PROBLEMA DE TRANSPORTE (distribuir unidades desde orígenes
            con cierta oferta hacia destinos con cierta demanda, minimizando el costo total).
            INVOCA ESTA HERRAMIENTA cuando se cumplan TODAS estas condiciones:
              1) El problema tenga orígenes con oferta, destinos con demanda y una matriz de
                 costos unitarios origen→destino.
              2) El modelo esté completamente validado por el estudiante.
              3) El estudiante haya pedido resolver explícitamente.
            ELECCIÓN DEL MÉTODO (parámetro 'metodo'):
              - MODI → cuando el estudiante quiere la solución ÓPTIMA (por defecto). MODI compara
                las tres soluciones iniciales (Esquina Noroeste, Costo Mínimo, Vogel), arranca de
                la más barata y optimiza hasta el óptimo.
              - ESQUINA_NOROESTE, COSTO_MINIMO o VOGEL → solo si el estudiante pide ver esa
                solución básica inicial concreta (no necesariamente óptima).
            EFECTO: NO resuelve inmediatamente — envía una solicitud de aprobación a la interfaz;
            el estudiante debe confirmar con el botón Aprobar antes de que el solver se ejecute.
            NOTA: si Σoferta ≠ Σdemanda el sistema balancea automáticamente con un origen/destino
            ficticio de costo 0; no hace falta que lo balancees tú.
            """)
    public String resolverTransporte(
            @P("Nombres de los orígenes (filas), ej: [\"Planta A\", \"Planta B\"]")
            List<String> origenes,

            @P("Nombres de los destinos (columnas), ej: [\"Ciudad 1\", \"Ciudad 2\"]")
            List<String> destinos,

            @P("Oferta de cada origen, en el mismo orden que 'origenes'")
            List<Double> oferta,

            @P("Demanda de cada destino, en el mismo orden que 'destinos'")
            List<Double> demanda,

            @P("Matriz de costos unitarios: una entrada por origen (en el orden de 'origenes'), cada una con la lista de costos hacia cada destino")
            List<FilaCostos> costos,

            @P("Método: MODI (óptimo, por defecto), ESQUINA_NOROESTE, COSTO_MINIMO o VOGEL")
            MetodoTransporte metodo
    ) {
        MetodoTransporte metodoEfectivo = metodo != null ? metodo : MetodoTransporte.MODI;
        log.info("[TOOL] resolverTransporte — solicitud HITL, origenes={}, destinos={}, metodo={}",
                origenes.size(), destinos.size(), metodoEfectivo);

        List<List<Double>> matrizCostos = costos.stream().map(FilaCostos::costos).toList();
        ModeloTransporte modelo = new ModeloTransporte(
                origenes, destinos, oferta, demanda, matrizCostos, metodoEfectivo);

        return SolicitudAprobacionHelper.solicitar(
                aprobacionService, contextStore, modelo, MetodoResolucion.TRANSPORTE);
    }
}
