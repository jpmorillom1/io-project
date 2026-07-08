package jpap.dev.io_api.infrastructure.inventario;

import jpap.dev.io_api.application.inventario.InventarioUseCase;
import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.inventario.MetodoInventario;
import jpap.dev.io_api.domain.inventario.ModeloInventario;
import jpap.dev.io_api.domain.inventario.SolucionInventario;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de los modelos deterministas de inventario.
 *
 * Cada endpoint fuerza su método; el cuerpo es un {@link ModeloInventario}. Devuelve el
 * envoltorio estándar {@code SolveResult<SolucionInventario>} (status + solution + steps).
 *
 * Ejemplo de cuerpo (EOQ básico):
 * <pre>{@code
 * { "demanda": 1000, "costoOrden": 50, "costoMantener": 4 }
 * }</pre>
 *
 * Ejemplo con descuentos por cantidad (precio depende del tamaño del pedido):
 * <pre>{@code
 * {
 *   "demanda": 5000, "costoOrden": 49, "tasaMantenerPorcentaje": 0.2,
 *   "tramos": [
 *     { "cantidadMinima": 0,    "precioUnitario": 5.00 },
 *     { "cantidadMinima": 1000, "precioUnitario": 4.80 },
 *     { "cantidadMinima": 2500, "precioUnitario": 4.75 }
 *   ]
 * }
 * }</pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/inventario")
public class InventarioController {

    private final InventarioUseCase inventarioUseCase;

    public InventarioController(InventarioUseCase inventarioUseCase) {
        this.inventarioUseCase = inventarioUseCase;
    }

    /** POST /api/v1/inventario/eoq-basico — EOQ clásico (Q*, N, ciclo, costo total). */
    @PostMapping("/eoq-basico")
    public ResponseEntity<SolveResult<SolucionInventario>> eoqBasico(@RequestBody ModeloInventario modelo) {
        return resolver(modelo, MetodoInventario.EOQ_BASICO);
    }

    /** POST /api/v1/inventario/eoq-descuentos — EOQ con descuentos por cantidad. */
    @PostMapping("/eoq-descuentos")
    public ResponseEntity<SolveResult<SolucionInventario>> eoqDescuentos(@RequestBody ModeloInventario modelo) {
        return resolver(modelo, MetodoInventario.EOQ_DESCUENTOS);
    }

    /** POST /api/v1/inventario/eoq-faltantes — EOQ con faltantes/backorders permitidos. */
    @PostMapping("/eoq-faltantes")
    public ResponseEntity<SolveResult<SolucionInventario>> eoqFaltantes(@RequestBody ModeloInventario modelo) {
        return resolver(modelo, MetodoInventario.EOQ_FALTANTES);
    }

    /** POST /api/v1/inventario/produccion-economica — POQ/EPQ (tasa de producción finita). */
    @PostMapping("/produccion-economica")
    public ResponseEntity<SolveResult<SolucionInventario>> produccionEconomica(@RequestBody ModeloInventario modelo) {
        return resolver(modelo, MetodoInventario.PRODUCCION_ECONOMICA);
    }

    /** POST /api/v1/inventario/punto-reorden — EOQ + punto de reorden con lead time. */
    @PostMapping("/punto-reorden")
    public ResponseEntity<SolveResult<SolucionInventario>> puntoReorden(@RequestBody ModeloInventario modelo) {
        return resolver(modelo, MetodoInventario.PUNTO_REORDEN);
    }

    private ResponseEntity<SolveResult<SolucionInventario>> resolver(ModeloInventario modelo,
                                                                     MetodoInventario metodo) {
        log.info("[inventario/{}] demanda={}, costoOrden={}, costoMantener={}",
                metodo, modelo.demanda(), modelo.costoOrden(), modelo.costoMantener());

        SolveResult<SolucionInventario> resultado = inventarioUseCase.resolver(modelo.conMetodo(metodo));

        String q = resultado.solution() != null
                ? String.valueOf(resultado.solution().cantidadOptima()) : "N/A";
        log.info("[inventario/{}] status={}, Q*={}, pasos={}",
                metodo, resultado.status(), q, resultado.steps().size());

        return ResponseEntity.ok(resultado);
    }
}
