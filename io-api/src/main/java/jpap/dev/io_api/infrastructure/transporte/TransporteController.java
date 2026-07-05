package jpap.dev.io_api.infrastructure.transporte;

import jpap.dev.io_api.application.transporte.TransporteUseCase;
import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.transporte.MetodoTransporte;
import jpap.dev.io_api.domain.transporte.ModeloTransporte;
import jpap.dev.io_api.domain.transporte.SolucionTransporte;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de resolución de problemas de transporte.
 *
 * Cada endpoint fuerza su método; el cuerpo es un {@link ModeloTransporte} con
 * oferta, demanda y matriz de costos. Devuelve el envoltorio estándar
 * {@code SolveResult<SolucionTransporte>} (status + solution + steps).
 *
 * Ejemplo de cuerpo:
 * <pre>{@code
 * {
 *   "origenes": ["O1","O2","O3"],
 *   "destinos": ["D1","D2","D3","D4"],
 *   "oferta":   [70, 90, 115],
 *   "demanda":  [50, 60, 70, 95],
 *   "costos": [[19,30,50,10],[70,30,40,60],[40,8,70,20]]
 * }
 * }</pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/transporte")
public class TransporteController {

    private final TransporteUseCase transporteUseCase;

    public TransporteController(TransporteUseCase transporteUseCase) {
        this.transporteUseCase = transporteUseCase;
    }

    /** POST /api/v1/transporte/esquina-noroeste — solución básica inicial (esquina noroeste). */
    @PostMapping("/esquina-noroeste")
    public ResponseEntity<SolveResult<SolucionTransporte>> esquinaNoroeste(@RequestBody ModeloTransporte modelo) {
        return resolver(modelo, MetodoTransporte.ESQUINA_NOROESTE);
    }

    /** POST /api/v1/transporte/costo-minimo — solución básica inicial (costo mínimo). */
    @PostMapping("/costo-minimo")
    public ResponseEntity<SolveResult<SolucionTransporte>> costoMinimo(@RequestBody ModeloTransporte modelo) {
        return resolver(modelo, MetodoTransporte.COSTO_MINIMO);
    }

    /** POST /api/v1/transporte/vogel — solución básica inicial (aproximación de Vogel). */
    @PostMapping("/vogel")
    public ResponseEntity<SolveResult<SolucionTransporte>> vogel(@RequestBody ModeloTransporte modelo) {
        return resolver(modelo, MetodoTransporte.VOGEL);
    }

    /** POST /api/v1/transporte/modi — óptimo por MODI (compara los 3 iniciales y optimiza). */
    @PostMapping("/modi")
    public ResponseEntity<SolveResult<SolucionTransporte>> modi(@RequestBody ModeloTransporte modelo) {
        return resolver(modelo, MetodoTransporte.MODI);
    }

    private ResponseEntity<SolveResult<SolucionTransporte>> resolver(ModeloTransporte modelo, MetodoTransporte metodo) {
        int m = modelo.origenes() != null ? modelo.origenes().size() : 0;
        int n = modelo.destinos() != null ? modelo.destinos().size() : 0;
        log.info("[transporte/{}] origenes={}, destinos={}", metodo, m, n);

        SolveResult<SolucionTransporte> resultado = transporteUseCase.resolver(modelo.conMetodo(metodo));

        String costo = resultado.solution() != null
                ? String.valueOf(resultado.solution().costoTotal()) : "N/A";
        log.info("[transporte/{}] status={}, costoTotal={}, pasos={}",
                metodo, resultado.status(), costo, resultado.steps().size());

        return ResponseEntity.ok(resultado);
    }
}
