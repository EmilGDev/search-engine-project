package searchengine.dto.responses;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IndexingResponse {
    private boolean result;
    private String message;

    public IndexingResponse(boolean result) {
        this.result = result;
    }

    public IndexingResponse(boolean result, String message) {
        this.result = result;
        this.message = message;
    }
}
