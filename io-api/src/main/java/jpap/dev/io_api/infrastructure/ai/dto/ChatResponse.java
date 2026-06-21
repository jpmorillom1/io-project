package jpap.dev.io_api.infrastructure.ai.dto;

import jpap.dev.io_api.domain.common.SolveResult;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.SolucionLP;

/**
 * Respuesta del endpoint POST /api/v1/ai/chat.
 *
 * - respuesta:       siempre presente — texto conversacional del tutor
 * - modeloSugerido:  non-null cuando el tutor invocó registrarModeloSugerido
 *                    → la UI debe pre-llenar el formulario LP con este modelo
 * - validacion:      non-null cuando el tutor invocó registrarValidacion
 *                    → la UI debe mostrar errores inline en el formulario
 * - resultado:       non-null cuando el tutor invocó resolverSimplex
 *                    → la UI debe mostrar el tableau con navegación de pasos
 */
public record ChatResponse(
        String sesionId,
        String respuesta,
        ModeloLP modeloSugerido,
        ValidacionResponse validacion,
        SolveResult<SolucionLP> resultado
) {}
