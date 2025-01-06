package io.kestra.core.http.client.configurations;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;

@Getter
@SuperBuilder(toBuilder = true)
public class BearerAuthConfiguration extends AbstractAuthConfiguration {
    @NotNull
    @JsonInclude
    @Builder.Default
    protected AuthType type = AuthType.BEARER;

    @Schema(title = "The token for bearer token authentication.")
    @PluginProperty(dynamic = true)
    private final String token;

    @Override
    public void configure(HttpClientBuilder builder) {
        builder.addRequestInterceptorFirst((request, entity, context) -> request
            .setHeader(new BasicHeader(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + this.token
            )));
    }
}
