package io.github.gaemi.producer;

import io.github.gaemi.model.Shipment;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ShipmentController {
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @PostMapping("/shipments")
    public void send(@RequestBody Shipment shipment) {
        kafkaTemplate.send("shipment", shipment);
    }
}