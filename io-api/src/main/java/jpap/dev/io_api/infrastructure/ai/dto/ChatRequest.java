package jpap.dev.io_api.infrastructure.ai.dto;

public record ChatRequest(
        String sesionId,   // null → se genera uno nuevo en el controller
        String mensaje
) {}
