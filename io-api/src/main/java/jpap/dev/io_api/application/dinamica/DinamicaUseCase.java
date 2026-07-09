package jpap.dev.io_api.application.dinamica;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.dinamica.ModeloDinamico;
import jpap.dev.io_api.domain.dinamica.SolucionDinamica;

public interface DinamicaUseCase {
    SolveResult<SolucionDinamica> resolver(ModeloDinamico modelo);
}
