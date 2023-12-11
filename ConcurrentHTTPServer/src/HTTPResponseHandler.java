@FunctionalInterface
public interface HTTPResponseHandler {
    void apply(byte[] buffer, int bytesRead, String header, boolean responseIsFinished);
}
