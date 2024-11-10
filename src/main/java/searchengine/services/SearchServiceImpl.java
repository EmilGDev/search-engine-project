package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.converter.json.GsonBuilderUtils;
import org.springframework.stereotype.Service;
import org.w3c.dom.ls.LSOutput;
import searchengine.dto.responses.SearchResults;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.util.CleanHtmlCode;
import searchengine.util.Morphology;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final Morphology morphology;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;


    @Override
    public SearchResults search(String query, Optional<String> siteUrl, int offset, int limit) {

        Optional<SearchResults> validationError = validateSiteForSearch(siteUrl);
        if (validationError.isPresent()) {
            return validationError.get(); // Возвращаем результат с ошибкой
        }

        List<SiteEntity> sites = siteUrl
                .map(url -> siteRepository.findByUrlAndStatus(url, Status.INDEXED).stream().toList()) // Если указан сайт, ищем по нему и по статусу
                .orElseGet(() -> siteRepository.findAllByStatus(Status.INDEXED)); // Иначе ищем по всем индексированным сайтам

        if (siteUrl.isPresent() && sites.isEmpty()) {
            return new SearchResults(false, "Указанный сайт не найден или не проиндексирован");
        }

        if (sites.isEmpty()) {
            return new SearchResults(false, "Индекс не готов. Нет ни одного проиндексированного сайта.");
        }

        Map<String, Integer> lemmasFromQuery = morphology.getLemmaList(query);
        Map<String, Integer> filteredLemmas = filterFrequentLemmas(lemmasFromQuery, sites);

        List<LemmaEntity> sortedLemmas = getSortedLemmas(filteredLemmas.keySet(), sites);
        List<PageEntity> relevantPages = getRelevantPages(sortedLemmas);

        List<SearchResults.SearchStatistic> searchStatisticList = calculateRelevance(relevantPages, sortedLemmas);
        searchStatisticList.sort(Comparator.comparingDouble(SearchResults.SearchStatistic::getRelevance).reversed());

        List<SearchResults.SearchStatistic> paginatedResults = searchStatisticList.stream()
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());

        return new SearchResults(true, searchStatisticList.size(), paginatedResults);
    }

    public Optional<SearchResults> validateSiteForSearch(Optional<String> siteUrl) {
        if (siteUrl.isPresent()) {
            Optional<SiteEntity> siteEntity = siteRepository.findByUrl(siteUrl.get());
            if (siteEntity.isEmpty()) {
                return Optional.of(new SearchResults(false, "Сайт не найден в базе данных."));
            }
            if (siteEntity.get().getStatus() == Status.INDEXING) {
                return Optional.of(new SearchResults(false, "Сайт в процессе индексации. Попробуйте позже."));
            }
        }
        return Optional.empty();
    }

    private Map<String, Integer> filterFrequentLemmas(Map<String, Integer> lemmasFromQuery, List<SiteEntity> sites) {
        int totalPages = sites.stream().mapToInt(pageRepository::countBySiteEntity).sum();
        double filterThreshold = 0.8;
        log.info("Общее количество страниц для всех выбранных сайтов (totalPages): " + totalPages);

        Map<String, Integer> filteredLemmas = lemmasFromQuery.entrySet().parallelStream()
                .filter(entry -> {
                String lemma = entry.getKey();
                int frequency = lemmaRepository.countByLemmaAndSites(lemma, sites);
                return frequency < (filterThreshold * totalPages);
            })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        log.info("Оставшиеся леммы после фильтрации: " + filteredLemmas.keySet());
        return filteredLemmas;
    }

    private List<LemmaEntity> getSortedLemmas(Set<String> lemmas,  List<SiteEntity> sites) {
        Optional<List<Integer>> siteIdsOptional = sites.isEmpty()
                ? Optional.empty()
                : Optional.of(sites.stream().map(SiteEntity::getId).collect(Collectors.toList()));

        List<LemmaEntity> lemmaEntities = siteIdsOptional
                .map(siteIds -> lemmaRepository.findLemmaEntityListByLemmasAndSites(new ArrayList<>(lemmas), siteIds))
                .orElseGet(() -> lemmaRepository.findLemmaEntityListByLemmasOnly(new ArrayList<>(lemmas)));

        if (lemmaEntities.isEmpty()) {
            log.info("Леммы не найдены для текущего набора сайтов и запроса.");
            return new ArrayList<>();
        }

        return lemmaEntities.stream()
                .sorted(Comparator.comparingInt(LemmaEntity::getFrequency))
                .collect(Collectors.toList());
    }

    private List<PageEntity> getRelevantPages(List<LemmaEntity> sortedLemmas) {
        if (sortedLemmas.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Integer, List<LemmaEntity>> lemmasBySite = sortedLemmas.stream()
                .collect(Collectors.groupingBy(lemma -> lemma.getSiteEntity().getId()));

        Set<PageEntity> relevantPages = new HashSet<>();

        for (Map.Entry<Integer, List<LemmaEntity>> entry : lemmasBySite.entrySet()) {
            List<LemmaEntity> siteLemmas = entry.getValue();

            int firstLemmaId = siteLemmas.get(0).getId();
            Set<Integer> pageIds = indexRepository.findPageEntityIdsByLemmaEntityId(firstLemmaId);

            log.info("Количество страниц до пересечений для сайта с ID " + entry.getKey() + ": " + pageIds.size());

            for (int i = 1; i < siteLemmas.size() && !pageIds.isEmpty(); i++) {
                Integer nextLemmaId = siteLemmas.get(i).getId();
                Set<Integer> nextPageIds = indexRepository.findPageEntityIdsByLemmaEntityId(nextLemmaId);
                pageIds.retainAll(nextPageIds);
            }

            relevantPages.addAll(pageRepository.findAllById(pageIds));
        }
        return new ArrayList<>(relevantPages);
    }

    private List<SearchResults.SearchStatistic> calculateRelevance(List<PageEntity> pages, List<LemmaEntity> lemmas) {
        DoubleAccumulator maxRelevance = new DoubleAccumulator(Double::max, 0.0);
        Map<PageEntity, Double> relevanceMap = new ConcurrentHashMap<>();
        List<Integer> lemmaIds = lemmas.stream().map(LemmaEntity::getId).toList();

        pages.forEach(page -> {
            double relevance = calculatePageRelevance(page, lemmaIds);
            relevanceMap.put(page, relevance);

            maxRelevance.accumulate(relevance);
        });

        return mapRelevanceToSearchStatistics(relevanceMap, maxRelevance.get(), lemmas);
    }

    private double calculatePageRelevance(PageEntity page, List<Integer> lemmaIds) {
        double relevance = 0;
        Pageable pageable = PageRequest.of(0, 100);

        Page<IndexEntity> pageResult;
        do {
            pageResult = indexRepository.findByPageIdAndLemmaIds(page.getId(), lemmaIds, pageable);

            relevance += pageResult.stream()
                    .mapToDouble(IndexEntity::getRank)
                    .sum();

            pageable = pageable.next();
        } while (pageResult.hasNext());

        return relevance;
    }

    private List<SearchResults.SearchStatistic> mapRelevanceToSearchStatistics(Map<PageEntity, Double> relevanceMap, double maxRelevance, List<LemmaEntity> lemmas) {
        return new ArrayList<>((relevanceMap.entrySet().parallelStream()
                .map(entry -> {
                    PageEntity pageEntity = entry.getKey();
                    double relativeRelevance = maxRelevance > 0 ? entry.getValue() / maxRelevance : 0;

                    String siteUrl = pageEntity.getSiteEntity().getUrl();
                    String siteName = pageEntity.getSiteEntity().getName();
                    String title = CleanHtmlCode.clear(pageEntity.getContent(), "title");
                    String snippet = generateSnippet(pageEntity, lemmas);

                    return new SearchResults.SearchStatistic(
                            siteUrl,
                            siteName,
                            pageEntity.getPath(),
                            title,
                            snippet,
                            relativeRelevance
                    );
                })
                .toList()));
    }

    private String generateSnippet(PageEntity pageEntity, List<LemmaEntity> lemmas) {
        String content = pageEntity.getContent();
        List<String> lemmaTexts = lemmas.stream().map(LemmaEntity::getLemma).toList();

        return morphology.generateSnippet(content, lemmaTexts);
    }

}
