package jpap.dev.io_api.application.entera;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.entera.ModeloEntero;
import jpap.dev.io_api.domain.entera.SolucionEntera;

public interface EnteraUseCase {
    SolveResult<SolucionEntera> resolver(ModeloEntero modelo);
}
