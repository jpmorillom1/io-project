package jpap.dev.io_api.application.lp;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.SolucionLP;

public interface DosFasesUseCase {
    SolveResult<SolucionLP> resolver(ModeloLP modelo);
}
