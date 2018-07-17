package io.github.gaemi.producer;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericContainer;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.*;
import org.apache.avro.reflect.ReflectData;
import org.apache.avro.reflect.ReflectDatumWriter;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.cache.CacheManager;
import org.springframework.cloud.stream.schema.ParsedSchema;
import org.springframework.cloud.stream.schema.SchemaNotFoundException;
import org.springframework.cloud.stream.schema.SchemaReference;
import org.springframework.cloud.stream.schema.SchemaRegistrationResponse;
import org.springframework.cloud.stream.schema.avro.AvroSchemaRegistryClientMessageConverter;
import org.springframework.cloud.stream.schema.client.ConfluentSchemaRegistryClient;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.util.MimeType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * AvroSchemaRegistryClientMessageConverter 가 Confluent Schema Registry 를 완전하게 지원하지 못하여, 이 문제를 수정하기 위한 메시지 컨버터
 *
 * - Confluent Schema Registry 를 사용하는 경우, payload 영역 앞에 매직바이트(1byte) + 스키마ID(4byte) 룰 추가하도록 되어 있다. (뒤에는 컨텐츠영역)
 * - 하지만 AvroSchemaRegistryClientMessageConverter 에서는 이 부분이 고려되어 있지 않고 payload 를 풀로 컨텐츠 영역으로 잡아버리게 된다.
 * - payload 를 Serialize 할때 앞에 매직바이트+스키마ID를 추가하고, Deserialize 할때는 이부분을 제외하고 컨텐츠영역만 파싱하도록 한다.
 */
public class ConfluentAvroSchemaRegistryClientMessageConverter extends AvroSchemaRegistryClientMessageConverter {

	private CacheManager cacheManager;

	private ConfluentSchemaRegistryClient schemaRegistryClient;

	public static final String HEADER_CONFLUENT_SCHEMA_ID = "confluentSchemaId";

	private String prefix = "vnd";

	public ConfluentAvroSchemaRegistryClientMessageConverter(ConfluentSchemaRegistryClient schemaRegistryClient, CacheManager cacheManager) {
		super(schemaRegistryClient, cacheManager);
		this.schemaRegistryClient = schemaRegistryClient;
		this.cacheManager = cacheManager;
	}


	@Override
	public void setPrefix(String prefix) {
		super.setPrefix(prefix);
		this.prefix = prefix;
	}

	@Override
	protected Schema resolveSchemaForWriting(Object payload, MessageHeaders headers,
											 MimeType hintedContentType) {

		Schema schema;
		schema = extractSchemaForWriting(payload);
		ParsedSchema parsedSchema = this.cacheManager.getCache(REFERENCE_CACHE_NAME)
				.get(schema, ParsedSchema.class);

		if (parsedSchema == null) {
			parsedSchema = new ParsedSchema(schema);
			this.cacheManager.getCache(REFERENCE_CACHE_NAME).putIfAbsent(schema,
					parsedSchema);
		}

		if (parsedSchema.getRegistration() == null) {
			SchemaRegistrationResponse response = this.schemaRegistryClient.register(
					toSubject(schema), AVRO_FORMAT, parsedSchema.getRepresentation());
			parsedSchema.setRegistration(response);

		}

		SchemaRegistrationResponse registration = parsedSchema.getRegistration();
		int schemaId = registration.getId();
		SchemaReference schemaReference = registration.getSchemaReference();

		DirectFieldAccessor dfa = new DirectFieldAccessor(headers);
		@SuppressWarnings("unchecked")
		Map<String, Object> _headers = (Map<String, Object>) dfa.getPropertyValue("headers");
		_headers.put(MessageHeaders.CONTENT_TYPE,
				"application/" + this.prefix + "." + schemaReference.getSubject()
						+ ".v" + schemaReference.getVersion() + "+" + AVRO_FORMAT);

		/*
		 * 스키마 아이디 정보를 입력한다.
		 */
		_headers.put(HEADER_CONFLUENT_SCHEMA_ID, schemaId);
		return schema;
	}

	private Schema extractSchemaForWriting(Object payload) {
		Schema schema = null;
		if (this.logger.isDebugEnabled()) {
			this.logger.debug("Obtaining schema for class " + payload.getClass());
		}
		if (GenericContainer.class.isAssignableFrom(payload.getClass())) {
			schema = ((GenericContainer) payload).getSchema();
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("Avro type detected, using schema from object");
			}
		}
		else {
			schema = this.cacheManager.getCache(REFLECTION_CACHE_NAME)
					.get(payload.getClass().getName(), Schema.class);
			if (schema == null) {
				if (!isDynamicSchemaGenerationEnabled()) {
					throw new SchemaNotFoundException(String
							.format("No schema found in the local cache for %s, and dynamic schema generation "
									+ "is not enabled", payload.getClass()));
				}
				else {
					schema = ReflectData.get().getSchema(payload.getClass());
				}
				this.cacheManager.getCache(REFLECTION_CACHE_NAME)
						.put(payload.getClass().getName(), schema);
			}
		}
		return schema;
	}

	@Override
	protected Object convertToInternal(Object payload, MessageHeaders headers, Object conversionHint) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		try {
			MimeType hintedContentType = null;
			if (conversionHint instanceof MimeType) {
				hintedContentType = (MimeType) conversionHint;
			}
			Schema schema = resolveSchemaForWriting(payload, headers, hintedContentType);

			/*
			 * Confluent 의 경우 payload 앞에 아래 내용을 추가한다.
			 * - 매직바이트 (1byte)
			 * - 스키마ID (4byte)
			 */
			Object schemaId = headers.get(HEADER_CONFLUENT_SCHEMA_ID);
			if (!(schemaId instanceof Integer)) {
				throw new IOException("Unknown schema id. " + schemaId);
			}
			baos.write(0);
			baos.write(ByteBuffer.allocate(4).putInt((Integer) schemaId).array());

			@SuppressWarnings("unchecked")
			DatumWriter<Object> writer = getDatumWriter((Class<Object>) payload.getClass(), schema);
			Encoder encoder = EncoderFactory.get().directBinaryEncoder(baos, null);
			writer.write(payload, encoder);
			encoder.flush();
		}
		catch (IOException e) {
			throw new MessageConversionException("Failed to write payload", e);
		}
		return baos.toByteArray();
	}

	private DatumWriter<Object> getDatumWriter(Class<Object> type, Schema schema) {
		DatumWriter<Object> writer;
		this.logger.debug("Finding correct DatumWriter for type " + type.getName());
		if (SpecificRecord.class.isAssignableFrom(type)) {
			if (schema != null) {
				writer = new SpecificDatumWriter<>(schema);
			}
			else {
				writer = new SpecificDatumWriter<>(type);
			}
		}
		else if (GenericRecord.class.isAssignableFrom(type)) {
			writer = new GenericDatumWriter<>(schema);
		}
		else {
			if (schema != null) {
				writer = new ReflectDatumWriter<>(schema);
			}
			else {
				writer = new ReflectDatumWriter<>(type);
			}
		}
		return writer;
	}

	@Override
	protected Object convertFromInternal(Message<?> message, Class<?> targetClass, Object conversionHint) {
		Object result = null;
		try {
			byte[] payload = (byte[]) message.getPayload();

			MimeType mimeType = getContentTypeResolver().resolve(message.getHeaders());
			if (mimeType == null) {
				if (conversionHint instanceof MimeType) {
					mimeType = (MimeType) conversionHint;
				}
				else {
					return null;
				}
			}

			/*
			 * 매직바이트 및 스키마ID 를 제외한 진짜 payload 를 추출한다.
			 */
			ByteBuffer buffer = ByteBuffer.wrap(payload);
			byte magicByte = buffer.get();
			if (magicByte != 0) {
				throw new IOException("Unknown magic byte!");
			}
			int schemaId = buffer.getInt(); // 사용은 안하고 버린다.
			int length = buffer.limit() - 1 - 4;
			byte[] actualPayload = new byte[length];
			buffer.get(actualPayload, 0, length);

			Schema writerSchema = resolveWriterSchemaForDeserialization(mimeType);
			Schema readerSchema = resolveReaderSchemaForDeserialization(targetClass);

			@SuppressWarnings("unchecked")
			DatumReader<Object> reader = getDatumReader((Class<Object>) targetClass, readerSchema, writerSchema);
			Decoder decoder = DecoderFactory.get().binaryDecoder(actualPayload, null);
			result = reader.read(null, decoder);
		}
		catch (IOException e) {
			throw new MessageConversionException(message, "Failed to read payload", e);
		}
		return result;
	}
}