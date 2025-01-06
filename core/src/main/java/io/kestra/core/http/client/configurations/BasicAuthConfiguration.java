package io.kestra.core.http.client.configurations;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

@Getter
@SuperBuilder(toBuilder = true)
public class BasicAuthConfiguration extends AbstractAuthConfiguration {
    @NotNull
    @JsonInclude
    @Builder.Default
    protected AuthType type = AuthType.BASIC;

    @Schema(title = "The username for HTTP basic authentication.")
    @PluginProperty(dynamic = true)
    private final String username;

    @Schema(title = "The password for HTTP basic authentication.")
    @PluginProperty(dynamic = true)
    private final String password;

    @Override
    public void configure(HttpClientBuilder builder) {
        byte[] encoded = Base64.getEncoder()
            .encode((this.getUsername() + ":" + this.getPassword()).getBytes(StandardCharsets.UTF_8));

        builder.addRequestInterceptorFirst((request, entity, context) -> request
            .setHeader(new BasicHeader(
                HttpHeaders.AUTHORIZATION,
                "Basic " + new String(encoded, StandardCharsets.UTF_8)
            )));
    }
}
