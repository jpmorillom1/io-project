package jpap.dev.io_api.application.lp;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.grafico.GraficoSolver;
import jpap.dev.io_api.domain.lp.grafico.SolucionGrafica;
import org.springframework.stereotype.Service;

@Service
public class GraficoService implements GraficoUseCase {

    private final GraficoSolver solver = new GraficoSolver();

    @Override
    public SolveResult<SolucionGrafica> resolver(ModeloLP modelo) {
        return solver.resolver(modelo);
    }
}
