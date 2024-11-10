package searchengine.dto.responses;

public class PageResponse {
    private int statusCode;
    private String content;

    public PageResponse(int statusCode, String content) {
        this.statusCode = statusCode;
        this.content = content;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getContent() {
        return content;
    }
}
