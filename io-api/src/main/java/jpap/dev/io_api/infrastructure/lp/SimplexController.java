package jpap.dev.io_api.infrastructure.lp;

import jpap.dev.io_api.application.lp.SimplexUseCase;
import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.SolucionLP;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/lp")
public class SimplexController {

    private final SimplexUseCase simplexUseCase;

    public SimplexController(SimplexUseCase simplexUseCase) {
        this.simplexUseCase = simplexUseCase;
    }

    /**
     * POST /api/v1/lp/simplex
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
    @PostMapping("/simplex")
    public ResponseEntity<SolveResult<SolucionLP>> resolver(@RequestBody ModeloLP modelo) {
        log.info("[LP/simplex] vars={}, tipo={}, restricciones={}",
                modelo.variables(), modelo.objetivo().tipo(), modelo.restricciones().size());

        SolveResult<SolucionLP> resultado = simplexUseCase.resolver(modelo);

        String zStar = resultado.solution() != null
                ? String.valueOf(resultado.solution().valorOptimo()) : "N/A";
        log.info("[LP/simplex] status={}, Z*={}, pasos={}",
                resultado.status(), zStar, resultado.steps().size());

        return ResponseEntity.ok(resultado);
    }
}
