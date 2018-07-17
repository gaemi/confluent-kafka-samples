package io.github.gaemi.consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cloud.stream.annotation.StreamMessageConverter;
import org.springframework.cloud.stream.schema.avro.AvroMessageConverterProperties;
import org.springframework.cloud.stream.schema.avro.AvroSchemaRegistryClientMessageConverter;
import org.springframework.cloud.stream.schema.avro.SubjectNamingStrategy;
import org.springframework.cloud.stream.schema.client.ConfluentSchemaRegistryClient;
import org.springframework.cloud.stream.schema.client.EnableSchemaRegistryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Constructor;

@Configuration
@EnableSchemaRegistryClient
@EnableConfigurationProperties({AvroMessageConverterProperties.class})
public class SpringCloudStreamKafkaConsumerConfig {

    @Autowired
    AvroMessageConverterProperties avroMessageConverterProperties;

    @Bean
    public ConfluentSchemaRegistryClient schemaRegistryClient(@Value("${spring.cloud.stream.schema-registry-client.endpoint") String endpoint) {
        ConfluentSchemaRegistryClient client = new ConfluentSchemaRegistryClient();
        client.setEndpoint(endpoint);
        return client;
    }

    /**
     * AvroSchemaRegistryClientMessageConverter 가 Confluent Schema Registry 를 정상적으로 지원하지 못하고 있어서,
     * 수정된 ConfluentAvroSchemaRegistryClientMessageConverter 를 사용하도록 한다.
     */
    @Bean
    @StreamMessageConverter
    public AvroSchemaRegistryClientMessageConverter avroSchemaRegistryClientMessageConverter(ConfluentSchemaRegistryClient schemaRegistryClient) {
        ConfluentAvroSchemaRegistryClientMessageConverter avroSchemaRegistryClientMessageConverter = new ConfluentAvroSchemaRegistryClientMessageConverter(
                schemaRegistryClient, new ConcurrentMapCacheManager());
        avroSchemaRegistryClientMessageConverter.setDynamicSchemaGenerationEnabled(this.avroMessageConverterProperties.isDynamicSchemaGenerationEnabled());
        if (this.avroMessageConverterProperties.getReaderSchema() != null) {
            avroSchemaRegistryClientMessageConverter.setReaderSchema(
                    this.avroMessageConverterProperties.getReaderSchema());
        }
        if (!ObjectUtils.isEmpty(this.avroMessageConverterProperties.getSchemaLocations())) {
            avroSchemaRegistryClientMessageConverter.setSchemaLocations(
                    this.avroMessageConverterProperties.getSchemaLocations());
        }
        avroSchemaRegistryClientMessageConverter.setPrefix(this.avroMessageConverterProperties.getPrefix());

        try {
            Class<?> clazz = this.avroMessageConverterProperties.getSubjectNamingStrategy();
            Constructor constructor = ReflectionUtils.accessibleConstructor(clazz);

            avroSchemaRegistryClientMessageConverter.setSubjectNamingStrategy(
                    (SubjectNamingStrategy) constructor.newInstance()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create SubjectNamingStrategy " +
                    this.avroMessageConverterProperties.getSubjectNamingStrategy().toString(),
                    ex
            );
        }

        return avroSchemaRegistryClientMessageConverter;
    }
}
