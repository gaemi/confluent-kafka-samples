package io.github.gaemi.consumer;

import io.github.gaemi.model.Shipment;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ShipmentProcessor {
    @KafkaListener(topics = "shipment")
    public void process(ConsumerRecord<String, Shipment> record) {
        log.info("received a message. {}", record);
    }
}
