package test;

import oyo.lol.util.DyBuf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    private static void assertBytes(String id, byte[] expected, byte[] actual) {
        if (expected.length != actual.length) {
            throw new AssertionError(id + ": byte length mismatch, expected " + expected.length + " got " + actual.length);
        }
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) {
                throw new AssertionError(id + ": byte mismatch at " + i);
            }
        }
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
}
