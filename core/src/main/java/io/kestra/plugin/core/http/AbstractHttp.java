package io.kestra.plugin.core.http;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.http.client.configurations.SslOptions;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Slf4j
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
abstract public class AbstractHttp extends Task implements HttpInterface {
    @NotNull
    protected String uri;

    @Builder.Default
    protected String method = "GET";

    protected String body;

    protected Map<String, Object> formData;

    @Builder.Default
    protected String contentType = "application/json";

    protected Map<CharSequence, CharSequence> headers;

    protected HttpConfiguration options;

    @Deprecated
    @Builder.Default
    @Schema(
        title = "If true, allow a failed response code (response code >= 400)"
    )
    private boolean allowFailed = false;

    @Deprecated
    public void setAllowFailed(Boolean allowFailed) {
        if (this.options == null) {
            this.options = HttpConfiguration.builder()
                .build();
        }

        this.options = this.options.toBuilder()
            .allowFailed(allowFailed)
            .build();
    }

    @Deprecated
    protected SslOptions sslOptions;

    @Deprecated
    public void sslOptions(SslOptions sslOptions) {
        if (this.options == null) {
            this.options = HttpConfiguration.builder()
                .build();
        }

        this.sslOptions = sslOptions;
        this.options = this.options.toBuilder()
            .ssl(sslOptions)
            .build();
    }

    protected HttpClient client(RunContext runContext) throws IllegalVariableEvaluationException, MalformedURLException, URISyntaxException {
        return HttpClient.builder()
            .configuration(this.options)
            .runContext(runContext)
            .build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected HttpRequest request(RunContext runContext) throws IllegalVariableEvaluationException, URISyntaxException, IOException {
        HttpRequest.HttpRequestBuilder request = HttpRequest.builder()
            .method(this.method)
            .uri(new URI(runContext.render(this.uri)));

        if (this.formData != null) {
            if ("multipart/form-data".equals(this.contentType)) {
                HashMap<String, Object> multipart = new HashMap<>();

                for (Map.Entry<String, Object> e : this.formData.entrySet()) {
                    String key = runContext.render(e.getKey());

                    if (e.getValue() instanceof String stringValue) {
                        String render = runContext.render(stringValue);

                        if (render.startsWith("kestra://")) {
                            File tempFile = runContext.workingDir().createTempFile().toFile();

                            try (OutputStream outputStream = new FileOutputStream(tempFile)) {
                                IOUtils.copyLarge(runContext.storage().getFile(new URI(render)), outputStream);
                            }

                            multipart.put(key, tempFile);
                        } else {
                            multipart.put(key, render);
                        }
                    } else if (e.getValue() instanceof Map mapValue && ((Map<String, String>) mapValue).containsKey("name") && ((Map<String, String>) mapValue).containsKey("content")) {
                        String name = runContext.render(((Map<String, String>) mapValue).get("name"));
                        String content = runContext.render(((Map<String, String>) mapValue).get("content"));

                        File tempFile = runContext.workingDir().createTempFile().toFile();
                        File renamedFile = new File(Files.move(tempFile.toPath(), tempFile.toPath().resolveSibling(name)).toUri());

                        try (OutputStream outputStream = new FileOutputStream(renamedFile)) {
                            IOUtils.copyLarge(runContext.storage().getFile(new URI(content)), outputStream);
                        }

                        multipart.put(key, renamedFile);
                    } else {
                        multipart.put(key, JacksonMapper.ofJson().writeValueAsString(e.getValue()));
                    }
                }

                request.body(HttpRequest.MultipartRequestBody.builder().content(multipart).build());
            } else {
                request.body(HttpRequest.UrlEncodedRequestBody.builder()
                    .content(runContext.render(this.formData))
                    .build()
                );
            }
        } else if (this.body != null) {
            request.body(HttpRequest.StringRequestBody.builder()
                .content(runContext.render(body))
                .contentType(runContext.render(this.contentType))
                .charset(this.options != null ? this.options.getDefaultCharset() : StandardCharsets.UTF_8)
                .build()
            );
        }

        if (this.headers != null) {
            request.headers(HttpHeaders.of(
                this.headers
                    .entrySet()
                    .stream()
                    .map(throwFunction(e -> new AbstractMap.SimpleEntry<>(
                            e.getKey().toString(),
                            runContext.render(e.getValue().toString())
                        ))
                    )
                    .collect(Collectors.groupingBy(AbstractMap.SimpleEntry::getKey, Collectors.mapping(AbstractMap.SimpleEntry::getValue, Collectors.toList()))),
                (a, b) -> true)
            );
        }

        return request.build();
    }
}
