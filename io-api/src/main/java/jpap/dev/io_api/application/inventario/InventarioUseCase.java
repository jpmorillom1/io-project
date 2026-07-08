package jpap.dev.io_api.application.inventario;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.inventario.ModeloInventario;
import jpap.dev.io_api.domain.inventario.SolucionInventario;

public interface InventarioUseCase {
    SolveResult<SolucionInventario> resolver(ModeloInventario modelo);
}
