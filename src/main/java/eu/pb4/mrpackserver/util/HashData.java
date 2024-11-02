package eu.pb4.mrpackserver.util;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.util.*;

public record HashData(String type, byte[] hash) {
    public static HashData read(String string) {
        var splitter = string.indexOf(';');
        if (splitter == -1) {
            return new HashData("SHA-512", HexFormat.of().parseHex(string));
        } else {
            return new HashData(getJavaHash(string.substring(0, splitter)), HexFormat.of().parseHex(string.substring(splitter + 1)));
        }
    }

    public static HashData read(String type, String string) {
        return new HashData(getJavaHash(type), HexFormat.of().parseHex(string));
    }

    public static HashData read(String type, Map<String, String> hashes) {
        return read(type, hashes.get(type));
    }

    public String inline() {
        return type + ";" + HexFormat.of().formatHex(hash);
    }

    @Override
    public String toString() {
        return "HashData[type=" + type +", data=" + HexFormat.of().formatHex(hash) + "]";
    }

    private static String getJavaHash(String type) {
        return switch (type) {
            case "sha512" -> "SHA-512";
            case "sha1" -> "SHA-1";
            default -> type;
        };
    }

    public String hashString() {
        return HexFormat.of().formatHex(hash);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        HashData hashData = (HashData) object;
        return Objects.equals(type, hashData.type) && Arrays.equals(hash, hashData.hash);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(type);
        result = 31 * result + Arrays.hashCode(hash);
        return result;
    }

    public static class Serializer implements JsonSerializer<HashData>, JsonDeserializer<HashData> {
        @Override
        public HashData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return HashData.read(json.getAsString());
        }

        @Override
        public JsonElement serialize(HashData src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.inline());
        }
    }
}
