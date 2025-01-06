package io.kestra.core.http.client.configurations;

import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;

@Builder(toBuilder = true)
@Getter
public class TimeoutConfiguration {
    @Schema(title = "The time allowed to establish a connection to the server before failing.")
    @PluginProperty
    Duration connectTimeout;

    @Schema(title = "The time allowed for a read connection to remain idle before closing it.")
    @Builder.Default
    @PluginProperty
    Duration readIdleTimeout = Duration.ofMinutes(5);
}
