package jpap.dev.io_api.application.lp;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.SolucionLP;
import jpap.dev.io_api.domain.lp.granm.GranMSolver;
import org.springframework.stereotype.Service;

@Service
public class GranMService implements GranMUseCase {

    private final GranMSolver solver = new GranMSolver();

    @Override
    public SolveResult<SolucionLP> resolver(ModeloLP modelo) {
        return solver.resolver(modelo);
    }
}
