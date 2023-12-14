@FunctionalInterface
public interface HTTPResponseHandler {
    void apply(byte[] buffer, int bytesRead, boolean responseIsFinished);
}
