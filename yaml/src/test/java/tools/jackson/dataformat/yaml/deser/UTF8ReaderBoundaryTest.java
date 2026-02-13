package tools.jackson.dataformat.yaml.deser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import tools.jackson.dataformat.yaml.UTF8Reader;

/**
 * Standalone test to reproduce and verify the fix for ArrayIndexOutOfBoundsException
 * in UTF8Reader when dealing with surrogate pairs and buffer boundaries.
 * 
 * This test can be run independently without the full test framework.
 */
public class UTF8ReaderBoundaryTest {
    
    public static void main(String[] args) {
        try {
            testSurrogateWithFullBuffer();
            System.out.println("✓ Test passed: Surrogate with full buffer handled correctly");
            
            testSurrogateWithPartialRoom();
            System.out.println("✓ Test passed: Surrogate with partial room handled correctly");
            
            testNormalOperation();
            System.out.println("✓ Test passed: Normal operation works correctly");
            
            System.out.println("\nAll tests passed! The fix is working correctly.");
        } catch (Exception e) {
            System.err.println("✗ Test failed with exception:");
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Test for the ArrayIndexOutOfBoundsException bug when a surrogate pair
     * needs to be written but the output buffer is full.
     */
    private static void testSurrogateWithFullBuffer() throws IOException {
        // Create a UTF-8 string with a 4-byte character (surrogate pair)
        // U+1F600 (😀) is a 4-byte UTF-8 character that becomes a surrogate pair in UTF-16
        String emoji = "\uD83D\uDE00";  // Surrogate pair for 😀
        byte[] utf8Bytes = emoji.getBytes(StandardCharsets.UTF_8);
        
        // Create input with enough data
        byte[] testData = new byte[utf8Bytes.length * 2];
        System.arraycopy(utf8Bytes, 0, testData, 0, utf8Bytes.length);
        System.arraycopy(utf8Bytes, 0, testData, utf8Bytes.length, utf8Bytes.length);
        
        InputStream in = new ByteArrayInputStream(testData);
        UTF8Reader reader = new UTF8Reader(in, true);
        
        // First read: read exactly one character (which is the first part of surrogate)
        char[] buffer1 = new char[1];
        int count1 = reader.read(buffer1, 0, 1);
        if (count1 != 1) {
            throw new AssertionError("Expected to read 1 character, got " + count1);
        }
        
        // Second read: try to read into a buffer with no room (start == length)
        // This would trigger the bug in the original code
        char[] buffer2 = new char[1];
        int count2 = reader.read(buffer2, 1, 0); // start=1, len=0, buffer.length=1
        
        // With the fix, this should return 0 without throwing
        if (count2 != 0) {
            throw new AssertionError("Expected to read 0 characters, got " + count2);
        }
        
        // Third read: now read the pending surrogate with proper room
        char[] buffer3 = new char[2];
        int count3 = reader.read(buffer3, 0, 2);
        if (count3 < 1) {
            throw new AssertionError("Expected to read at least 1 character, got " + count3);
        }
        
        reader.close();
    }
    
    /**
     * Test when there's exactly one position left and we have a pending surrogate.
     */
    private static void testSurrogateWithPartialRoom() throws IOException {
        // Create input with a 4-byte UTF-8 character
        String emoji = "\uD83D\uDE00ABC";  // Emoji followed by regular chars
        byte[] testData = emoji.getBytes(StandardCharsets.UTF_8);
        
        InputStream in = new ByteArrayInputStream(testData);
        UTF8Reader reader = new UTF8Reader(in, true);
        
        // Read the first part of the surrogate
        char[] buffer1 = new char[1];
        int count1 = reader.read(buffer1, 0, 1);
        if (count1 != 1) {
            throw new AssertionError("Expected to read 1 character, got " + count1);
        }
        
        // Now read into a buffer where start + len == buffer.length
        // This should successfully write the pending surrogate
        char[] buffer2 = new char[2];
        int count2 = reader.read(buffer2, 1, 1); // start=1, len=1
        if (count2 != 1) {
            throw new AssertionError("Expected to read 1 character (pending surrogate), got " + count2);
        }
        
        reader.close();
    }
    
    /**
     * Test normal operation to ensure the fix doesn't break anything.
     */
    private static void testNormalOperation() throws IOException {
        String message = "Hello, World! 😀🎉";
        byte[] testData = message.getBytes(StandardCharsets.UTF_8);
        
        InputStream in = new ByteArrayInputStream(testData);
        UTF8Reader reader = new UTF8Reader(in, true);
        
        char[] buffer = new char[100];
        int totalRead = 0;
        int count;
        
        while ((count = reader.read(buffer, totalRead, buffer.length - totalRead)) > 0) {
            totalRead += count;
        }
        
        String result = new String(buffer, 0, totalRead);
        if (!message.equals(result)) {
            throw new AssertionError("Expected: " + message + ", got: " + result);
        }
        
        reader.close();
    }
}
