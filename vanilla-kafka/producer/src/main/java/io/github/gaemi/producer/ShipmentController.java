package io.github.gaemi.producer;

import io.github.gaemi.model.Shipment;
import lombok.RequiredArgsConstructor;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ShipmentController {
    private final Producer<String, Shipment> producer;

    @PostMapping("/shipments")
    public void send(@RequestBody Shipment shipment) {
        producer.send(new ProducerRecord<>("shipment", shipment));
    }
}