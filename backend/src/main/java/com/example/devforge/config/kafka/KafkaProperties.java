package com.example.devforge.config.kafka;

import java.io.Serializable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "devforge.kafka.topics")
public record KafkaProperties(
    String requestTopic, String responseTopic, int partitions, int replicas)
    implements Serializable {}
