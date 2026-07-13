package jpap.dev.io_api.infrastructure.ai.sensibilidad;

import jpap.dev.io_api.domain.common.SolveStatus;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.SolucionLP;
import jpap.dev.io_api.infrastructure.ai.hitl.MetodoResolucion;
import jpap.dev.io_api.infrastructure.persistence.entity.ProblemaResueltoEntity;
import jpap.dev.io_api.infrastructure.persistence.repository.ProblemaResueltoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Relee el último problema resuelto de la sesión y devuelve su análisis de sensibilidad
 * ya redactado para el tutor.
 *
 * Lee de problema_resuelto en vez de depender del mensaje [SISTEMA] que se inyectó al
 * resolver: ese mensaje sale de la ventana de memoria (14 mensajes) tras unos pocos
 * intercambios, y el estudiante suele preguntar por la sensibilidad bastante después.
 *
 * No pasa por la compuerta HITL a propósito: no ejecuta ningún solver, solo lee un
 * resultado que el humano ya aprobó.
 */
@Slf4j
@Service
public class SensibilidadService {

    /** Los únicos métodos que producen análisis post-óptimo. El gráfico no lo calcula. */
    private static final Set<MetodoResolucion> TABULARES =
            EnumSet.of(MetodoResolucion.SIMPLEX, MetodoResolucion.GRAN_M, MetodoResolucion.DOS_FASES);

    private static final String SIN_RESOLUCION = """
            NO HAY DATOS DE SENSIBILIDAD: en esta sesión todavía no se ha resuelto ningún modelo de
            Programación Lineal con Simplex, Gran M o Dos Fases.
            Dilo con honestidad al estudiante y ofrécele resolver el modelo primero.
            JAMÁS inventes holguras, valores marginales ni rangos.
            """;

    private static final String SOLO_GRAFICO = """
            NO HAY DATOS DE SENSIBILIDAD: lo último que se resolvió en esta sesión fue por el MÉTODO
            GRÁFICO, que no calcula análisis post-óptimo.
            Explícaselo al estudiante y ofrécele volver a resolver el mismo modelo con Simplex o Dos
            Fases para obtenerlo. JAMÁS inventes holguras, valores marginales ni rangos.
            """;

    private static final String OTRO_MODULO = """
            NO HAY DATOS DE SENSIBILIDAD: lo último que se resolvió en esta sesión no es un modelo de
            Programación Lineal continua, y el análisis post-óptimo solo existe para esos modelos.
            Dilo con honestidad al estudiante. JAMÁS inventes holguras, valores marginales ni rangos.
            """;

    private static final String SIN_OPTIMO = """
            NO HAY DATOS DE SENSIBILIDAD: el último modelo resuelto no alcanzó un óptimo (resultó
            infactible o no acotado), así que no hay holguras ni valores marginales que interpretar.
            Dilo con honestidad al estudiante y ayúdale a revisar el modelo.
            """;

    /**
     * El estudiante PIDIÓ el análisis: la respuesta es la explicación misma, no un ofrecimiento
     * ni un repaso del modelo. Esta instrucción solo viaja por la vía de la tool — el resumen
     * post-resolución (ResolucionEjecutor) OFRECE la sensibilidad, no la suelta sin que la pidan.
     */
    private static final String INSTRUCCION_RESPUESTA = """

            RESPONDE AHORA con la explicación, en lenguaje llano y directo. Nada de preámbulos
            ("claro, veamos el análisis…"), nada de repetir el modelo, nada de tablas ni de nombres
            de variables o restricciones. Tres bloques y una pregunta de cierre:
              1. Qué recurso se agotó (tu cuello de botella) y cuál sobró, con su nombre real.
              2. Cuánto ganarías (o ahorrarías) con una unidad más de cada recurso escaso, y hasta
                 qué cantidad sigue valiendo ese beneficio.
              3. Cuánto pueden moverse las ganancias unitarias antes de que cambie el plan; y para
                 lo que hoy NO se produce, cuánto tendría que dejar para que valga la pena hacerlo.
            Cierra con UNA pregunta que obligue al estudiante a DECIDIR con estos números.
            """;

    private final ProblemaResueltoRepository problemaRepository;
    // Jackson 3 (tools.jackson): es el que escribió estas columnas JSONB en ChatHistorialService.
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public SensibilidadService(ProblemaResueltoRepository problemaRepository) {
        this.problemaRepository = problemaRepository;
    }

    /**
     * Bloque de sensibilidad del último modelo LP resuelto en la sesión, listo para que
     * el tutor lo traduzca al vocabulario del enunciado.
     *
     * Cuando no hay nada que explicar, devuelve un texto que se lo dice al LLM de forma
     * explícita: es preferible que el tutor admita que no tiene los datos a que los invente.
     */
    @Transactional(readOnly = true)
    public String explicarUltimaResolucion(String sesionId) {
        Optional<ProblemaResueltoEntity> ultimo = buscarUltimo(sesionId);
        if (ultimo.isEmpty()) return SIN_RESOLUCION;

        ProblemaResueltoEntity problema = ultimo.get();
        MetodoResolucion metodo;
        try {
            metodo = MetodoResolucion.valueOf(problema.getModulo());
        } catch (IllegalArgumentException e) {
            log.warn("[SENSIBILIDAD] módulo desconocido en problema_resuelto: {}", problema.getModulo());
            return OTRO_MODULO;
        }

        if (metodo == MetodoResolucion.GRAFICO) return SOLO_GRAFICO;
        if (!TABULARES.contains(metodo)) return OTRO_MODULO;

        ModeloLP modelo = leer(problema.getModeloJson(), ModeloLP.class);
        JsonNode resultado = leerArbol(problema.getResultado());
        if (modelo == null || resultado == null) {
            log.warn("[SENSIBILIDAD] no se pudo releer el problema resuelto de la sesión {}", sesionId);
            return SIN_RESOLUCION;
        }

        SolveStatus status = leerNodo(resultado.get("status"), SolveStatus.class);
        if (status != SolveStatus.OPTIMO && status != SolveStatus.MULTIPLE_OPTIMO) return SIN_OPTIMO;

        SolucionLP solucion = leerNodo(resultado.get("solution"), SolucionLP.class);
        String bloque = SensibilidadFormatter.formatear(solucion, modelo);
        if (bloque.isEmpty()) return SIN_RESOLUCION;

        String cabecera = "Modelo resuelto por " + nombre(metodo) + ". Valor óptimo Z* = "
                + solucion.valorOptimo() + ".\n"
                + (status == SolveStatus.MULTIPLE_OPTIMO
                        ? "Ojo: hay ÓPTIMOS MÚLTIPLES — los rangos siguen siendo válidos, pero existe otra "
                          + "solución con el mismo valor óptimo. Menciónaselo al estudiante.\n"
                        : "");

        log.info("[SENSIBILIDAD] sesión {} — se releyó el resultado de {}", sesionId, metodo);
        return cabecera + "\n" + bloque + INSTRUCCION_RESPUESTA;
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    private Optional<ProblemaResueltoEntity> buscarUltimo(String sesionId) {
        try {
            return problemaRepository.findFirstBySesionIdOrderByResueltoEnDesc(UUID.fromString(sesionId));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    private String nombre(MetodoResolucion metodo) {
        return switch (metodo) {
            case SIMPLEX -> "Simplex estándar";
            case GRAN_M -> "Gran M";
            case DOS_FASES -> "Dos Fases";
            default -> metodo.name();
        };
    }

    private <T> T leer(String json, Class<T> tipo) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, tipo);
        } catch (Exception e) {
            log.warn("[SENSIBILIDAD] JSON ilegible para {}: {}", tipo.getSimpleName(), e.getMessage());
            return null;
        }
    }

    private JsonNode leerArbol(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("[SENSIBILIDAD] JSON ilegible en problema_resuelto: {}", e.getMessage());
            return null;
        }
    }

    private <T> T leerNodo(JsonNode nodo, Class<T> tipo) {
        if (nodo == null || nodo.isNull()) return null;
        try {
            return objectMapper.treeToValue(nodo, tipo);
        } catch (Exception e) {
            log.warn("[SENSIBILIDAD] no se pudo mapear {}: {}", tipo.getSimpleName(), e.getMessage());
            return null;
        }
    }
}
