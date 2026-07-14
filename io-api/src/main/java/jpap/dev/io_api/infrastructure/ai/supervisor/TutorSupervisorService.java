package jpap.dev.io_api.infrastructure.ai.supervisor;

import jpap.dev.io_api.infrastructure.ai.actividad.ActividadRegistry;
import jpap.dev.io_api.infrastructure.ai.actividad.FaseActividad;
import jpap.dev.io_api.infrastructure.ai.subagents.*;
import jpap.dev.io_api.infrastructure.persistence.entity.SesionEntity;
import jpap.dev.io_api.infrastructure.persistence.repository.SesionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.UUID;

/**
 * Supervisor / Orquestador principal de la arquitectura Multi-Agente.
 * Clasifica la solicitud del estudiante y enruta dinámicamente al subagente
 * especializado (PL, Inventario, Transporte, Redes, Entera o Dinámica).
 *
 * El módulo activo de cada sesión se persiste en sesion.modulo_activo, de modo que
 * un reinicio del backend no hace que la conversación vuelva a empezar en PL.
 */
@Slf4j
@Service
public class TutorSupervisorService {

    public enum ModuloIO {
        PL,
        INVENTARIO,
        TRANSPORTE,
        REDES,
        ENTERA,
        DINAMICA,
        GENERAL
    }

    private final PlSubAgent plSubAgent;
    private final InventarioSubAgent inventarioSubAgent;
    private final TransporteSubAgent transporteSubAgent;
    private final RedesSubAgent redesSubAgent;
    private final EnteraSubAgent enteraSubAgent;
    private final DinamicaSubAgent dinamicaSubAgent;
    private final GeneralSubAgent generalSubAgent;
    private final ModuloClassifierService moduloClassifierService;
    private final SesionRepository sesionRepository;
    private final ActividadRegistry actividadRegistry;

    public TutorSupervisorService(PlSubAgent plSubAgent,
                                  InventarioSubAgent inventarioSubAgent,
                                  TransporteSubAgent transporteSubAgent,
                                  RedesSubAgent redesSubAgent,
                                  EnteraSubAgent enteraSubAgent,
                                  DinamicaSubAgent dinamicaSubAgent,
                                  GeneralSubAgent generalSubAgent,
                                  ModuloClassifierService moduloClassifierService,
                                  SesionRepository sesionRepository,
                                  ActividadRegistry actividadRegistry) {
        this.plSubAgent = plSubAgent;
        this.inventarioSubAgent = inventarioSubAgent;
        this.transporteSubAgent = transporteSubAgent;
        this.redesSubAgent = redesSubAgent;
        this.enteraSubAgent = enteraSubAgent;
        this.dinamicaSubAgent = dinamicaSubAgent;
        this.generalSubAgent = generalSubAgent;
        this.moduloClassifierService = moduloClassifierService;
        this.sesionRepository = sesionRepository;
        this.actividadRegistry = actividadRegistry;
    }

    /** Nombre del módulo tal y como se le anuncia al estudiante mientras Pivot trabaja. */
    private static String etiqueta(ModuloIO modulo) {
        return switch (modulo) {
            case PL -> "Programación Lineal";
            case INVENTARIO -> "Inventarios";
            case TRANSPORTE -> "Transporte";
            case REDES -> "Redes";
            case ENTERA -> "PL Entera";
            case DINAMICA -> "Programación Dinámica";
            case GENERAL -> "Conversación General";
        };
    }

    public String chat(String sesionId, String mensaje) {
        ModuloIO modulo = determinarModulo(sesionId, mensaje);
        log.info("[SUPERVISOR] Sesión={} -> Enrutando al subagente: {}", sesionId, modulo);
        actividadRegistry.publicar(sesionId, FaseActividad.ENRUTANDO, etiqueta(modulo));

        return switch (modulo) {
            case INVENTARIO -> inventarioSubAgent.chat(sesionId, mensaje);
            case TRANSPORTE -> transporteSubAgent.chat(sesionId, mensaje);
            case REDES -> redesSubAgent.chat(sesionId, mensaje);
            case ENTERA -> enteraSubAgent.chat(sesionId, mensaje);
            case DINAMICA -> dinamicaSubAgent.chat(sesionId, mensaje);
            case PL -> plSubAgent.chat(sesionId, mensaje);
            case GENERAL -> generalSubAgent.chat(sesionId, mensaje);
        };
    }

    /** Módulo que atiende la sesión ahora mismo; PL si aún no se ha clasificado. */
    public ModuloIO moduloDeSesion(String sesionId) {
        return sesionRepository.findById(UUID.fromString(sesionId))
                .map(SesionEntity::getModuloActivo)
                .map(ModuloIO::valueOf)
                .orElse(ModuloIO.PL);
    }

    private ModuloIO determinarModulo(String sesionId, String mensaje) {
        String texto = normalizar(mensaje);

        // Si es un mensaje del sistema [SISTEMA], mantener el módulo de la sesión
        if (texto.contains("[sistema]")) {
            return moduloDeSesion(sesionId);
        }

        ModuloIO detectadoPorPalabras = clasificarPorPalabrasClave(texto);
        if (detectadoPorPalabras != null) {
            guardarModulo(sesionId, detectadoPorPalabras);
            return detectadoPorPalabras;
        }

        // Clasificación inteligente con LLM si no hubo coincidencias exactas por palabra clave
        try {
            ModuloClassifierService.ModuloDetectado clasificado = moduloClassifierService.clasificar(mensaje);
            log.info("[SUPERVISOR] Clasificación LLM del mensaje -> {}", clasificado);
            if (clasificado != null && clasificado != ModuloClassifierService.ModuloDetectado.CONTINUAR) {
                ModuloIO moduloIO = ModuloIO.valueOf(clasificado.name());
                guardarModulo(sesionId, moduloIO);
                return moduloIO;
            }
        } catch (Exception e) {
            log.warn("[SUPERVISOR] Fallo temporal en clasificador LLM, usando fallback: {}", e.getMessage());
        }

        // Si no se detecta módulo nuevo o es CONTINUAR, seguir en el módulo de la sesión actual
        return moduloDeSesion(sesionId);
    }

    /** La fila de sesión la crea ChatHistorialService.asegurarSesion antes del primer turno. */
    private void guardarModulo(String sesionId, ModuloIO modulo) {
        sesionRepository.findById(UUID.fromString(sesionId)).ifPresent(sesion -> {
            sesion.setModuloActivo(modulo.name());
            sesionRepository.save(sesion);
        });
    }

    private ModuloIO clasificarPorPalabrasClave(String texto) {
        // Inventarios
        if (texto.contains("inventario") || texto.contains("eoq") || texto.contains("pedidos")
                || texto.contains("lote económico") || texto.contains("costo de ordenar")
                || texto.contains("costo de mantener") || texto.contains("almacenar")
                || texto.contains("almacenamiento") || texto.contains("punto de reorden")
                || texto.contains("sacos") || (texto.contains("latas") && texto.contains("pedido"))
                || texto.contains("tramos") || (texto.contains("demanda") && texto.contains("pedido"))) {
            return ModuloIO.INVENTARIO;
        }

        // Transporte
        if (texto.contains("esquina noroeste") || texto.contains("costo minimo") || texto.contains("vogel")
                || texto.contains("modi") || (texto.contains("origen") && texto.contains("destino") && texto.contains("oferta"))) {
            return ModuloIO.TRANSPORTE;
        }

        // Redes
        if (texto.contains("dijkstra") || texto.contains("kruskal") || texto.contains("edmonds")
                || texto.contains("grafo") || (texto.contains("nodos") && texto.contains("arcos"))
                || texto.contains("ruta mas corta") || texto.contains("arbol de expansion")
                || texto.contains("flujo maximo")) {
            return ModuloIO.REDES;
        }

        // Programación Dinámica
        if (texto.contains("bellman") || texto.contains("etapas") || texto.contains("recurrencia")
                || texto.contains("programacion dinamica") || texto.contains("mochila dinamica")) {
            return ModuloIO.DINAMICA;
        }

        // Programación Lineal Entera
        if (texto.contains("branch and bound") || texto.contains("ramificacion")
                || texto.contains("variables enteras") || texto.contains("variables binarias")) {
            return ModuloIO.ENTERA;
        }

        // Programación Lineal Continua (Simplex, Gran M, Dos Fases, Gráfico).
        // El análisis post-óptimo solo existe en LP: quien pregunta por él ya está en PL,
        // y dejar que el clasificador LLM lo mande a otro módulo perdería el resultado.
        if (texto.contains("simplex") || texto.contains("gran m") || texto.contains("dos fases")
                || texto.contains("metodo grafico") || texto.contains("maximizar") || texto.contains("minimizar")
                || texto.contains("funcion objetivo") || texto.contains("restricciones")
                || texto.contains("sensibilidad") || texto.contains("precio sombra")
                || texto.contains("precios sombra") || texto.contains("holgura")) {
            return ModuloIO.PL;
        }

        return null;
    }

    private String normalizar(String input) {
        if (input == null) return "";
        String clean = Normalizer.normalize(input.toLowerCase(), Normalizer.Form.NFD);
        return clean.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
