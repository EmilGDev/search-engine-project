package searchengine.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.responses.IndexingResponse;
import searchengine.dto.responses.SearchResults;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final SearchService searchService;
    private final IndexingService indexingService;
    private final StatisticsService statisticsService;

    public ApiController(SearchService searchService,
                         StatisticsService statisticsService,
                         IndexingService indexingService) {
        this.searchService = searchService;
        this.indexingService = indexingService;
        this.statisticsService = statisticsService;
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<String> startIndexing() {
        IndexingResponse response = indexingService.startIndexing();

        return response.isResult()
                ? ResponseEntity.ok("'result': " + response.isResult())
                : ResponseEntity.status(HttpStatus.BAD_REQUEST).body("'result': "
                + response.isResult() + "\n" +
                "'error': " + response.getMessage());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<String> stopIndexing() {
        IndexingResponse response = indexingService.stopIndexing();

        return response.isResult()
                ? ResponseEntity.ok("'result': " + response.isResult())
                : ResponseEntity.status(HttpStatus.BAD_REQUEST).body("'result': "
                + response.isResult() + "\n" +
                "'error': " + response.getMessage());
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResults> search(
            @RequestParam String query,
            @RequestParam(required = false) String site,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit) {

        Optional<String> siteUrl = Optional.ofNullable(site);

        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new SearchResults(false, "Задан пустой поисковый запрос"));
        }

        SearchResults searchResults = searchService.search(query, siteUrl, offset, limit);

        return searchResults.isResult()
                ? ResponseEntity.ok(searchResults)
                : ResponseEntity.badRequest().body(searchResults);
    }

    @PostMapping("/indexPage")
    public ResponseEntity<String> indexPage(@RequestParam String url) {
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body("'result': 'false'\n"
                    + "'error': URL не может быть пустым");
        }

        IndexingResponse response = indexingService.indexPage(url);

        return response.isResult()
                ? ResponseEntity.ok("'result': " + response.isResult())
                : ResponseEntity.status(HttpStatus.BAD_REQUEST).body("'result': "
                + response.isResult() + "\n" +
                "'error': " + response.getMessage());
    }
}
