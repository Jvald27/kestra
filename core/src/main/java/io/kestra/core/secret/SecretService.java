package io.kestra.core.secret;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@Slf4j
public class SecretService {
    private static final String SECRET_PREFIX = "SECRET_";
    private Map<String, String> decodedSecrets;

    @PostConstruct
    private void postConstruct() {
        this.decode();
    }

    public void decode() {
        decodedSecrets = System.getenv().entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(SECRET_PREFIX))
            .<Map.Entry<String, String>>mapMulti((entry, consumer) -> {
                try {
                    String value = entry.getValue().replaceAll("\\R", "");
                    consumer.accept(Map.entry(entry.getKey(), new String(Base64.getDecoder().decode(value))));
                } catch (Exception e) {
                    log.error("Could not decode secret '{}', make sure it is Base64-encoded: {}", entry.getKey(), e.getMessage());
                }
            })
            .collect(Collectors.toMap(
                entry -> entry.getKey().substring(SECRET_PREFIX.length()).toUpperCase(),
                Map.Entry::getValue
            ));
    }

    public String findSecret(String tenantId, String namespace, String key) throws SecretNotFoundException, IOException {
        String secret = decodedSecrets.get(key.toUpperCase());
        if (secret == null) {
            throw new SecretNotFoundException("Cannot find secret for key '" + key + "'.");
        }
        return secret;
    }

    public Map<String, Set<String>> inheritedSecrets(String tenantId, String namespace) throws IOException {
        return Map.of(namespace, decodedSecrets.keySet());
    }
}
