class ResponseException extends Exception {
    private final int statusCode; 
    
    public int getStatusCode() {
        return statusCode;
    }

    public ResponseException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }
}