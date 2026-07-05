package jpap.dev.io_api.application.transporte;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.transporte.ModeloTransporte;
import jpap.dev.io_api.domain.transporte.SolucionTransporte;

/**
 * Puerto de resolución de problemas de transporte. El método concreto
 * (Esquina Noroeste, Costo Mínimo, Vogel o MODI) viaja dentro del modelo.
 */
public interface TransporteUseCase {
    SolveResult<SolucionTransporte> resolver(ModeloTransporte modelo);
}
