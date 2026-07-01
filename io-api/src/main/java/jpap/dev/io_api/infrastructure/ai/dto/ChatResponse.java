package jpap.dev.io_api.infrastructure.ai.dto;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.SolucionLP;
import jpap.dev.io_api.domain.lp.grafico.SolucionGrafica;

/**
 * Respuesta del endpoint POST /api/v1/ai/chat.
 *
 * - respuesta:          siempre presente — texto conversacional del tutor
 * - modeloSugerido:     non-null cuando el tutor invocó registrarModeloSugerido
 * - validacion:         non-null cuando el tutor invocó registrarValidacion
 * - resultado:          non-null cuando el tutor resolvió con Simplex, GranM o DosFases
 * - resultadoGrafico:   non-null cuando el tutor resolvió con el método gráfico (2 variables)
 */
public record ChatResponse(
        String sesionId,
        String respuesta,
        ModeloLP modeloSugerido,
        ValidacionResponse validacion,
        SolveResult<SolucionLP> resultado,
        SolveResult<SolucionGrafica> resultadoGrafico
) {}
