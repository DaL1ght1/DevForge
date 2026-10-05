package com.example.devforge.config.kafka;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;


@Configuration
@EnableConfigurationProperties(KafkaProperties.class)
@RequiredArgsConstructor

public class KafkaConfig {

    private final KafkaProperties props;

    @Bean
    public NewTopic provisionRequestTopic() {
        return TopicBuilder.name(props.requestTopic())
                .partitions(props.partitions())
                .replicas(props.replicas())
                .build();
    }

    @Bean
    public NewTopic provisionResponseTopic() {
        return TopicBuilder.name(props.responseTopic())
                .partitions(props.partitions())
                .replicas(props.replicas())
                .build();
    }
}
