package io.github.gaemi.consumer;

import io.github.gaemi.model.Shipment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ShipmentProcessor {

    private final Consumer<String, Shipment> consumer;

    @EventListener(ApplicationReadyEvent.class)
    public void process() {
        while (true) {
            consumer.poll(1000).forEach(record -> {
                log.info("received a message. {}", record);
            });

            consumer.commitAsync();
        }
    }

}
