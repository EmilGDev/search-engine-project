package searchengine.dto.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchResults {
    private boolean result;
    private String error;
    private Integer count;
    private List<SearchStatistic> data;

    public SearchResults(boolean result, int count, List<SearchStatistic> data) {
        this.result = result;
        this.count = count;
        this.data = data;
    }

    public SearchResults(boolean result, String error) {
        this.result = result;
        this.error = error;
    }

    @Data
    @AllArgsConstructor
    public static class SearchStatistic {
        private String site;
        private String siteName;
        private String uri;
        private String title;
        private String snippet;
        private double relevance;
    }
}
