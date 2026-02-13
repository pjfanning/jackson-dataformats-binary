package tools.jackson.dataformat.avro.dos;

import java.io.ByteArrayOutputStream;
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

    protected final String FIXED_SCHEMA_JSON = "{\n"
            +"\"type\": \"record\",\n"
            +"\"name\": \"FixedWrapper\",\n"
            +"\"fields\": [\n"
            +" {\"name\": \"data\", \"type\": {\"type\": \"fixed\", \"size\": 100, \"name\": \"FixedData\"}}\n"
            +"]}";

    static class BytesWrapper {
        public byte[] data;

        protected BytesWrapper() { }
        public BytesWrapper(byte[] data) {
            this.data = data;
        }
    }

    static class FixedWrapper {
        public byte[] data;

        protected FixedWrapper() { }
        public FixedWrapper(byte[] data) {
            this.data = data;
        }
    }

    private final AvroMapper NATIVE_MAPPER = newMapper();
    private final AvroMapper APACHE_MAPPER = newApacheMapper();
    
    // Mapper with low binary length limit
    private final AvroMapper NATIVE_MAPPER_LIMITED;
    private final AvroMapper APACHE_MAPPER_LIMITED;

    {
        AvroFactory nativeFactory = AvroFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxStringLength(1000000) // Allow large strings
                        .maxDocumentLength(10_000_000L) // Allow large documents
                        .build())
                .build();
        NATIVE_MAPPER_LIMITED = new AvroMapper(nativeFactory);

        AvroFactory apacheFactory = AvroFactory.builder()
                .enable(true) // Use Apache decoder
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxStringLength(1000000) // Allow large strings
                        .maxDocumentLength(10_000_000L) // Allow large documents
                        .build())
                .build();
        APACHE_MAPPER_LIMITED = new AvroMapper(apacheFactory);
    }

    private final AvroSchema BYTES_SCHEMA;
    private final AvroSchema FIXED_SCHEMA;
    {
        try {
            BYTES_SCHEMA = NATIVE_MAPPER.schemaFrom(BYTES_SCHEMA_JSON);
            FIXED_SCHEMA = NATIVE_MAPPER.schemaFrom(FIXED_SCHEMA_JSON);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testLargeBytesNativeDecoder() throws Exception
    {
        // Test with native decoder
        _testLargeBytes(NATIVE_MAPPER_LIMITED);
    }

    @Test
    public void testLargeBytesApacheDecoder() throws Exception
    {
        // Test with Apache decoder
        _testLargeBytes(APACHE_MAPPER_LIMITED);
    }

    private void _testLargeBytes(ObjectMapper mapper) throws Exception
    {
        // Create a malicious Avro document claiming to have a very large byte array
        // Without validation, this would attempt to allocate Integer.MAX_VALUE bytes
        byte[] maliciousDoc = createMaliciousBytesDoc();
        
        try (JsonParser jp = mapper.readerFor(BytesWrapper.class)
                .with(BYTES_SCHEMA)
                .createParser(maliciousDoc)) {
            while (jp.nextToken() != null) { 
                // Try to read the malicious document
            }
            fail("expected StreamConstraintsException for large byte array");
        } catch (StreamConstraintsException e) {
            // Expected - should fail with constraint exception
            assertTrue(e.getMessage().contains("exceeds the maximum allowed") 
                    || e.getMessage().contains("byte array length"),
                    "unexpected exception message: " + e.getMessage());
        }
    }

    /**
     * Creates a malicious Avro document with a bytes field claiming to be very large
     */
    private byte[] createMaliciousBytesDoc() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // Avro bytes encoding: length (zigzag varint) + data
        // Let's claim a length of 100MB (which would cause allocation issues)
        int claimedLength = 100 * 1024 * 1024; // 100MB
        
        // Encode the length as zigzag varint
        writeZigZagInt(baos, claimedLength);
        
        // We don't actually write 100MB of data - just enough to make it parse
        // This simulates an attacker sending a document claiming large size
        // In real scenario, they might send partial data to trigger allocation
        
        return baos.toByteArray();
    }

    /**
     * Write an integer using Avro's zigzag encoding
     */
    private void writeZigZagInt(ByteArrayOutputStream baos, int value) throws IOException {
        // Zigzag encoding
        int encoded = (value << 1) ^ (value >> 31);
        
        // Variable-length encoding
        while ((encoded & ~0x7F) != 0) {
            baos.write((byte) ((encoded & 0x7F) | 0x80));
            encoded >>>= 7;
        }
        baos.write((byte) encoded);
    }
}
