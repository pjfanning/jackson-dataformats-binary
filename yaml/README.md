# YAML Module - UTF8Reader ArrayIndexOutOfBoundsException Fix

## Issue Description

An `ArrayIndexOutOfBoundsException` was occurring in `UTF8Reader.read()` at line 177:

```
java.lang.ArrayIndexOutOfBoundsException: Index 1025 out of bounds for length 1025
 at tools.jackson.dataformat.yaml.UTF8Reader.read(UTF8Reader.java:177)
 at org.snakeyaml.engine.v2.scanner.StreamReader.extendIfTrailingHighSurrogate(StreamReader.java:275)
 at org.snakeyaml.engine.v2.scanner.StreamReader.update(StreamReader.java:247)
```

## Root Cause

The bug occurred in the `read(char[] cbuf, int start, int len)` method when:

1. A 4-byte UTF-8 character (which requires a surrogate pair in UTF-16) was being decoded
2. The first part of the surrogate pair was written, but the second part couldn't fit in the buffer
3. The second part was saved for the next read call
4. On the next read call, if `start + len >= cbuf.length` (i.e., no room in the buffer), the code would try to write the pending surrogate without checking if there was space

The problematic code at line 177 was:

```java
if (_surrogate >= 0) {
    cbuf[outPtr++] = (char) _surrogate;  // No bounds check!
    _surrogate = -1;
    ...
}
```

## The Fix

Added a bounds check before writing the pending surrogate:

```java
if (_surrogate >= 0) {
    // Make sure there's room for the surrogate
    if (outPtr >= len) {
        // No room, just return 0 and keep the surrogate for next time
        return 0;
    }
    cbuf[outPtr++] = (char) _surrogate;
    _surrogate = -1;
    ...
}
```

## Testing

The fix has been validated with three test cases in `UTF8ReaderBoundaryTest.java`:

1. **testSurrogateWithFullBuffer**: Reproduces the exact bug scenario where a pending surrogate needs to be written but the output buffer is completely full
2. **testSurrogateWithPartialRoom**: Tests the edge case where there's exactly one position left in the buffer
3. **testNormalOperation**: Ensures the fix doesn't break normal operation

### Test Results

Original code (without fix):
```
✗ Test failed with exception:
java.lang.ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1
	at tools.jackson.dataformat.yaml.UTF8Reader.read(UTF8Reader.java:177)
```

Fixed code:
```
✓ Test passed: Surrogate with full buffer handled correctly
✓ Test passed: Surrogate with partial room handled correctly
✓ Test passed: Normal operation works correctly

All tests passed! The fix is working correctly.
```

## Running the Tests

To compile and run the tests:

```bash
cd yaml
javac -cp src/main/java:src/test/java \
    src/main/java/tools/jackson/dataformat/yaml/UTF8Reader.java \
    src/test/java/tools/jackson/dataformat/yaml/deser/UTF8ReaderBoundaryTest.java
    
java -cp src/main/java:src/test/java \
    tools.jackson.dataformat.yaml.deser.UTF8ReaderBoundaryTest
```

## Files Changed

- `yaml/src/main/java/tools/jackson/dataformat/yaml/UTF8Reader.java`: Added bounds check at line 177-181
- `yaml/src/test/java/tools/jackson/dataformat/yaml/deser/UTF8ReaderBoundaryTest.java`: New test file
- `yaml/src/test/java/tools/jackson/dataformat/yaml/deser/UTF8ReaderTest.java`: Added test case
- `yaml/pom.xml`: Module configuration
- `pom.xml`: Added yaml module to parent

## Impact

This fix prevents the `ArrayIndexOutOfBoundsException` when reading UTF-8 data containing multi-byte characters (like emojis) near buffer boundaries. The fix is minimal and surgical, only adding the necessary bounds check without changing the overall logic or behavior of the UTF8Reader.
