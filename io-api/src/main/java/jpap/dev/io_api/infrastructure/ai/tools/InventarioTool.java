package jpap.dev.io_api.infrastructure.ai.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.output.structured.Description;
import jpap.dev.io_api.domain.inventario.MetodoInventario;
import jpap.dev.io_api.domain.inventario.ModeloInventario;
import jpap.dev.io_api.domain.inventario.TramoDescuento;
import jpap.dev.io_api.infrastructure.ai.ChatContextStore;
import jpap.dev.io_api.infrastructure.ai.hitl.AprobacionHumanaService;
import jpap.dev.io_api.infrastructure.ai.hitl.MetodoResolucion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adaptador @Tool de los modelos deterministas de inventario. Con Human-in-the-Loop:
 * crea una solicitud de aprobación en lugar de resolver — el solver corre solo tras el
 * clic humano.
 *
 * Dos tools: resolverInventario (EOQ básico, faltantes, producción económica y punto de
 * reorden, con parámetros escalares) y resolverInventarioDescuentos (EOQ con descuentos,
 * que necesita la tabla de precios por tramo). El submodelo concreto viaja dentro del
 * ModeloInventario; para el chat siempre se usa MetodoResolucion.INVENTARIO.
 */
@Slf4j
@Component
public class InventarioTool {

    private final AprobacionHumanaService aprobacionService;
    private final ChatContextStore contextStore;

    public InventarioTool(AprobacionHumanaService aprobacionService, ChatContextStore contextStore) {
        this.aprobacionService = aprobacionService;
        this.contextStore = contextStore;
    }

    /** Un tramo de la tabla de descuentos por cantidad. Record plano para el esquema del @Tool. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TramoInput(
            @Description("Cantidad mínima de unidades por pedido a partir de la cual aplica este precio (el primer tramo suele empezar en 0)")
            double cantidadMinima,
            @Description("Precio unitario de compra cuando se pide al menos 'cantidadMinima' unidades")
            double precioUnitario
    ) {}

    @Tool("""
            Solicita resolver un MODELO DETERMINISTA DE INVENTARIO con demanda conocida:
            EOQ básico, EOQ con faltantes, producción económica (POQ/EPQ) o punto de reorden.
            Para EOQ con DESCUENTOS por cantidad usa resolverInventarioDescuentos, NO esta.
            INVOCA ESTA HERRAMIENTA cuando se cumplan TODAS estas condiciones:
              1) El problema tenga demanda conocida, un costo de ordenar/preparar y un costo de mantener,
                 y se busque cuánto pedir y cada cuánto (cantidad económica de pedido).
              2) El modelo esté completamente validado por el estudiante.
              3) El estudiante haya pedido resolver explícitamente.
            ELECCIÓN DEL MÉTODO (parámetro 'metodo'):
              - EOQ_BASICO → EOQ clásico: demanda constante, reposición instantánea, sin faltantes.
                Solo requiere demanda, costoOrden y costoMantener.
              - EOQ_FALTANTES → se permiten faltantes/pedidos pendientes (backorders). Requiere ADEMÁS
                'costoFaltante' (costo por unidad y periodo de quedar sin stock).
              - PRODUCCION_ECONOMICA → el artículo se PRODUCE a una tasa finita en vez de comprarse de
                golpe. Requiere ADEMÁS 'tasaProduccion' (P, unidades/año, debe ser mayor que la demanda).
              - PUNTO_REORDEN → además de cuánto pedir, calcula CUÁNDO pedir. Requiere ADEMÁS
                'leadTimeDias' (tiempo de entrega en días) y opcionalmente 'diasHabiles' (default 360).
            IMPORTANTE: incluye SOLO los parámetros opcionales que el método usa y OMITE los demás —
            nunca envíes un campo con valor null.
            EFECTO: NO resuelve inmediatamente — envía una solicitud de aprobación a la interfaz;
            el estudiante debe confirmar con el botón Aprobar antes de que el solver se ejecute.
            """)
    public String resolverInventario(
            @P("Modelo: EOQ_BASICO, EOQ_FALTANTES, PRODUCCION_ECONOMICA o PUNTO_REORDEN")
            MetodoInventario metodo,

            @P("Demanda conocida por periodo (normalmente anual), en unidades")
            double demanda,

            @P("Costo de ordenar o preparar un pedido (K), en dinero por pedido")
            double costoOrden,

            @P("Costo de mantener una unidad en inventario por periodo (H)")
            double costoMantener,

            @P(value = "Costo de faltante por unidad y periodo (b) — SOLO para EOQ_FALTANTES; en los demás OMITE este campo (nunca null)", required = false)
            Double costoFaltante,

            @P(value = "Tasa de producción P en unidades/año, debe ser mayor que la demanda — SOLO para PRODUCCION_ECONOMICA; en los demás OMITE este campo (nunca null)", required = false)
            Double tasaProduccion,

            @P(value = "Tiempo de entrega (lead time) en días — SOLO para PUNTO_REORDEN; en los demás OMITE este campo (nunca null)", required = false)
            Double leadTimeDias,

            @P(value = "Días hábiles al año para el punto de reorden (default 360 si se omite) — SOLO para PUNTO_REORDEN; en los demás OMITE este campo (nunca null)", required = false)
            Integer diasHabiles
    ) {
        if (metodo == null) {
            return "ERROR: falta el parámetro 'metodo'. Indica EOQ_BASICO, EOQ_FALTANTES, "
                    + "PRODUCCION_ECONOMICA o PUNTO_REORDEN (para descuentos usa resolverInventarioDescuentos).";
        }
        if (metodo == MetodoInventario.EOQ_DESCUENTOS) {
            return "ERROR: para EOQ con descuentos por cantidad usa la herramienta "
                    + "resolverInventarioDescuentos con la tabla de precios por tramo.";
        }
        log.info("[TOOL] resolverInventario — solicitud HITL, metodo={}, D={}, K={}, H={}",
                metodo, demanda, costoOrden, costoMantener);

        ModeloInventario modelo = new ModeloInventario(metodo, demanda, costoOrden, costoMantener,
                costoFaltante, tasaProduccion, leadTimeDias, diasHabiles, null, null);

        return SolicitudAprobacionHelper.solicitar(
                aprobacionService, contextStore, modelo, MetodoResolucion.INVENTARIO);
    }

    @Tool("""
            Solicita resolver un modelo EOQ CON DESCUENTOS POR CANTIDAD: el precio unitario de compra
            baja al pedir más unidades, según una tabla de tramos (cantidad mínima → precio).
            INVOCA ESTA HERRAMIENTA cuando se cumplan TODAS estas condiciones:
              1) El problema tenga demanda conocida, costo de ordenar, y una TABLA DE PRECIOS donde el
                 precio unitario depende del tamaño del pedido.
              2) El modelo esté completamente validado por el estudiante.
              3) El estudiante haya pedido resolver explícitamente.
            COSTO DE MANTENER: indica UNO de los dos:
              - 'tasaMantenerPorcentaje' (i) si el costo de mantener es una fracción del precio (ej. 0.2 = 20%),
                lo habitual cuando el enunciado dice "el costo de mantener es el 20% del precio". OMITE 'costoMantener'.
              - 'costoMantener' (H) si es un valor fijo por unidad-año independiente del precio. OMITE 'tasaMantenerPorcentaje'.
            EFECTO: NO resuelve inmediatamente — envía una solicitud de aprobación a la interfaz;
            el estudiante debe confirmar con el botón Aprobar antes de que el solver se ejecute.
            """)
    public String resolverInventarioDescuentos(
            @P("Demanda conocida por periodo (normalmente anual), en unidades")
            double demanda,

            @P("Costo de ordenar o preparar un pedido (K), en dinero por pedido")
            double costoOrden,

            @P("Tabla de descuentos: un tramo por nivel de precio, cada uno con 'cantidadMinima' y 'precioUnitario'")
            List<TramoInput> tramos,

            @P(value = "Tasa de mantener como fracción del precio (ej. 0.2 = 20% del precio). Úsala si el costo de mantener depende del precio; si no, OMÍTELA (nunca null)", required = false)
            Double tasaMantenerPorcentaje,

            @P(value = "Costo de mantener fijo por unidad-año (H), independiente del precio. Úsalo si no hay tasa porcentual; si no, OMÍTELO (nunca null)", required = false)
            Double costoMantener
    ) {
        log.info("[TOOL] resolverInventarioDescuentos — solicitud HITL, D={}, K={}, tramos={}",
                demanda, costoOrden, tramos != null ? tramos.size() : 0);

        List<TramoDescuento> lista = tramos == null ? List.of() : tramos.stream()
                .map(t -> new TramoDescuento(t.cantidadMinima(), t.precioUnitario()))
                .toList();
        ModeloInventario modelo = new ModeloInventario(MetodoInventario.EOQ_DESCUENTOS, demanda,
                costoOrden, costoMantener, null, null, null, null, tasaMantenerPorcentaje, lista);

        return SolicitudAprobacionHelper.solicitar(
                aprobacionService, contextStore, modelo, MetodoResolucion.INVENTARIO);
    }
}
