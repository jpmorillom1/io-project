package jpap.dev.io_api.application.inventario;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.inventario.ModeloInventario;
import jpap.dev.io_api.domain.inventario.SolucionInventario;
import jpap.dev.io_api.domain.inventario.descuentos.EoqDescuentosSolver;
import jpap.dev.io_api.domain.inventario.eoqbasico.EoqBasicoSolver;
import jpap.dev.io_api.domain.inventario.faltantes.EoqFaltantesSolver;
import jpap.dev.io_api.domain.inventario.produccion.ProduccionEconomicaSolver;
import jpap.dev.io_api.domain.inventario.reorden.PuntoReordenSolver;
import org.springframework.stereotype.Service;

/**
 * Adaptador de aplicación: despacha al solver de inventario según el método del modelo.
 * Los solvers son objetos Java puros (creados con new), igual que en Transporte y Redes.
 *
 * No hay método por defecto: los cinco modelos son heterogéneos, así que un modelo sin
 * método es entrada malformada (400).
 */
@Service
public class InventarioService implements InventarioUseCase {

    private final EoqBasicoSolver eoqBasico = new EoqBasicoSolver();
    private final EoqDescuentosSolver eoqDescuentos = new EoqDescuentosSolver();
    private final EoqFaltantesSolver eoqFaltantes = new EoqFaltantesSolver();
    private final ProduccionEconomicaSolver produccion = new ProduccionEconomicaSolver();
    private final PuntoReordenSolver puntoReorden = new PuntoReordenSolver();

    @Override
    public SolveResult<SolucionInventario> resolver(ModeloInventario modelo) {
        if (modelo == null || modelo.metodo() == null)
            throw new IllegalArgumentException(
                    "El modelo necesita el método de inventario (metodo): EOQ_BASICO, EOQ_DESCUENTOS, "
                    + "EOQ_FALTANTES, PRODUCCION_ECONOMICA o PUNTO_REORDEN.");
        return switch (modelo.metodo()) {
            case EOQ_BASICO -> eoqBasico.resolver(modelo);
            case EOQ_DESCUENTOS -> eoqDescuentos.resolver(modelo);
            case EOQ_FALTANTES -> eoqFaltantes.resolver(modelo);
            case PRODUCCION_ECONOMICA -> produccion.resolver(modelo);
            case PUNTO_REORDEN -> puntoReorden.resolver(modelo);
        };
    }
}
