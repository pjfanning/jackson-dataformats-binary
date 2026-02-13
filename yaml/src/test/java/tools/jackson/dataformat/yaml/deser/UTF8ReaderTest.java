package tools.jackson.dataformat.yaml.deser;

import java.io.*;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import tools.jackson.dataformat.yaml.UTF8Reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UTF8ReaderTest
{
    @Test
    public void canUseMultipleUTF8ReadersInSameThread() throws IOException {
        String message = "we expect this message to be present after reading the contents of the reader out";
        InputStream expected = new ByteArrayInputStream(("." + message).getBytes(StandardCharsets.UTF_8));
        InputStream overwriter =
                new ByteArrayInputStream(".in older versions of Jackson, this overwrote it"
                        .getBytes(StandardCharsets.UTF_8));

        char[] result = new char[message.length()];

        UTF8Reader utf8Reader = new UTF8Reader(expected, true);
        UTF8Reader badUtf8Reader = new UTF8Reader(overwriter, true);

        utf8Reader.read();
        badUtf8Reader.read();

        utf8Reader.read(result);

        assertEquals(message, new String(result));

        utf8Reader.close();
        badUtf8Reader.close();
    }

    /**
     * Test for the ArrayIndexOutOfBoundsException bug when a surrogate pair
     * needs to be written but the output buffer is full.
     * 
     * This reproduces the issue where UTF8Reader.read() tries to write a pending
     * surrogate character at an index that is out of bounds.
     */
    @Test
    public void testSurrogateWithFullBuffer() throws IOException {
        // Create a UTF-8 string with a 4-byte character (surrogate pair)
        // U+1F600 (😀) is a 4-byte UTF-8 character that becomes a surrogate pair in UTF-16
        String emoji = "\uD83D\uDE00";  // Surrogate pair for 😀
        byte[] utf8Bytes = emoji.getBytes(StandardCharsets.UTF_8);
        
        // Create input with enough data to test boundary conditions
        byte[] testData = new byte[utf8Bytes.length * 2];
        System.arraycopy(utf8Bytes, 0, testData, 0, utf8Bytes.length);
        System.arraycopy(utf8Bytes, 0, testData, utf8Bytes.length, utf8Bytes.length);
        
        InputStream in = new ByteArrayInputStream(testData);
        UTF8Reader reader = new UTF8Reader(in, true);
        
        // First read: read exactly one character (which is the first part of surrogate)
        // This should leave the second part pending
        char[] buffer1 = new char[1];
        int count1 = reader.read(buffer1, 0, 1);
        assertEquals(1, count1, "Should read one character");
        
        // Second read: try to read into a buffer with no room (start == length)
        // This should trigger the bug in the original code where it tries to write
        // the pending surrogate at index == buffer.length
        char[] buffer2 = new char[1];
        int count2 = reader.read(buffer2, 1, 0); // start=1, len=0, buffer.length=1
        
        // With the fix, this should return 0 (no characters read) without throwing
        assertEquals(0, count2, "Should return 0 when no room for pending surrogate");
        
        // Third read: now read the pending surrogate with proper room
        char[] buffer3 = new char[2];
        int count3 = reader.read(buffer3, 0, 2);
        assertTrue(count3 >= 1, "Should read at least one character (the pending surrogate)");
        
        reader.close();
    }
}
