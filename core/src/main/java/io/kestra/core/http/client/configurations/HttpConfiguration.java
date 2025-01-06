package io.kestra.core.http.client.configurations;

import io.kestra.core.models.annotations.PluginProperty;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.logging.LogLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.net.Proxy;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Builder(toBuilder = true)
@Getter
public class HttpConfiguration {
    @Schema(title = "The timeout configuration.")
    @PluginProperty
    private TimeoutConfiguration timeout;

    @Schema(title = "The proxy configuration.")
    @PluginProperty
    private ProxyConfiguration proxy;

    @Schema(title = "The authentification to use.")
    private AbstractAuthConfiguration auth;

    @Schema(title = "The SSL request options")
    private SslOptions ssl;

    @Schema(title = "Whether redirects should be followed automatically.")
    @Builder.Default
    @PluginProperty
    private Boolean followRedirects = true;

    @Schema(title = "If true, allow a failed response code (response code >= 400)")
    @Builder.Default
    @PluginProperty
    private Boolean allowFailed = false;

    @Schema(title = "The default charset for the request.")
    @Builder.Default
    @PluginProperty
    private final Charset defaultCharset = StandardCharsets.UTF_8;

    @Schema(title = "The enabled log.")
    @PluginProperty
    private LoggingType[] logs;

    public enum LoggingType {
        REQUEST_HEADERS,
        REQUEST_BODY,
        RESPONSE_HEADERS,
        RESPONSE_BODY
    }

    // Deprecated properties

    @Schema(title = "The time allowed to establish a connection to the server before failing.")
    @PluginProperty
    @Deprecated
    private final Duration connectTimeout;

    @Deprecated
    public void setConnectTimeout(Duration connectTimeout) {
        if (this.timeout == null) {
            this.timeout = TimeoutConfiguration.builder()
                .build();
        }

        this.timeout = this.timeout.toBuilder()
            .connectTimeout(connectTimeout)
            .build();
    }

    @Schema(title = "The maximum time allowed for reading data from the server before failing.")
    @Builder.Default
    @PluginProperty
    @Deprecated
    private final Duration readTimeout = Duration.ofSeconds(HttpClientConfiguration.DEFAULT_READ_TIMEOUT_SECONDS);

    @Deprecated
    public void setReadTimeout(Duration readTimeout) {
        if (this.timeout == null) {
            this.timeout = TimeoutConfiguration.builder()
                .build();
        }

        this.timeout = this.timeout.toBuilder()
            .readIdleTimeout(readTimeout)
            .build();
    }

    @Schema(title = "The type of proxy to use.")
    @Builder.Default
    @PluginProperty
    @Deprecated
    private final Proxy.Type proxyType = Proxy.Type.DIRECT;

    @Deprecated
    public void setProxyType(Proxy.Type proxyType) {
        if (this.proxy == null) {
            this.proxy = ProxyConfiguration.builder()
                .build();
        }

        this.proxy = this.proxy.toBuilder()
            .type(proxyType)
            .build();
    }

    @Schema(title = "The address of the proxy server.")
    @PluginProperty(dynamic = true)
    @Deprecated
    private final String proxyAddress;

    @Deprecated
    public void setProxyAddress(String proxyAddress) {
        if (this.proxy == null) {
            this.proxy = ProxyConfiguration.builder()
                .build();
        }

        this.proxy = this.proxy.toBuilder()
            .address(proxyAddress)
            .build();
    }

    @Schema(title = "The port of the proxy server.")
    @PluginProperty
    @Deprecated
    private final Integer proxyPort;

    @Deprecated
    public void setProxyPort(Integer proxyPort) {
        if (this.proxy == null) {
            this.proxy = ProxyConfiguration.builder()
                .build();
        }

        this.proxy = this.proxy.toBuilder()
            .port(proxyPort)
            .build();
    }

    @Schema(title = "The username for proxy authentication.")
    @PluginProperty(dynamic = true)
    @Deprecated
    private final String proxyUsername;

    @Deprecated
    public void setProxyUsername(String proxyUsername) {
        if (this.proxy == null) {
            this.proxy = ProxyConfiguration.builder()
                .build();
        }

        this.proxy = this.proxy.toBuilder()
            .username(proxyUsername)
            .build();
    }

    @Schema(title = "The password for proxy authentication.")
    @PluginProperty(dynamic = true)
    @Deprecated
    private final String proxyPassword;

    @Deprecated
    public void setProxyPassword(String proxyPassword) {
        if (this.proxy == null) {
            this.proxy = ProxyConfiguration.builder()
                .build();
        }

        this.proxy = this.proxy.toBuilder()
            .password(proxyPassword)
            .build();
    }

    @Schema(title = "The username for HTTP basic authentication.")
    @PluginProperty(dynamic = true)
    @Deprecated
    private final String basicAuthUser;

    @Deprecated
    public void setBasicAuthUser(String basicAuthUser) {
        if (this.auth == null || !(this.auth instanceof BasicAuthConfiguration)) {
            this.auth = BasicAuthConfiguration.builder()
                .build();
        }

        this.auth = ((BasicAuthConfiguration) this.auth).toBuilder()
            .username(basicAuthUser)
            .build();
    }

    @Schema(title = "The password for HTTP basic authentication.")
    @PluginProperty(dynamic = true)
    @Deprecated
    private final String basicAuthPassword;

    @Deprecated
    private void setBasicAuthPassword(String basicAuthPassword) {
        if (this.auth == null || !(this.auth instanceof BasicAuthConfiguration)) {
            this.auth = BasicAuthConfiguration.builder()
                .build();
        }

        this.auth = ((BasicAuthConfiguration) this.auth).toBuilder()
            .password(basicAuthPassword)
            .build();
    }

    @Schema(title = "The log level for the HTTP client.")
    @PluginProperty
    @Deprecated
    private final LogLevel logLevel;

    @Deprecated
    private void setLogLevel(LogLevel logLevel) {
        if (logLevel == LogLevel.TRACE) {
            this.logs = new LoggingType[]{
                LoggingType.REQUEST_HEADERS,
                LoggingType.REQUEST_BODY,
                LoggingType.RESPONSE_HEADERS,
                LoggingType.RESPONSE_BODY
            };
        } else if (logLevel == LogLevel.DEBUG) {
            this.logs = new LoggingType[]{
                LoggingType.REQUEST_HEADERS,
                LoggingType.RESPONSE_HEADERS,
            };
        } else if (logLevel == LogLevel.INFO) {
            this.logs = new LoggingType[]{
                LoggingType.RESPONSE_HEADERS,
            };
        }
    }

    // Deprecated properties with no real value to be kept, silently ignore

    @Schema(title = "The time allowed for a read connection to remain idle before closing it.")
    @Builder.Default
    @PluginProperty
    @Deprecated
    private final Duration readIdleTimeout = Duration.of(HttpClientConfiguration.DEFAULT_READ_IDLE_TIMEOUT_MINUTES, ChronoUnit.MINUTES);


    @Schema(title = "The time an idle connection can remain in the client's connection pool before being closed.")
    @Builder.Default
    @PluginProperty
    @Deprecated
    private final Duration connectionPoolIdleTimeout = Duration.ofSeconds(HttpClientConfiguration.DEFAULT_CONNECTION_POOL_IDLE_TIMEOUT_SECONDS);

    @Schema(title = "The maximum content length of the response.")
    @Builder.Default
    @PluginProperty
    @Deprecated
    private final Integer maxContentLength = HttpClientConfiguration.DEFAULT_MAX_CONTENT_LENGTH;
}
