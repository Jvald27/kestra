package io.kestra.core.http.client.configurations;

import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class ProxyConfiguration {
    @Schema(title = "The type of proxy to use.")
    @Builder.Default
    @PluginProperty
    private final java.net.Proxy.Type type = java.net.Proxy.Type.DIRECT;

    @Schema(title = "The address of the proxy server.")
    @PluginProperty(dynamic = true)
    private final String address;

    @Schema(title = "The port of the proxy server.")
    @PluginProperty
    private final Integer port;

    @Schema(title = "The username for proxy authentication.")
    @PluginProperty(dynamic = true)
    private final String username;

    @Schema(title = "The password for proxy authentication.")
    @PluginProperty(dynamic = true)
    private final String password;
}
