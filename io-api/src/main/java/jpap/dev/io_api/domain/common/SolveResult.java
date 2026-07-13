package jpap.dev.io_api.domain.common;

import java.util.List;

public record SolveResult<T>(
        SolveStatus status,
        T solution,
        List<SolveStep> steps
) {}
