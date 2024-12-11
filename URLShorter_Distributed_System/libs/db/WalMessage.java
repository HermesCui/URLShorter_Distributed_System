package libs.db;
public class WalMessage {
    private final String key;
    private final String value;
    private final int hashValue;

    public WalMessage(String key, String value, int hashValue) {
        this.key = key;
        this.value = value;
        this.hashValue = hashValue;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public int getHashValue() {
        return hashValue;
    }

    @Override
    public String toString() {
        return key + "," + value + "," + hashValue;
    }

    public static WalMessage fromString(String line) {
        String[] parts = line.split(",");
        return new WalMessage(parts[0], parts[1], Integer.parseInt(parts[2]));
    }
}