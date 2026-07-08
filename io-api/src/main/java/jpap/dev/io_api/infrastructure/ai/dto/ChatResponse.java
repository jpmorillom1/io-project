package jpap.dev.io_api.infrastructure.ai.dto;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.entera.SolucionEntera;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.SolucionLP;
import jpap.dev.io_api.domain.lp.grafico.SolucionGrafica;
import jpap.dev.io_api.domain.redes.SolucionRed;
import jpap.dev.io_api.domain.transporte.SolucionTransporte;

/**
 * Respuesta de POST /api/v1/ai/chat y POST /api/v1/ai/chat/aprobacion.
 *
 * - respuesta:            siempre presente — texto conversacional del tutor
 * - modeloSugerido:       non-null cuando el tutor invocó registrarModeloSugerido
 * - validacion:           non-null cuando el tutor invocó registrarValidacion
 * - resultado:            non-null cuando un solver tabular (Simplex/GranM/DosFases) se ejecutó
 *                         tras la aprobación humana
 * - resultadoGrafico:     non-null cuando el método gráfico se ejecutó tras la aprobación humana
 * - resultadoTransporte:  non-null cuando un método de transporte se ejecutó tras la aprobación humana
 * - resultadoRed:         non-null cuando un problema de redes se ejecutó tras la aprobación humana
 * - resultadoEntero:      non-null cuando un modelo de PL Entera (Branch & Bound) se ejecutó tras la aprobación humana
 * - solicitudAprobacion:  non-null cuando el tutor quiere resolver y espera la aprobación del
 *                         estudiante — la UI debe mostrar el modelo con botones Aprobar/Rechazar
 */
public record ChatResponse(
        String sesionId,
        String respuesta,
        ModeloLP modeloSugerido,
        ValidacionResponse validacion,
        SolveResult<SolucionLP> resultado,
        SolveResult<SolucionGrafica> resultadoGrafico,
        SolveResult<SolucionTransporte> resultadoTransporte,
        SolveResult<SolucionRed> resultadoRed,
        SolveResult<SolucionEntera> resultadoEntero,
        SolicitudAprobacion solicitudAprobacion
) {}
