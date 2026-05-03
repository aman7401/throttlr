package com.throttlr.algorithm;

import com.throttlr.model.AlgorithmType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AlgorithmFactory {

    private final Map<AlgorithmType, RateLimitAlgorithm> algorithmMap;

    // Spring injects all RateLimitAlgorithm beans (one per algorithm) into the list
    public AlgorithmFactory(List<RateLimitAlgorithm> algorithms) {
        this.algorithmMap = algorithms.stream()
                .collect(Collectors.toMap(RateLimitAlgorithm::getType, Function.identity()));
    }

    public RateLimitAlgorithm get(AlgorithmType type) {
        RateLimitAlgorithm algorithm = algorithmMap.get(type);
        if (algorithm == null) {
            throw new IllegalArgumentException("No algorithm registered for type: " + type);
        }
        return algorithm;
    }
}
