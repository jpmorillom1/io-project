package jpap.dev.io_api.application.lp;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.SolucionLP;
import jpap.dev.io_api.domain.lp.dosfases.DosFasesSolver;
import org.springframework.stereotype.Service;

@Service
public class DosFasesService implements DosFasesUseCase {

    private final DosFasesSolver solver = new DosFasesSolver();

    @Override
    public SolveResult<SolucionLP> resolver(ModeloLP modelo) {
        return solver.resolver(modelo);
    }
}
