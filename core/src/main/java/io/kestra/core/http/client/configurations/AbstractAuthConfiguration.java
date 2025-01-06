package io.kestra.core.http.client.configurations;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.experimental.SuperBuilder;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes({
    @JsonSubTypes.Type(value = BasicAuthConfiguration.class, name = "BASIC"),
    @JsonSubTypes.Type(value = BearerAuthConfiguration.class, name = "BEARER")
})
@SuperBuilder(toBuilder = true)
public abstract class AbstractAuthConfiguration {
    abstract public AuthType getType();

    abstract public void configure(HttpClientBuilder builder);

    public enum AuthType {
        BASIC,
        BEARER
    }
}
