package jpap.dev.io_api.application.entera;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.entera.ModeloEntero;
import jpap.dev.io_api.domain.entera.SolucionEntera;
import jpap.dev.io_api.domain.entera.branchandbound.BranchAndBoundSolver;
import org.springframework.stereotype.Service;

@Service
public class EnteraService implements EnteraUseCase {

    private final BranchAndBoundSolver solver = new BranchAndBoundSolver();

    @Override
    public SolveResult<SolucionEntera> resolver(ModeloEntero modelo) {
        return solver.resolver(modelo);
    }
}
