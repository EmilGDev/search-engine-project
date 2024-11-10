package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;


    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics totalStatistics = new TotalStatistics();
        List<DetailedStatisticsItem> detailedStatisticsItems = createDetailedStatistics(totalStatistics);

        StatisticsData statisticsData = new StatisticsData();
        statisticsData.setTotal(totalStatistics);
        statisticsData.setDetailed(detailedStatisticsItems);

        return createStatisticsResponse(statisticsData);
    }

    private List<DetailedStatisticsItem> createDetailedStatistics(TotalStatistics totalStatistics) {
        List<DetailedStatisticsItem> detailedStatisticsItems = new ArrayList<>();
        List<SiteEntity> allSites = siteRepository.findAll();

        int totalPages = 0;
        int totalLemmas = 0;

        for (SiteEntity siteEntity : allSites) {
            DetailedStatisticsItem detailedItem = createDetailedStatisticsItem(siteEntity);
            detailedStatisticsItems.add(detailedItem);

            int pagesCount = detailedItem.getPages();
            int lemmasCount = detailedItem.getLemmas();
            totalPages += pagesCount;
            totalLemmas += lemmasCount;
        }

        updateTotalStatistics(totalStatistics, totalPages, totalLemmas, allSites.size(), allSites);
        return detailedStatisticsItems;
    }

    private DetailedStatisticsItem createDetailedStatisticsItem(SiteEntity siteEntity) {
        DetailedStatisticsItem detailedItem = new DetailedStatisticsItem();
        detailedItem.setUrl(siteEntity.getUrl());
        detailedItem.setName(siteEntity.getName());
        detailedItem.setStatus(siteEntity.getStatus().toString());
        detailedItem.setStatusTime(siteEntity.getStatusTime().toString());

        if (siteEntity.getStatus() == Status.FAILED) {
            detailedItem.setError(siteEntity.getLastError());
        } else {
            detailedItem.setError("");
        }

        int pagesCount = pageRepository.countBySiteEntity(siteEntity);
        int lemmasCount = lemmaRepository.countBySiteEntity(siteEntity);
        detailedItem.setPages(pagesCount);
        detailedItem.setLemmas(lemmasCount);

        return detailedItem;
    }

    private void updateTotalStatistics(TotalStatistics totalStatistics, int totalPages, int totalLemmas, int totalSites, List<SiteEntity> allSites) {
        totalStatistics.setSites(totalSites);
        totalStatistics.setPages(totalPages);
        totalStatistics.setLemmas(totalLemmas);
        totalStatistics.setIndexing(allSites.stream().anyMatch(site -> site.getStatus() == Status.INDEXING));
    }

    private StatisticsResponse createStatisticsResponse(StatisticsData statisticsData) {
        StatisticsResponse response = new StatisticsResponse();
        response.setResult(true);
        response.setStatistics(statisticsData);
        return response;
    }
}
