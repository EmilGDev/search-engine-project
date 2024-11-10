package searchengine.services;

import searchengine.dto.responses.SearchResults;

import java.util.Optional;

public interface SearchService {

    SearchResults search(String query, Optional<String> siteUrl, int offset, int limit);
}
