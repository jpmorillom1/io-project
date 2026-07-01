package jpap.dev.io_api.infrastructure.lp;

import jpap.dev.io_api.application.lp.GraficoUseCase;
import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.grafico.SolucionGrafica;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/lp")
public class GraficoController {

    private final GraficoUseCase graficoUseCase;

    public GraficoController(GraficoUseCase graficoUseCase) {
        this.graficoUseCase = graficoUseCase;
    }

    /**
     * POST /api/v1/lp/grafico
     *
     * Requiere exactamente 2 variables. Soporta LEQ, GEQ y EQ.
     *
     * Body ejemplo (MAX 3x1 + 5x2, s.a. x1<=4, 2x2<=12, 3x1+5x2<=25):
     * {
     *   "variables": ["x1", "x2"],
     *   "objetivo": { "coeficientes": [3, 5], "tipo": "MAXIMIZAR" },
     *   "restricciones": [
     *     { "coeficientes": [1, 0], "tipo": "LEQ", "rhs": 4 },
     *     { "coeficientes": [0, 2], "tipo": "LEQ", "rhs": 12 },
     *     { "coeficientes": [3, 5], "tipo": "LEQ", "rhs": 25 }
     *   ]
     * }
     */
    @PostMapping("/grafico")
    public ResponseEntity<SolveResult<SolucionGrafica>> resolver(@RequestBody ModeloLP modelo) {
        log.info("[LP/grafico] vars={}, tipo={}, restricciones={}",
                modelo.variables(), modelo.objetivo().tipo(), modelo.restricciones().size());

        SolveResult<SolucionGrafica> resultado = graficoUseCase.resolver(modelo);

        String zStar = resultado.solution() != null
                ? String.valueOf(resultado.solution().valorOptimo()) : "N/A";
        log.info("[LP/grafico] status={}, Z*={}, pasos={}",
                resultado.status(), zStar, resultado.steps().size());

        return ResponseEntity.ok(resultado);
    }
}
