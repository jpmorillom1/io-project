package jpap.dev.io_api.infrastructure.lp;

import jpap.dev.io_api.application.lp.DosFasesUseCase;
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
public class DosFasesController {

    private final DosFasesUseCase dosFasesUseCase;

    public DosFasesController(DosFasesUseCase dosFasesUseCase) {
        this.dosFasesUseCase = dosFasesUseCase;
    }

    @PostMapping("/dos-fases")
    public ResponseEntity<SolveResult<SolucionLP>> resolver(@RequestBody ModeloLP modelo) {
        log.info("[LP/dos-fases] vars={}, tipo={}, restricciones={}",
                modelo.variables(), modelo.objetivo().tipo(), modelo.restricciones().size());
        SolveResult<SolucionLP> resultado = dosFasesUseCase.resolver(modelo);
        log.info("[LP/dos-fases] status={}, Z*={}, pasos={}",
                resultado.status(),
                resultado.solution() != null ? resultado.solution().valorOptimo() : "N/A",
                resultado.steps().size());
        return ResponseEntity.ok(resultado);
    }
}
