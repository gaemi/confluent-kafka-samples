package io.github.gaemi.producer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableAutoConfiguration
@SpringBootApplication
public class SpringKafkaProducerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringKafkaProducerApplication.class, args);
    }
}
