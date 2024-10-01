package io.kestra.core.trace;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.convert.format.MapFormat;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

@ConfigurationProperties("kestra.traces")
public record TracesConfiguration (
    @NotNull
    TraceLevel root,

    @NotNull
    @MapFormat(transformation = MapFormat.MapTransformation.FLAT)
    Map<String, TraceLevel> categories
) {
}
