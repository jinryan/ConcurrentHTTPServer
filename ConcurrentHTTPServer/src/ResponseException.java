class ResponseException extends Exception {
    private final int statusCode;
    private final boolean hasEmptyAuthentication;
    
    public int getStatusCode() {
        return statusCode;
    }

    public boolean getHasEmptyAuthentication() {
        return hasEmptyAuthentication;
    }

    public ResponseException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
        this.hasEmptyAuthentication = false;
    }
    public ResponseException(String message, int statusCode, boolean hasEmptyAuthentication) {
        super(message);
        this.statusCode = statusCode;
        this.hasEmptyAuthentication = hasEmptyAuthentication;
    }
}