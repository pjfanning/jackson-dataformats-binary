# Visual Comparison: The Fix

## Original Code (Buggy)
```java
175:         // Ok, first; do we have a surrogate from last round?
176:         if (_surrogate >= 0) {
177:             cbuf[outPtr++] = (char) _surrogate;  // ← BUG: No bounds check!
178:             _surrogate = -1;
179:             ...
180:         }
```

## Fixed Code
```java
175:         // Ok, first; do we have a surrogate from last round?
176:         if (_surrogate >= 0) {
177:             // Make sure there's room for the surrogate
178:             if (outPtr >= len) {                   // ← FIX: Check bounds first!
179:                 // No room, just return 0 and keep the surrogate for next time
180:                 return 0;                          // ← FIX: Return 0, keep surrogate
181:             }
182:             cbuf[outPtr++] = (char) _surrogate;
183:             _surrogate = -1;
184:             ...
185:         }
```

## What Changed
- **Added lines 177-181**: Bounds check before writing pending surrogate
- **Result**: If there's no room in the buffer, return 0 and keep the surrogate for the next call
- **Impact**: Prevents ArrayIndexOutOfBoundsException when processing multi-byte UTF-8 characters

## Example Scenario That Triggers the Bug

```
1. Input: UTF-8 bytes for emoji 😀 (4 bytes: F0 9F 98 80)
2. UTF8Reader decodes to surrogate pair: [0xD83D, 0xDE00]
3. First read(cbuf, 0, 1):
   - Writes 0xD83D to cbuf[0]
   - No room for 0xDE00, so saves it as _surrogate
4. Second read(cbuf, 1, 0):  // start=1, len=0, cbuf.length=1
   - Without fix: Tries to write to cbuf[1] → ArrayIndexOutOfBoundsException!
   - With fix: Returns 0 and keeps _surrogate for next time ✓
```

## Test Results

### Original Code
```
✗ Test failed with exception:
java.lang.ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1
	at tools.jackson.dataformat.yaml.UTF8Reader.read(UTF8Reader.java:177)
```

### Fixed Code
```
✓ Test passed: Surrogate with full buffer handled correctly
✓ Test passed: Surrogate with partial room handled correctly
✓ Test passed: Normal operation works correctly

All tests passed! The fix is working correctly.
```
