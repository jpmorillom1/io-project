package jpap.dev.io_api.infrastructure.ai.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.output.structured.Description;
import jpap.dev.io_api.domain.dinamica.ActividadRecurso;
import jpap.dev.io_api.domain.dinamica.ArcoRuta;
import jpap.dev.io_api.domain.dinamica.ArticuloMochila;
import jpap.dev.io_api.domain.dinamica.DatosEdadEquipo;
import jpap.dev.io_api.domain.dinamica.EtapaRuta;
import jpap.dev.io_api.domain.dinamica.MetodoDinamico;
import jpap.dev.io_api.domain.dinamica.ModeloDinamico;
import jpap.dev.io_api.domain.dinamica.SentidoOptimizacion;
import jpap.dev.io_api.infrastructure.ai.ChatContextStore;
import jpap.dev.io_api.infrastructure.ai.hitl.AprobacionHumanaService;
import jpap.dev.io_api.infrastructure.ai.hitl.MetodoResolucion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adaptador @Tool de los modelos de Programación Dinámica determinística. Con Human-in-the-Loop:
 * crea una solicitud de aprobación en lugar de resolver — el solver corre solo tras el clic humano.
 *
 * Una tool por submodelo, porque los cinco tienen datos de entrada muy distintos y un esquema
 * único con todo opcional confundiría al LLM. El submodelo concreto viaja dentro del
 * ModeloDinamico; para el chat siempre se usa MetodoResolucion.PROGRAMACION_DINAMICA.
 *
 * Los records anidados llevan @JsonIgnoreProperties(ignoreUnknown = true) porque el LLM a veces
 * inventa campos extra, y los componentes opcionales @JsonProperty(required = false) para que no
 * entren en el bloque "required" del esquema JSON.
 */
@Slf4j
@Component
public class DinamicaTool {

    private final AprobacionHumanaService aprobacionService;
    private final ChatContextStore contextStore;

    public DinamicaTool(AprobacionHumanaService aprobacionService, ChatContextStore contextStore) {
        this.aprobacionService = aprobacionService;
        this.contextStore = contextStore;
    }

    // ─── records de entrada (planos, sin genéricos anidados como parámetro) ──────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActividadInput(
            @Description("Nombre de la actividad, periodo o rubro que recibe recurso")
            String nombre,
            @Description("Retorno de asignarle 0, 1, 2, ... unidades del recurso, en ese orden. "
                    + "Debe tener exactamente recursoTotal + 1 valores; el primero es el retorno de no asignarle nada")
            List<Double> retornos
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArticuloInput(
            @Description("Nombre del artículo, proyecto o inversión")
            String nombre,
            @Description("Unidades de recurso (peso, dinero, horas) que consume una unidad de este artículo")
            int peso,
            @Description("Beneficio que aporta una unidad de este artículo")
            double valor,
            @Description("Máximo de unidades que se pueden llevar de este artículo. OMITE este campo para el "
                    + "caso clásico 0/1 (llevarlo o no); nunca lo envíes como null")
            @JsonProperty(required = false)
            Integer unidadesMaximas
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EtapaInput(
            @Description("Número de etapa, empezando en 1. La etapa 1 debe tener un único nodo: el origen")
            int etapa,
            @Description("Nombres de los nodos disponibles en esta etapa")
            List<String> nodos
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArcoInput(
            @Description("Nodo de partida, perteneciente a la etapa k")
            String origen,
            @Description("Nodo de llegada, perteneciente a la etapa k+1")
            String destino,
            @Description("Costo, distancia o beneficio de recorrer este arco")
            double costo
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EdadInput(
            @Description("Edad del equipo en años; 0 = equipo nuevo. Debe haber una fila por cada edad de 0 a edadMaxima")
            int edad,
            @Description("Ingreso anual que genera el equipo a esa edad")
            double ingreso,
            @Description("Costo anual de operar y mantener el equipo a esa edad")
            double costoOperacion,
            @Description("Valor de reventa del equipo a esa edad")
            double valorRescate
    ) {}

    // ─── tools ──────────────────────────────────────────────────────────────────

    @Tool("""
            Solicita resolver por PROGRAMACIÓN DINÁMICA un problema de ASIGNACIÓN DE UN RECURSO
            entre varias actividades, periodos o rubros. Sirve para repartir un presupuesto, distribuir
            personal o máquinas entre plantas, o asignar recursos a lo largo de varios periodos.
            INVOCA ESTA HERRAMIENTA cuando se cumplan TODAS estas condiciones:
              1) Haya una cantidad ENTERA y limitada de un recurso que repartir.
              2) Para cada actividad se conozca una TABLA de retorno según cuántas unidades recibe
                 (no una fórmula lineal: si el retorno fuera lineal bastaría programación lineal).
              3) El modelo esté completamente validado por el estudiante.
              4) El estudiante haya pedido resolver explícitamente.
            IMPORTANTE: la lista 'retornos' de cada actividad debe tener exactamente recursoTotal + 1
            valores (el retorno de asignarle 0, 1, ..., recursoTotal unidades).
            EFECTO: NO resuelve inmediatamente — envía una solicitud de aprobación a la interfaz;
            el estudiante debe confirmar con el botón Aprobar antes de que el solver se ejecute.
            """)
    public String resolverPdAsignacionRecursos(
            @P("Unidades enteras del recurso disponibles para repartir")
            int recursoTotal,

            @P("Una entrada por actividad, cada una con su nombre y su tabla de retornos")
            List<ActividadInput> actividades,

            @P(value = "MAXIMIZAR si los valores son beneficios (lo habitual), MINIMIZAR si son costos. "
                    + "OMITE este campo para usar MAXIMIZAR (nunca null)", required = false)
            SentidoOptimizacion sentido
    ) {
        log.info("[TOOL] resolverPdAsignacionRecursos — solicitud HITL, recurso={}, actividades={}",
                recursoTotal, actividades != null ? actividades.size() : 0);

        List<ActividadRecurso> lista = actividades == null ? List.of() : actividades.stream()
                .map(a -> new ActividadRecurso(a.nombre(), a.retornos()))
                .toList();

        ModeloDinamico modelo = ModeloDinamico.builder()
                .metodo(MetodoDinamico.ASIGNACION_RECURSOS)
                .sentido(sentido)
                .recursoTotal(recursoTotal)
                .actividades(lista)
                .build();

        return solicitar(modelo);
    }

    @Tool("""
            Solicita resolver por PROGRAMACIÓN DINÁMICA un problema de MOCHILA: elegir qué artículos,
            proyectos o inversiones llevar cuando cada uno consume parte de un recurso limitado
            (peso, presupuesto, horas) y aporta un beneficio, maximizando el beneficio total.
            INVOCA ESTA HERRAMIENTA cuando se cumplan TODAS estas condiciones:
              1) Haya una CAPACIDAD entera limitada y una lista de candidatos, cada uno con un consumo
                 entero y un beneficio.
              2) La decisión sea cuántas unidades de cada candidato llevar (o si llevarlo o no).
              3) El modelo esté completamente validado por el estudiante.
              4) El estudiante haya pedido resolver explícitamente.
            OMITE 'unidadesMaximas' de un artículo para el caso clásico 0/1 (se lleva o no se lleva).
            Este modelo SIEMPRE maximiza.
            EFECTO: NO resuelve inmediatamente — envía una solicitud de aprobación a la interfaz;
            el estudiante debe confirmar con el botón Aprobar antes de que el solver se ejecute.
            """)
    public String resolverPdMochila(
            @P("Capacidad entera disponible (peso máximo, presupuesto, horas)")
            int capacidad,

            @P("Un artículo por candidato, cada uno con nombre, peso, valor y opcionalmente unidadesMaximas")
            List<ArticuloInput> articulos
    ) {
        log.info("[TOOL] resolverPdMochila — solicitud HITL, capacidad={}, articulos={}",
                capacidad, articulos != null ? articulos.size() : 0);

        List<ArticuloMochila> lista = articulos == null ? List.of() : articulos.stream()
                .map(a -> new ArticuloMochila(a.nombre(), a.peso(), a.valor(), a.unidadesMaximas()))
                .toList();

        ModeloDinamico modelo = ModeloDinamico.builder()
                .metodo(MetodoDinamico.MOCHILA)
                .capacidad(capacidad)
                .articulos(lista)
                .build();

        return solicitar(modelo);
    }

    @Tool("""
            Solicita resolver por PROGRAMACIÓN DINÁMICA un problema de RUTA SECUENCIAL sobre una red
            por ETAPAS (el clásico problema de la diligencia): hay que ir de un origen a un destino
            atravesando una columna de nodos por etapa, minimizando (o maximizando) el total acumulado.
            INVOCA ESTA HERRAMIENTA cuando se cumplan TODAS estas condiciones:
              1) Los nodos estén organizados en ETAPAS y cada arco avance exactamente una etapa.
              2) Se busque la mejor secuencia de decisiones de origen a destino.
              3) El modelo esté completamente validado por el estudiante.
              4) El estudiante haya pedido resolver explícitamente.
            Si el grafo NO está organizado en etapas (los arcos saltan libremente entre nodos) usa la
            herramienta de Redes con Dijkstra, NO esta.
            La etapa 1 debe tener exactamente un nodo: el origen. Los nombres de nodo son únicos.
            EFECTO: NO resuelve inmediatamente — envía una solicitud de aprobación a la interfaz;
            el estudiante debe confirmar con el botón Aprobar antes de que el solver se ejecute.
            """)
    public String resolverPdRutaEtapas(
            @P("Las etapas de la red, numeradas desde 1; la etapa 1 lleva solo el nodo origen")
            List<EtapaInput> etapas,

            @P("Los arcos de la red; cada uno va de un nodo de la etapa k a uno de la etapa k+1")
            List<ArcoInput> arcos,

            @P(value = "MINIMIZAR si los costos son distancias o costos (lo habitual), MAXIMIZAR si son "
                    + "beneficios. OMITE este campo para usar MINIMIZAR (nunca null)", required = false)
            SentidoOptimizacion sentido
    ) {
        log.info("[TOOL] resolverPdRutaEtapas — solicitud HITL, etapas={}, arcos={}",
                etapas != null ? etapas.size() : 0, arcos != null ? arcos.size() : 0);

        List<EtapaRuta> listaEtapas = etapas == null ? List.of() : etapas.stream()
                .map(e -> new EtapaRuta(e.etapa(), e.nodos()))
                .toList();
        List<ArcoRuta> listaArcos = arcos == null ? List.of() : arcos.stream()
                .map(a -> new ArcoRuta(a.origen(), a.destino(), a.costo()))
                .toList();

        ModeloDinamico modelo = ModeloDinamico.builder()
                .metodo(MetodoDinamico.RUTA_ETAPAS)
                .sentido(sentido)
                .etapasRuta(listaEtapas)
                .arcos(listaArcos)
                .build();

        return solicitar(modelo);
    }

    @Tool("""
            Solicita resolver por PROGRAMACIÓN DINÁMICA un problema de PLANIFICACIÓN DE PRODUCCIÓN / INVENTARIO POR PERÍODOS:
            decidir cuánto producir en cada periodo para cubrir una demanda dinámica conocida al mínimo costo total.
            USA SIEMPRE ESTA HERRAMIENTA cuando:
              1) El problema proporcione una LISTA o secuencia de demandas por periodo/mes (ej. [3, 2, 4]).
              2) Se mencionen costos de preparación de lote, producción y mantenimiento de inventario por periodo.
              3) El enunciado solicite resolver por Programación Dinámica.
            IMPORTANTE: JAMÁS llames a resolverInventario cuando la demanda sea una lista por periodos; resolverInventario es solo para demanda escalar constante.
            Incluye SOLO los parámetros opcionales que el enunciado menciona y OMITE los demás.
            EFECTO: NO resuelve inmediatamente — envía una solicitud de aprobación a la interfaz;
            el estudiante debe confirmar con el botón Aprobar antes de que el solver se ejecute.
            """)
    public String resolverPdPlanificacionProduccion(
            @P("Lista de demandas para cada periodo/mes en orden, ej: [3, 2, 4]")
            List<Integer> demandas,

            @P("Costo fijo de preparar un lote (K); se paga una vez por periodo en que se produce algo")
            double costoPreparacion,

            @P("Costo de producir una unidad (c)")
            double costoUnitarioProduccion,

            @P("Costo de mantener una unidad en inventario de un periodo al siguiente (h)")
            double costoMantener,

            @P(value = "Máximo de unidades producibles en un periodo. OMITE este campo si no hay límite (nunca null)", required = false)
            Integer capacidadProduccion,

            @P(value = "Máximo de unidades almacenables entre periodos. OMITE este campo si no hay límite (nunca null)", required = false)
            Integer capacidadAlmacen,

            @P(value = "Inventario disponible al inicio del periodo 1 (default 0). OMITE este campo si es cero (nunca null)", required = false)
            Integer inventarioInicial,

            @P(value = "Inventario exigido al cerrar el último periodo (default 0). OMITE este campo si es cero (nunca null)", required = false)
            Integer inventarioFinal
    ) {
        log.info("[TOOL] resolverPdPlanificacionProduccion — solicitud HITL, periodos={}, K={}, c={}, h={}",
                demandas != null ? demandas.size() : 0, costoPreparacion, costoUnitarioProduccion, costoMantener);

        ModeloDinamico modelo = ModeloDinamico.builder()
                .metodo(MetodoDinamico.PLANIFICACION_PRODUCCION)
                .demandas(demandas)
                .costoPreparacion(costoPreparacion)
                .costoUnitarioProduccion(costoUnitarioProduccion)
                .costoMantener(costoMantener)
                .capacidadProduccion(capacidadProduccion)
                .capacidadAlmacen(capacidadAlmacen)
                .inventarioInicial(inventarioInicial)
                .inventarioFinal(inventarioFinal)
                .build();

        return solicitar(modelo);
    }

    @Tool("""
            Solicita resolver por PROGRAMACIÓN DINÁMICA un problema de REEMPLAZO DE EQUIPOS: decidir
            cada año si conservar la máquina actual un año más o venderla por su valor de rescate y
            comprar una nueva, maximizando el ingreso neto del horizonte.
            INVOCA ESTA HERRAMIENTA cuando se cumplan TODAS estas condiciones:
              1) Haya un horizonte de varios años y una TABLA con el ingreso, el costo de operación y el
                 valor de rescate del equipo SEGÚN SU EDAD.
              2) Se conozca el precio de un equipo nuevo.
              3) El modelo esté completamente validado por el estudiante.
              4) El estudiante haya pedido resolver explícitamente.
            La tabla 'tablaEdades' debe traer una fila por CADA edad de 0 a edadMaxima, sin huecos.
            Al cerrar el horizonte el equipo se vende por su valor de rescate. Este modelo SIEMPRE maximiza.
            EFECTO: NO resuelve inmediatamente — envía una solicitud de aprobación a la interfaz;
            el estudiante debe confirmar con el botón Aprobar antes de que el solver se ejecute.
            """)
    public String resolverPdReemplazoEquipos(
            @P("Número de años del horizonte de planeación")
            int horizonteAnios,

            @P("Edad máxima que puede alcanzar el equipo; a esa edad el reemplazo es obligatorio")
            int edadMaxima,

            @P("Precio de compra de un equipo nuevo")
            double costoCompra,

            @P("Una fila por cada edad de 0 a edadMaxima, con ingreso, costoOperacion y valorRescate")
            List<EdadInput> tablaEdades,

            @P(value = "Edad del equipo al inicio del año 1 (default 0 = equipo nuevo). OMITE este campo si arranca nuevo (nunca null)", required = false)
            Integer edadInicial
    ) {
        log.info("[TOOL] resolverPdReemplazoEquipos — solicitud HITL, horizonte={}, edadMaxima={}, I={}",
                horizonteAnios, edadMaxima, costoCompra);

        List<DatosEdadEquipo> lista = tablaEdades == null ? List.of() : tablaEdades.stream()
                .map(e -> new DatosEdadEquipo(e.edad(), e.ingreso(), e.costoOperacion(), e.valorRescate()))
                .toList();

        ModeloDinamico modelo = ModeloDinamico.builder()
                .metodo(MetodoDinamico.REEMPLAZO_EQUIPOS)
                .horizonteAnios(horizonteAnios)
                .edadMaxima(edadMaxima)
                .edadInicial(edadInicial)
                .costoCompra(costoCompra)
                .tablaEdades(lista)
                .build();

        return solicitar(modelo);
    }

    private String solicitar(ModeloDinamico modelo) {
        return SolicitudAprobacionHelper.solicitar(
                aprobacionService, contextStore, modelo, MetodoResolucion.PROGRAMACION_DINAMICA);
    }
}
