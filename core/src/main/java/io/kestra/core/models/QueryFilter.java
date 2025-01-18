package io.kestra.core.models;

import lombok.Builder;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;


@Builder
public record QueryFilter(
     String field,
     Op operation,
     Object value
) {
   public enum Op {
        EQUALS("$eq"),
        NOT_EQUALS("$ne"),
        GREATER_THAN("$gt"),
        LESS_THAN("$lt"),
        IN("$in"),
        NOT_IN("$notIn"),
        STARTS_WITH("$startsWith"),
        ENDS_WITH("$endsWith"),
        CONTAINS("$contains"),
        REGEX("$regex");
        private final String value;

        Op(String value) {
            this.value = value;
        }
        public String value() {
            return value;
        }
        private static final Map<String, Op> OP_BY_VALUE = Arrays.stream(Op.values())
            .collect(Collectors.toMap(Op::value, Function.identity()));

        public static Op fromString(String value) {
          return Optional.ofNullable( OP_BY_VALUE.get(value))
                .orElseThrow( () ->
                    new IllegalArgumentException(String.format(
                        "Unsupported operation '%s'. Expected one of: %s",
                        value,
                       OP_BY_VALUE.keySet().stream()
                            .collect(Collectors.joining(", ", "[", "]"))
                    ))
                );
        }
    }
}
