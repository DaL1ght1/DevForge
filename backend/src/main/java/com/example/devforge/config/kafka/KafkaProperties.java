package com.example.devforge.config.kafka;


import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.Serializable;

@ConfigurationProperties(prefix = "devforge.kafka.topics")
public record KafkaProperties(
        String requestTopic,
        String responseTopic,
        int partitions,
        int replicas
) implements Serializable {
}
