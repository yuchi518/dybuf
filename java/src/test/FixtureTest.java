package test;

import oyo.lol.util.DyBuf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FixtureTest {
    private final Path fixtureDir;
    private int assertions = 0;

    public FixtureTest(Path fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    public static void main(String[] args) throws IOException {
        Path fixtureDir = args.length > 0 ? Path.of(args[0]) : Path.of("fixtures/v1");
        FixtureTest test = new FixtureTest(fixtureDir);
        test.verifyVarintUnsigned();
        test.verifyVarintSigned();
        test.verifyTypdex();
        test.verifyVarlenBytes();
        test.verifyVarlenStrings();
        test.verifyJsonValues();
        test.verifyJsonRandomRoundTrip();
        System.out.println("Java fixture verification passed (" + test.assertions + " assertions)");
    }

    private void verifyVarintUnsigned() throws IOException {
        for (String item : cases("varint_unsigned")) {
            String id = stringField(item, "id");
            String valueDec = stringField(item, "value_dec");
            byte[] encoded = hexBytes(stringField(item, "encoded_hex"));

            DyBuf reader = new DyBuf(encoded, true);
            long decoded = reader.getUnsignedVarLong();
            assertEquals(id, valueDec, Long.toUnsignedString(decoded));
            assertEquals(id, encoded.length, reader.position());

            DyBuf writer = new DyBuf(encoded.length + 8);
            writer.putUnsignedVarLong(Long.parseUnsignedLong(valueDec));
            assertBytes(id, encoded, writer.toBytesBeforeCurrentPosition());
        }
    }

    private void verifyVarintSigned() throws IOException {
        for (String item : cases("varint_signed")) {
            String id = stringField(item, "id");
            long value = Long.parseLong(stringField(item, "value_dec"));
            byte[] encoded = hexBytes(stringField(item, "encoded_hex"));

            DyBuf reader = new DyBuf(encoded, true);
            assertEquals(id, value, reader.getSignedVarLong());
            assertEquals(id, encoded.length, reader.position());

            DyBuf writer = new DyBuf(encoded.length + 8);
            writer.putSignedVarLong(value);
            assertBytes(id, encoded, writer.toBytesBeforeCurrentPosition());
        }
    }

    private void verifyTypdex() throws IOException {
        for (String item : cases("typdex")) {
            String id = stringField(item, "id");
            int type = intField(item, "type");
            int index = intField(item, "index");
            byte[] encoded = hexBytes(stringField(item, "encoded_hex"));

            DyBuf reader = new DyBuf(encoded, true);
            DyBuf.Typdex typdex = reader.getTypdex();
            assertEquals(id, type, typdex.type);
            assertEquals(id, index, typdex.index);
            assertEquals(id, encoded.length, reader.position());

            DyBuf writer = new DyBuf(encoded.length + 4);
            writer.putTypdex(new DyBuf.Typdex(type, index));
            assertBytes(id, encoded, writer.toBytesBeforeCurrentPosition());
        }
    }

    private void verifyVarlenBytes() throws IOException {
        for (String item : cases("varlen_bytes")) {
            String id = stringField(item, "id");
            byte[] payload = hexBytes(stringField(item, "payload_hex"));
            int payloadLength = intField(item, "payload_length");
            byte[] encoded = hexBytes(stringField(item, "encoded_hex"));
            assertEquals(id, payloadLength, payload.length);

            DyBuf reader = new DyBuf(encoded, true);
            assertBytes(id, payload, reader.getVarLengthData());
            assertEquals(id, encoded.length, reader.position());

            DyBuf writer = new DyBuf(encoded.length + 8);
            writer.putVarLengthData(payload);
            assertBytes(id, encoded, writer.toBytesBeforeCurrentPosition());
        }
    }

    private void verifyVarlenStrings() throws IOException {
        for (String item : cases("varlen_strings")) {
            String id = stringField(item, "id");
            String value = stringField(item, "utf8");
            byte[] encoded = hexBytes(stringField(item, "encoded_hex"));

            DyBuf reader = new DyBuf(encoded, true);
            assertEquals(id, value, reader.getCStringWithVarLength());
            assertEquals(id, encoded.length, reader.position());

            DyBuf writer = new DyBuf(encoded.length + 8);
            writer.putCStringWithVarLength(value);
            assertBytes(id, encoded, writer.toBytesBeforeCurrentPosition());
        }
    }

    private void verifyJsonValues() throws IOException {
        for (String item : cases("json_values")) {
            String id = stringField(item, "id");
            Object value = new JsonParser(valueField(item, "value")).parse();
            byte[] encoded = hexBytes(stringField(item, "encoded_hex"));

            assertJsonEquals(id, value, DyBuf.decodeJson(encoded));
            assertBytes(id, encoded, DyBuf.encodeJson(value));
        }
    }

    private void verifyJsonRandomRoundTrip() {
        Random random = new Random(20260711L);
        for (int i = 0; i < 100; i++) {
            Object value = randomJsonValue(random, 0);
            assertJsonEquals("random_json_" + i, value, DyBuf.decodeJson(DyBuf.encodeJson(value)));
        }
    }

    private List<String> cases(String name) throws IOException {
        String content = Files.readString(fixtureDir.resolve(name + ".json"), StandardCharsets.UTF_8);
        int casesIndex = content.indexOf("\"cases\"");
        int arrayStart = content.indexOf('[', casesIndex);
        int arrayEnd = content.lastIndexOf(']');
        if (casesIndex < 0 || arrayStart < 0 || arrayEnd < arrayStart) {
            throw new AssertionError("Missing cases array in " + name);
        }

        List<String> result = new ArrayList<>();
        int depth = 0;
        int objectStart = -1;
        boolean inString = false;
        boolean escaped = false;
        for (int i = arrayStart + 1; i < arrayEnd; i++) {
            char ch = content.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == '{') {
                if (depth == 0) objectStart = i;
                depth += 1;
            } else if (ch == '}') {
                depth -= 1;
                if (depth == 0 && objectStart >= 0) {
                    result.add(content.substring(objectStart, i + 1));
                    objectStart = -1;
                }
            }
        }
        return result;
    }

    private static String stringField(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("Missing string field " + name + " in " + json);
        }
        return unescapeJsonString(matcher.group(1));
    }

    private static int intField(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("Missing int field " + name + " in " + json);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static String valueField(String json, String name) {
        String key = "\"" + name + "\"";
        int keyIndex = json.indexOf(key);
        if (keyIndex < 0) {
            throw new AssertionError("Missing value field " + name + " in " + json);
        }
        int colon = json.indexOf(':', keyIndex + key.length());
        if (colon < 0) {
            throw new AssertionError("Missing ':' after " + name + " in " + json);
        }
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start += 1;
        }
        int end = endOfJsonValue(json, start);
        return json.substring(start, end);
    }

    private static int endOfJsonValue(String json, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == '{' || ch == '[') {
                depth += 1;
            } else if (ch == '}' || ch == ']') {
                if (depth == 0) {
                    return i;
                }
                depth -= 1;
                if (depth == 0) {
                    return i + 1;
                }
            } else if (ch == ',' && depth == 0) {
                return i;
            }
        }
        return json.length();
    }

    private static String unescapeJsonString(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch != '\\') {
                out.append(ch);
                continue;
            }
            char escaped = text.charAt(++i);
            switch (escaped) {
                case '"': out.append('"'); break;
                case '\\': out.append('\\'); break;
                case '/': out.append('/'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'n': out.append('\n'); break;
                case 'r': out.append('\r'); break;
                case 't': out.append('\t'); break;
                case 'u':
                    out.append((char) Integer.parseInt(text.substring(i + 1, i + 5), 16));
                    i += 4;
                    break;
                default:
                    throw new AssertionError("Unsupported JSON escape: \\" + escaped);
            }
        }
        return out.toString();
    }

    private static byte[] hexBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static Object randomJsonValue(Random random, int depth) {
        if (depth >= 4) {
            return randomScalar(random);
        }

        int choice = random.nextInt(8);
        if (choice <= 4) {
            return randomScalar(random);
        }
        if (choice == 5) {
            ArrayList<Object> result = new ArrayList<>();
            int length = random.nextInt(4);
            for (int i = 0; i < length; i++) {
                result.add(randomJsonValue(random, depth + 1));
            }
            return result;
        }

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        int keyCount = random.nextInt(4);
        for (int keyIndex = 0; keyIndex < keyCount; keyIndex++) {
            result.put("k" + depth + "_" + keyIndex, randomJsonValue(random, depth + 1));
        }
        return result;
    }

    private static Object randomScalar(Random random) {
        switch (random.nextInt(7)) {
            case 0:
                return null;
            case 1:
                return random.nextInt(2) == 1;
            case 2:
                return (long) (-1 - random.nextInt(1000));
            case 3:
                return (long) random.nextInt(1001);
            case 4: {
                double[] values = {0.25, -1.5, 3.5, 100.125};
                return values[random.nextInt(values.length)];
            }
            default: {
                String[] values = {"", "dybuf", "json", "map-data"};
                return values[random.nextInt(values.length)];
            }
        }
    }

    private static void assertBytes(String id, byte[] expected, byte[] actual) {
        if (expected.length != actual.length) {
            throw new AssertionError(
                    id + ": byte length mismatch, expected " + expected.length + " got " + actual.length
                            + "\nexpected=" + toHex(expected)
                            + "\nactual=" + toHex(actual)
            );
        }
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) {
                throw new AssertionError(
                        id + ": byte mismatch at " + i
                                + "\nexpected=" + toHex(expected)
                                + "\nactual=" + toHex(actual)
                );
            }
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) {
            out.append(String.format("%02x", b & 0xff));
        }
        return out.toString();
    }

    private void assertEquals(String id, long expected, long actual) {
        assertions += 1;
        if (expected != actual) {
            throw new AssertionError(id + ": expected " + expected + " got " + actual);
        }
    }

    private void assertEquals(String id, String expected, String actual) {
        assertions += 1;
        if (!expected.equals(actual)) {
            throw new AssertionError(id + ": expected " + expected + " got " + actual);
        }
    }

    private void assertJsonEquals(String id, Object expected, Object actual) {
        assertions += 1;
        if (!jsonEquals(expected, actual)) {
            throw new AssertionError(id + ": JSON mismatch, expected " + expected + " got " + actual);
        }
    }

    private static boolean jsonEquals(Object expected, Object actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        if (expected instanceof Number && actual instanceof Number) {
            double expectedDouble = ((Number) expected).doubleValue();
            double actualDouble = ((Number) actual).doubleValue();
            return Double.compare(expectedDouble, actualDouble) == 0;
        }
        if (expected instanceof List<?> && actual instanceof List<?>) {
            List<?> expectedList = (List<?>) expected;
            List<?> actualList = (List<?>) actual;
            if (expectedList.size() != actualList.size()) return false;
            for (int i = 0; i < expectedList.size(); i++) {
                if (!jsonEquals(expectedList.get(i), actualList.get(i))) return false;
            }
            return true;
        }
        if (expected instanceof Map<?, ?> && actual instanceof Map<?, ?>) {
            Map<?, ?> expectedMap = (Map<?, ?>) expected;
            Map<?, ?> actualMap = (Map<?, ?>) actual;
            if (expectedMap.size() != actualMap.size()) return false;
            for (Map.Entry<?, ?> entry : expectedMap.entrySet()) {
                if (!actualMap.containsKey(entry.getKey())) return false;
                if (!jsonEquals(entry.getValue(), actualMap.get(entry.getKey()))) return false;
            }
            return true;
        }
        return expected.equals(actual);
    }

    private static final class JsonParser {
        private final String text;
        private int position = 0;

        private JsonParser(String text) {
            this.text = text;
        }

        private Object parse() {
            Object value = parseValue();
            skipWhitespace();
            if (position != text.length()) {
                throw new AssertionError("Trailing JSON text: " + text.substring(position));
            }
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            char ch = peek();
            if (ch == '"') return parseString();
            if (ch == '{') return parseObject();
            if (ch == '[') return parseArray();
            if (ch == 't') {
                expect("true");
                return true;
            }
            if (ch == 'f') {
                expect("false");
                return false;
            }
            if (ch == 'n') {
                expect("null");
                return null;
            }
            return parseNumber();
        }

        private LinkedHashMap<String, Object> parseObject() {
            expect('{');
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                position += 1;
                return result;
            }
            while (true) {
                String key = parseString();
                skipWhitespace();
                expect(':');
                result.put(key, parseValue());
                skipWhitespace();
                char ch = peek();
                if (ch == '}') {
                    position += 1;
                    return result;
                }
                expect(',');
            }
        }

        private ArrayList<Object> parseArray() {
            expect('[');
            ArrayList<Object> result = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                position += 1;
                return result;
            }
            while (true) {
                result.add(parseValue());
                skipWhitespace();
                char ch = peek();
                if (ch == ']') {
                    position += 1;
                    return result;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                char ch = text.charAt(position++);
                if (ch == '"') return out.toString();
                if (ch != '\\') {
                    out.append(ch);
                    continue;
                }
                char escaped = text.charAt(position++);
                switch (escaped) {
                    case '"': out.append('"'); break;
                    case '\\': out.append('\\'); break;
                    case '/': out.append('/'); break;
                    case 'b': out.append('\b'); break;
                    case 'f': out.append('\f'); break;
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    case 'u':
                        out.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                        position += 4;
                        break;
                    default:
                        throw new AssertionError("Unsupported JSON escape: \\" + escaped);
                }
            }
        }

        private Object parseNumber() {
            int start = position;
            if (peek() == '-') position += 1;
            while (position < text.length() && Character.isDigit(text.charAt(position))) {
                position += 1;
            }
            boolean fractional = false;
            if (position < text.length() && text.charAt(position) == '.') {
                fractional = true;
                position += 1;
                while (position < text.length() && Character.isDigit(text.charAt(position))) {
                    position += 1;
                }
            }
            if (position < text.length() && (text.charAt(position) == 'e' || text.charAt(position) == 'E')) {
                fractional = true;
                position += 1;
                if (position < text.length() && (text.charAt(position) == '+' || text.charAt(position) == '-')) {
                    position += 1;
                }
                while (position < text.length() && Character.isDigit(text.charAt(position))) {
                    position += 1;
                }
            }
            String number = text.substring(start, position);
            if (fractional) {
                return Double.parseDouble(number);
            }
            return Long.parseLong(number);
        }

        private void skipWhitespace() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
                position += 1;
            }
        }

        private char peek() {
            if (position >= text.length()) {
                throw new AssertionError("Unexpected end of JSON text");
            }
            return text.charAt(position);
        }

        private void expect(char expected) {
            skipWhitespace();
            char actual = peek();
            if (actual != expected) {
                throw new AssertionError("Expected '" + expected + "' got '" + actual + "'");
            }
            position += 1;
        }

        private void expect(String expected) {
            if (!text.startsWith(expected, position)) {
                throw new AssertionError("Expected " + expected);
            }
            position += expected.length();
        }
    }
}
