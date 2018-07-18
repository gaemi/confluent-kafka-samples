package io.github.gaemi.consumer;

import io.github.gaemi.model.Shipment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Collections;
import java.util.concurrent.TimeUnit;


@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ShipmentProcessor {

    private final Consumer<String, Shipment> consumer;

    @PostConstruct
    public void subscribe() {
        consumer.subscribe(Collections.singletonList("shipment"));

        while (true) {
            consumer.poll(1000).forEach(record -> {
                log.info("received a message. {}", record);
            });

            consumer.commitAsync();
        }
    }

    @PreDestroy
    public void destroy() {
        consumer.close(5, TimeUnit.SECONDS);
    }
}
