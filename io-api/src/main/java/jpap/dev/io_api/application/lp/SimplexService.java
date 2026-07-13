package jpap.dev.io_api.application.lp;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.SolucionLP;
import jpap.dev.io_api.domain.lp.simplex.SimplexSolver;
import org.springframework.stereotype.Service;

@Service
public class SimplexService implements SimplexUseCase {

    private final SimplexSolver solver = new SimplexSolver();

    @Override
    public SolveResult<SolucionLP> resolver(ModeloLP modelo) {
        return solver.resolver(modelo);
    }
}
