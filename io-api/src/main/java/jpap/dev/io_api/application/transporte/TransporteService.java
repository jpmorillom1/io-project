package jpap.dev.io_api.application.transporte;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.transporte.MetodoTransporte;
import jpap.dev.io_api.domain.transporte.ModeloTransporte;
import jpap.dev.io_api.domain.transporte.SolucionTransporte;
import jpap.dev.io_api.domain.transporte.costominimo.CostoMinimoSolver;
import jpap.dev.io_api.domain.transporte.esquinanoroeste.EsquinaNoroesteSolver;
import jpap.dev.io_api.domain.transporte.modi.ModiSolver;
import jpap.dev.io_api.domain.transporte.vogel.VogelSolver;
import org.springframework.stereotype.Service;

/**
 * Adaptador de aplicación: despacha al solver de transporte según el método del modelo.
 * Los solvers son objetos Java puros (creados con new), igual que en el módulo LP.
 */
@Service
public class TransporteService implements TransporteUseCase {

    private final EsquinaNoroesteSolver esquinaNoroeste = new EsquinaNoroesteSolver();
    private final CostoMinimoSolver costoMinimo = new CostoMinimoSolver();
    private final VogelSolver vogel = new VogelSolver();
    private final ModiSolver modi = new ModiSolver();

    @Override
    public SolveResult<SolucionTransporte> resolver(ModeloTransporte modelo) {
        MetodoTransporte metodo = modelo.metodo() != null ? modelo.metodo() : MetodoTransporte.MODI;
        return switch (metodo) {
            case ESQUINA_NOROESTE -> esquinaNoroeste.resolver(modelo);
            case COSTO_MINIMO -> costoMinimo.resolver(modelo);
            case VOGEL -> vogel.resolver(modelo);
            case MODI -> modi.resolver(modelo);
        };
    }
}
