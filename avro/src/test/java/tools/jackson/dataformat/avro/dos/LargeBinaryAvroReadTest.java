package tools.jackson.dataformat.avro.dos;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.exc.StreamConstraintsException;

import tools.jackson.databind.ObjectMapper;

import tools.jackson.dataformat.avro.AvroFactory;
import tools.jackson.dataformat.avro.AvroMapper;
import tools.jackson.dataformat.avro.AvroSchema;
import tools.jackson.dataformat.avro.AvroTestBase;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit tests for handling large binary data that could cause OOM
 */
public class LargeBinaryAvroReadTest extends AvroTestBase
{
    protected final String BYTES_SCHEMA_JSON = "{\n"
            +"\"type\": \"record\",\n"
            +"\"name\": \"BytesWrapper\",\n"
            +"\"fields\": [\n"
            +" {\"name\": \"data\", \"type\": \"bytes\"}\n"
            +"]}";

    static class BytesWrapper {
        public byte[] data;

        protected BytesWrapper() { }
        public BytesWrapper(byte[] data) {
            this.data = data;
        }
    }

    private final AvroMapper DEFAULT_NATIVE_MAPPER = newMapper();
    private final AvroMapper DEFAULT_APACHE_MAPPER = newApacheMapper();
    
    // Mapper with low binary length limit (100 bytes)
    private final AvroMapper NATIVE_MAPPER_LIMITED;
    private final AvroMapper APACHE_MAPPER_LIMITED;

    {
        AvroFactory nativeFactory = AvroFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxStringLength(1000000) // Allow large strings
                        .maxNumberLength(100)     // Limit binary to 100 bytes
                        .build())
                .build();
        NATIVE_MAPPER_LIMITED = new AvroMapper(nativeFactory);

        AvroFactory apacheFactory = AvroFactory.builderWithApacheDecoder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxStringLength(1000000) // Allow large strings
                        .maxNumberLength(100)     // Limit binary to 100 bytes
                        .build())
                .build();
        APACHE_MAPPER_LIMITED = new AvroMapper(apacheFactory);
    }

    private final AvroSchema BYTES_SCHEMA;
    {
        try {
            BYTES_SCHEMA = DEFAULT_NATIVE_MAPPER.schemaFrom(BYTES_SCHEMA_JSON);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testNormalSizeBytesNativeDecoder() throws Exception
    {
        // Test that normal-sized byte arrays work fine
        byte[] normalData = new byte[50]; // 50 bytes, within limit
        for (int i = 0; i < normalData.length; i++) {
            normalData[i] = (byte) i;
        }
        BytesWrapper input = new BytesWrapper(normalData);
        
        byte[] avroDoc = DEFAULT_NATIVE_MAPPER.writer(BYTES_SCHEMA)
                .writeValueAsBytes(input);
        assertNotNull(avroDoc);
        
        // Should work fine with limited mapper
        BytesWrapper output = NATIVE_MAPPER_LIMITED.readerFor(BytesWrapper.class)
                .with(BYTES_SCHEMA)
                .readValue(avroDoc);
        assertNotNull(output);
        assertNotNull(output.data);
    }

    @Test
    public void testLargeBytesNativeDecoder() throws Exception
    {
        // Test with native decoder - create a document with large byte array
        _testLargeBytes(DEFAULT_NATIVE_MAPPER, NATIVE_MAPPER_LIMITED);
    }

    @Test
    public void testLargeBytesApacheDecoder() throws Exception
    {
        // Test with Apache decoder
        _testLargeBytes(DEFAULT_APACHE_MAPPER, APACHE_MAPPER_LIMITED);
    }

    private void _testLargeBytes(ObjectMapper writerMapper, ObjectMapper readerMapper) throws Exception
    {
        // Create a byte array that exceeds the limit (200 bytes > 100 byte limit)
        byte[] largeData = new byte[200];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) i;
        }
        BytesWrapper input = new BytesWrapper(largeData);
        
        // Write with unlimited mapper
        byte[] avroDoc = writerMapper.writer(BYTES_SCHEMA)
                .writeValueAsBytes(input);
        assertNotNull(avroDoc);
        
        // Try to read with limited mapper - should fail
        try {
            readerMapper.readerFor(BytesWrapper.class)
                    .with(BYTES_SCHEMA)
                    .readValue(avroDoc);
            fail("expected StreamConstraintsException for large byte array");
        } catch (StreamConstraintsException e) {
            // Expected - should fail with constraint exception
            String msg = e.getMessage();
            assertTrue(msg.contains("exceeds the maximum allowed") 
                    || msg.contains("Number value length"),
                    "unexpected exception message: " + msg);
        }
    }
}
