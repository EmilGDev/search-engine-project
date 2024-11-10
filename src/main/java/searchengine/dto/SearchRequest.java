package searchengine.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SearchRequest {
    private String query;
    private String site;
    private int offset = 0;
    private int limit = 20;
}
