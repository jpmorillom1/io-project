package jpap.dev.io_api.infrastructure.ai;

import jpap.dev.io_api.infrastructure.persistence.repository.SesionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pone nombre a cada conversación de la barra lateral.
 *
 * Dos tiempos:
 *   1. Al crear la sesión se guarda un título provisional (recorte del enunciado), de forma
 *      síncrona: la lista nunca muestra una fila sin nombre.
 *   2. En background, un modelo pequeño redacta el definitivo y lo reemplaza.
 *
 * Titular jamás puede añadir latencia al turno del chat ni tumbarlo si el LLM falla.
 */
@Slf4j
@Service
public class TituloSesionService {

    /** Lo que cabe en la barra lateral sin truncarse. La columna admite hasta 120. */
    private static final int MAX_LARGO = 60;

    private static final String SIN_TITULO = "Nueva conversación";

    /** El LLM adorna: entrecomilla, antepone "Título:" y cierra con punto. */
    private static final String ADORNOS_INICIALES = "^[\"'«»\\s]+|^(?i:t[íi]tulo\\s*:)\\s*";
    private static final String ADORNOS_FINALES = "[\"'«».\\s]+$";

    private final TituladorAiService titulador;
    private final SesionRepository sesionRepository;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public TituloSesionService(TituladorAiService titulador, SesionRepository sesionRepository) {
        this.titulador = titulador;
        this.sesionRepository = sesionRepository;
    }

    /** Título de arranque: el enunciado recortado. Se usa hasta que el LLM entrega el suyo. */
    public static String provisional(String enunciado) {
        if (enunciado == null || enunciado.isBlank()) return SIN_TITULO;
        return recortar(enunciado.strip().replaceAll("\\s+", " "));
    }

    /**
     * Reemplaza el título provisional por el que redacta el modelo pequeño.
     *
     * Debe llamarse DESPUÉS de que la transacción que crea la sesión haya comitado: el hilo
     * de fondo hace su propio UPDATE y no vería una fila aún no visible.
     */
    public void generarEnBackground(UUID sesionId, String enunciado) {
        executor.execute(() -> {
            try {
                String titulo = limpiar(titulador.titular(enunciado));
                if (titulo.isBlank()) return;
                sesionRepository.actualizarTitulo(sesionId, titulo);
                log.info("[TITULO] sesión {} titulada: \"{}\"", sesionId, titulo);
            } catch (Exception e) {
                log.warn("[TITULO] no se pudo titular la sesión {} ({}) — queda el provisional",
                        sesionId, e.getMessage());
            }
        });
    }

    private String limpiar(String crudo) {
        if (crudo == null) return "";
        String titulo = crudo.strip().lines().findFirst().orElse("")
                .replaceAll(ADORNOS_INICIALES, "")
                .replaceAll(ADORNOS_FINALES, "");
        return recortar(titulo);
    }

    private static String recortar(String texto) {
        if (texto.length() <= MAX_LARGO) return texto;
        return texto.substring(0, MAX_LARGO - 1).stripTrailing() + "…";
    }
}
