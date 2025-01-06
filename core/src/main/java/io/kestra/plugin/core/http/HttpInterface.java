package io.kestra.plugin.core.http;

import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.http.client.configurations.SslOptions;
import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

public interface HttpInterface {
    @Schema(
            title = "The fully-qualified URI that points to the HTTP destination"
    )
    @PluginProperty(dynamic = true)
    String getUri();

    @Schema(
            title = "The HTTP method to use"
    )
    @PluginProperty
    String getMethod();

    @Schema(
            title = "The full body as a string"
    )
    @PluginProperty(dynamic = true)
    String getBody();

    @Schema(
            title = "The form data to be send"
    )
    @PluginProperty(dynamic = true)
    Map<String, Object> getFormData();

    @Schema(
            title = "The request content type"
    )
    @PluginProperty(dynamic = true)
    String getContentType();

    @Schema(
            title = "The headers to pass to the request"
    )
    @PluginProperty(dynamic = true)
    Map<CharSequence, CharSequence> getHeaders();

    @Schema(
            title = "The HTTP request options"
    )
    HttpConfiguration getOptions();

    @Schema(
        title = "The SSL request options"
    )
    SslOptions getSslOptions();
}
