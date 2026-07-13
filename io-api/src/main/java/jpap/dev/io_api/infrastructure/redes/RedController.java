package jpap.dev.io_api.infrastructure.redes;

import jpap.dev.io_api.application.redes.RedUseCase;
import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.redes.MetodoRed;
import jpap.dev.io_api.domain.redes.ModeloRed;
import jpap.dev.io_api.domain.redes.SolucionRed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de resolución de problemas de redes.
 *
 * Cada endpoint fuerza su método; el cuerpo es un {@link ModeloRed}. Devuelve el
 * envoltorio estándar {@code SolveResult<SolucionRed>} (status + solution + steps).
 *
 * Ejemplo de cuerpo (Dijkstra):
 * <pre>{@code
 * {
 *   "nodos": ["A","B","C","D"],
 *   "aristas": [
 *     {"origen":"A","destino":"B","peso":4},
 *     {"origen":"A","destino":"C","peso":2},
 *     {"origen":"C","destino":"B","peso":1},
 *     {"origen":"B","destino":"D","peso":5}
 *   ],
 *   "dirigido": true,
 *   "fuente": "A",
 *   "sumidero": "D"
 * }
 * }</pre>
 *
 * Para asignación el cuerpo lleva agentes/tareas/matrizCostos en lugar de nodos/aristas:
 * <pre>{@code
 * {
 *   "agentes": ["A1","A2","A3"],
 *   "tareas":  ["T1","T2","T3"],
 *   "matrizCostos": [[9,2,7],[6,4,3],[5,8,1]]
 * }
 * }</pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/redes")
public class RedController {

    private final RedUseCase redUseCase;

    public RedController(RedUseCase redUseCase) {
        this.redUseCase = redUseCase;
    }

    /** POST /api/v1/redes/dijkstra — ruta más corta (pesos ≥ 0). */
    @PostMapping("/dijkstra")
    public ResponseEntity<SolveResult<SolucionRed>> dijkstra(@RequestBody ModeloRed modelo) {
        return resolver(modelo, MetodoRed.DIJKSTRA);
    }

    /** POST /api/v1/redes/kruskal — árbol de expansión mínima (grafo no dirigido). */
    @PostMapping("/kruskal")
    public ResponseEntity<SolveResult<SolucionRed>> kruskal(@RequestBody ModeloRed modelo) {
        return resolver(modelo, MetodoRed.KRUSKAL);
    }

    /** POST /api/v1/redes/edmonds-karp — flujo máximo fuente→sumidero. */
    @PostMapping("/edmonds-karp")
    public ResponseEntity<SolveResult<SolucionRed>> edmondsKarp(@RequestBody ModeloRed modelo) {
        return resolver(modelo, MetodoRed.EDMONDS_KARP);
    }

    /** POST /api/v1/redes/flujo-costo-minimo — flujo máximo de costo mínimo fuente→sumidero. */
    @PostMapping("/flujo-costo-minimo")
    public ResponseEntity<SolveResult<SolucionRed>> flujoCostoMinimo(@RequestBody ModeloRed modelo) {
        return resolver(modelo, MetodoRed.FLUJO_COSTO_MINIMO);
    }

    /** POST /api/v1/redes/asignacion — asignación óptima agentes→tareas (vía red MCF). */
    @PostMapping("/asignacion")
    public ResponseEntity<SolveResult<SolucionRed>> asignacion(@RequestBody ModeloRed modelo) {
        return resolver(modelo, MetodoRed.ASIGNACION);
    }

    private ResponseEntity<SolveResult<SolucionRed>> resolver(ModeloRed modelo, MetodoRed metodo) {
        int nodos = modelo.nodos() != null ? modelo.nodos().size() : 0;
        int aristas = modelo.aristas() != null ? modelo.aristas().size() : 0;
        log.info("[redes/{}] nodos={}, aristas={}", metodo, nodos, aristas);

        SolveResult<SolucionRed> resultado = redUseCase.resolver(modelo.conMetodo(metodo));

        String valor = resultado.solution() != null
                ? String.valueOf(resultado.solution().valorObjetivo()) : "N/A";
        log.info("[redes/{}] status={}, valorObjetivo={}, pasos={}",
                metodo, resultado.status(), valor, resultado.steps().size());

        return ResponseEntity.ok(resultado);
    }
}
