package team.terrafirmagreg.konf;

import java.util.List;

public final class InvalidConfigException extends RuntimeException {

    private InvalidConfigException(String message) {
        super(message);
    }

    public static InvalidConfigException of(String path, String file, Object raw) {
        return new InvalidConfigException("Invalid value '" + raw + "' at '" + path + "' in " + file);
    }

    public static InvalidConfigException missing(String path, String file) {
        return new InvalidConfigException("Missing required key '" + path + "' in " + file);
    }

    public static InvalidConfigException unknown(List<String> paths, String file) {
        return new InvalidConfigException("Unknown keys in " + file + ": " + String.join(", ", paths));
    }
}
