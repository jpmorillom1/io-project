package jpap.dev.io_api.infrastructure.dinamica;

import jpap.dev.io_api.application.dinamica.DinamicaUseCase;
import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.dinamica.MetodoDinamico;
import jpap.dev.io_api.domain.dinamica.ModeloDinamico;
import jpap.dev.io_api.domain.dinamica.SolucionDinamica;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de los modelos de Programación Dinámica determinística.
 *
 * Cada endpoint fuerza su método; el cuerpo es un {@link ModeloDinamico}. Devuelve el
 * envoltorio estándar {@code SolveResult<SolucionDinamica>} (status + solution + steps),
 * donde la solución trae las tablas de cada etapa y la política óptima.
 *
 * Ejemplo de cuerpo (mochila 0/1):
 * <pre>{@code
 * {
 *   "capacidad": 5,
 *   "articulos": [
 *     { "nombre": "A", "peso": 2, "valor": 3 },
 *     { "nombre": "B", "peso": 3, "valor": 4 }
 *   ]
 * }
 * }</pre>
 *
 * Ejemplo de cuerpo (ruta por etapas):
 * <pre>{@code
 * {
 *   "etapasRuta": [
 *     { "etapa": 1, "nodos": ["A"] },
 *     { "etapa": 2, "nodos": ["B", "C"] },
 *     { "etapa": 3, "nodos": ["D"] }
 *   ],
 *   "arcos": [
 *     { "origen": "A", "destino": "B", "costo": 2 },
 *     { "origen": "A", "destino": "C", "costo": 4 },
 *     { "origen": "B", "destino": "D", "costo": 7 },
 *     { "origen": "C", "destino": "D", "costo": 3 }
 *   ]
 * }
 * }</pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dinamica")
public class DinamicaController {

    private final DinamicaUseCase dinamicaUseCase;

    public DinamicaController(DinamicaUseCase dinamicaUseCase) {
        this.dinamicaUseCase = dinamicaUseCase;
    }

    /** POST /api/v1/dinamica/asignacion-recursos — reparto de un recurso entre actividades o periodos. */
    @PostMapping("/asignacion-recursos")
    public ResponseEntity<SolveResult<SolucionDinamica>> asignacionRecursos(@RequestBody ModeloDinamico modelo) {
        return resolver(modelo, MetodoDinamico.ASIGNACION_RECURSOS);
    }

    /** POST /api/v1/dinamica/mochila — selección de artículos, proyectos o inversiones. */
    @PostMapping("/mochila")
    public ResponseEntity<SolveResult<SolucionDinamica>> mochila(@RequestBody ModeloDinamico modelo) {
        return resolver(modelo, MetodoDinamico.MOCHILA);
    }

    /** POST /api/v1/dinamica/ruta-etapas — ruta secuencial sobre una red por etapas. */
    @PostMapping("/ruta-etapas")
    public ResponseEntity<SolveResult<SolucionDinamica>> rutaEtapas(@RequestBody ModeloDinamico modelo) {
        return resolver(modelo, MetodoDinamico.RUTA_ETAPAS);
    }

    /** POST /api/v1/dinamica/planificacion-produccion — producción e inventarios por etapas. */
    @PostMapping("/planificacion-produccion")
    public ResponseEntity<SolveResult<SolucionDinamica>> planificacionProduccion(@RequestBody ModeloDinamico modelo) {
        return resolver(modelo, MetodoDinamico.PLANIFICACION_PRODUCCION);
    }

    /** POST /api/v1/dinamica/reemplazo-equipos — conservar o reemplazar un equipo cada año. */
    @PostMapping("/reemplazo-equipos")
    public ResponseEntity<SolveResult<SolucionDinamica>> reemplazoEquipos(@RequestBody ModeloDinamico modelo) {
        return resolver(modelo, MetodoDinamico.REEMPLAZO_EQUIPOS);
    }

    private ResponseEntity<SolveResult<SolucionDinamica>> resolver(ModeloDinamico modelo,
                                                                   MetodoDinamico metodo) {
        log.info("[dinamica/{}] sentido={}", metodo, modelo.sentidoOrDefault());

        SolveResult<SolucionDinamica> resultado = dinamicaUseCase.resolver(modelo.conMetodo(metodo));

        String z = resultado.solution() != null
                ? String.valueOf(resultado.solution().valorOptimo()) : "N/A";
        log.info("[dinamica/{}] status={}, valorOptimo={}, pasos={}",
                metodo, resultado.status(), z, resultado.steps().size());

        return ResponseEntity.ok(resultado);
    }
}
