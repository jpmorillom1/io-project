package jpap.dev.io_api.infrastructure.entera;

import jpap.dev.io_api.application.entera.EnteraUseCase;
import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.entera.ModeloEntero;
import jpap.dev.io_api.domain.entera.SolucionEntera;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/entera")
public class EnteraController {

    private final EnteraUseCase enteraUseCase;

    public EnteraController(EnteraUseCase enteraUseCase) {
        this.enteraUseCase = enteraUseCase;
    }

    /**
     * POST /api/v1/entera/branch-and-bound
     *
     * Body ejemplo (MAX 5x1 + 4x2, s.a. 6x1+4x2<=24, x1+2x2<=6, x1,x2 enteras):
     * {
     *   "relajacion": {
     *     "variables": ["x1", "x2"],
     *     "objetivo": { "coeficientes": [5, 4], "tipo": "MAXIMIZAR" },
     *     "restricciones": [
     *       { "coeficientes": [6, 4], "tipo": "LEQ", "rhs": 24 },
     *       { "coeficientes": [1, 2], "tipo": "LEQ", "rhs": 6 }
     *     ]
     *   },
     *   "tiposVariable": ["ENTERA", "ENTERA"]
     * }
     */
    @PostMapping("/branch-and-bound")
    public ResponseEntity<SolveResult<SolucionEntera>> resolver(@RequestBody ModeloEntero modelo) {
        log.info("[entera/branch-and-bound] vars={}, tipos={}, restricciones={}",
                modelo.relajacion().variables(), modelo.tiposVariable(),
                modelo.relajacion().restricciones().size());

        SolveResult<SolucionEntera> resultado = enteraUseCase.resolver(modelo);

        String zStar = resultado.solution() != null
                ? String.valueOf(resultado.solution().valorOptimo()) : "N/A";
        log.info("[entera/branch-and-bound] status={}, Z*={}, pasos={}",
                resultado.status(), zStar, resultado.steps().size());

        return ResponseEntity.ok(resultado);
    }
}
