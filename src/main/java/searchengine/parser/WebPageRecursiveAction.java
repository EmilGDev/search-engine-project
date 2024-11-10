package searchengine.parser;

import lombok.extern.slf4j.Slf4j;
import searchengine.dto.responses.IndexingResponse;
import searchengine.dto.responses.PageResponse;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.IndexingServiceImpl;
import searchengine.services.LemmaService;
import searchengine.util.Morphology;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class WebPageRecursiveAction extends RecursiveAction {

    private final SiteEntity siteEntity;

    private final Morphology morphology;
    private final WebPageNode webPageNode;
    private final ReentrantLock lemmaLock;
    private final ReentrantLock siteEntityLock;
    private final LemmaService lemmaService;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final HtmlLinkParser htmlLinkParser;
    private final IndexingServiceImpl indexingService;
    private final ConcurrentHashMap<String, Boolean> visitedLinksMap;

    public WebPageRecursiveAction(SiteEntity siteEntity,
                                  Morphology morphology,
                                  WebPageNode webPageNode,
                                  ReentrantLock lemmaLock,
                                  ReentrantLock siteEntityLock,
                                  LemmaService lemmaService,
                                  PageRepository pageRepository,
                                  SiteRepository siteRepository,
                                  HtmlLinkParser htmlLinkParser,
                                  IndexingServiceImpl indexingService,
                                  ConcurrentHashMap<String, Boolean> visitedLinksMap) {
        this.siteEntity = siteEntity;
        this.morphology = morphology;
        this.webPageNode = webPageNode;
        this.lemmaService = lemmaService;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.htmlLinkParser = htmlLinkParser;
        this.siteEntityLock = siteEntityLock;
        this.lemmaLock = lemmaLock;
        this.indexingService = indexingService;
        this.visitedLinksMap = visitedLinksMap;
    }

    @Override
    protected void compute() {
        if (shouldStopIndexing()) return;
        if (hasPreviousError()) return;

        try {
            if (shouldStopIndexingBeforeProcessing()) return;

            visitedLinksMap.putIfAbsent(webPageNode.getUrl(), true);
            Set<String> links = new ConcurrentSkipListSet<>(htmlLinkParser.parseLinks(webPageNode.getUrl()));
            processLinksAndSavePages(links);

            if (shouldStopIndexingBeforeFork()) return;

            forkAndJoinTasks();

            log.info("Обрабатываем URL: " + webPageNode.getUrl());
        } catch (Exception e) {
            siteEntityLock.lock();
            try {
                log.error("Ошибка при индексации страницы: ", e);
                siteEntity.setStatus(Status.FAILED);
                siteEntity.setLastError("Ошибка при индексации страницы: " + e.getMessage());
                siteEntity.setStatusTime(LocalDateTime.now());
                siteRepository.saveAndFlush(siteEntity);
            } finally {
                siteEntityLock.unlock();
            }
        }
    }

    private boolean shouldStopIndexing() {
        if (indexingService.isStopping()) {
            log.info("Индексация остановлена пользователем для URL: {}", webPageNode.getUrl());
            return true;
        }
        return false;
    }

    private boolean hasPreviousError() {
        if (siteEntity.getStatus() == Status.FAILED) {
            log.info("Индексация остановлена для сайта {} из-за предыдущей ошибки.", siteEntity.getUrl());
            return true;
        }
        return false;
    }

    private boolean shouldStopIndexingBeforeProcessing() {
        if (indexingService.isStopping()) {
            log.info("Индексация остановлена пользователем для URL перед процессингом: {}", webPageNode.getUrl());
            return true;
        }
        return false;
    }

    private boolean shouldStopIndexingBeforeFork() {
        if (indexingService.isStopping()) {
            log.info("Индексация остановлена пользователем перед форком задач для URL: {}", webPageNode.getUrl());
            return true;
        }
        return false;
    }

    private boolean isLinkNotVisited(String link) {
        return visitedLinksMap.putIfAbsent(link, true) == null;
    }

    private boolean isLinkNotIndexed(String link) {
        return !pageRepository.existsByPath(extractRelativePath(link));
    }

    private void forkAndJoinTasks() {
        if (shouldStopIndexingBeforeFork()) return;
        List<WebPageRecursiveAction> taskList = new ArrayList<>();
        for (WebPageNode child : webPageNode.getChildrenList()) {
            WebPageRecursiveAction task = new WebPageRecursiveAction(
                    siteEntity,
                    morphology,
                    child,
                    lemmaLock,
                    siteEntityLock,
                    lemmaService,
                    pageRepository,
                    siteRepository,
                    htmlLinkParser,
                    indexingService,
                    visitedLinksMap);
            task.fork();
            taskList.add(task);
        }

        if (shouldStopIndexingBeforeFork()) return;

        for (WebPageRecursiveAction task : taskList) {
            try {
                task.join();
            } catch (CancellationException e) {
                log.info("Задача была отменена для подзадачи URL: {}", task.webPageNode.getUrl());
            } catch (Exception e) {
                log.error("Ошибка при выполнении подзадачи для URL: {}", task.webPageNode.getUrl(), e);
            }
        }
    }

    private String extractRelativePath(String url) {
        try {
            URI uri = new URI(url);
            return uri.getPath();
        } catch (URISyntaxException e) {
            log.error("Некорректный URL: {}", url, e);
            return "/";
        }
    }

    private void processLinksAndSavePages(Set<String> links) {

        shouldStopIndexingBeforeProcessing();

        List<PageEntity> pageEntities = new ArrayList<>();
        int batchSize = 50;

        links.stream()
                .filter(this::isLinkNotIndexed)
                .filter(this::isLinkNotVisited)
                .forEach(link -> processSingleLink(link, pageEntities, batchSize));

        saveRemainingPages(pageEntities);

        if (indexingService.isStopping()) {
            log.info("Индексация остановлена пользователем перед лемматизацией для URL: {}", webPageNode.getUrl());
            return;
        }

        updateSiteStatusTime();

        lemmaLock.lock();
        try {
            processLemmasForSavedPages(pageEntities);
        } finally {
            lemmaLock.unlock();
        }
    }

    private void processSingleLink(String link, List<PageEntity> pageEntities, int batchSize) {

        shouldStopIndexingBeforeProcessing();

        WebPageNode childNode = new WebPageNode(link);
        webPageNode.addChild(childNode);

        PageResponse pageResponse = htmlLinkParser.getPageResponse(link);
        log.info("Статус для URL " + link + ": " + pageResponse.getStatusCode());

        String relativePath = extractRelativePath(link);
        PageEntity pageEntity = createPageEntity(relativePath, pageResponse);
        pageEntities.add(pageEntity);

        if (pageEntities.size() == batchSize) {
            saveBatchPages(pageEntities);

            lemmaLock.lock();
            try {
                processLemmasForSavedPages(pageEntities);
            } finally {
                lemmaLock.unlock();
            }
        }
    }

    private void processLemmasForSavedPages(List<PageEntity> pageEntities) {
        for (PageEntity pageEntity : pageEntities) {
            if (pageEntity.getCode() == 200) {
                String cleanedText = morphology.cleanHtmlTags(pageEntity.getContent());
                lemmaService.processTextAndSaveLemmas(cleanedText, pageEntity);
            }
        }
    }

    private PageEntity createPageEntity(String relativePath, PageResponse pageResponse) {
        PageEntity pageEntity = new PageEntity();
        pageEntity.setSiteEntity(siteEntity);
        pageEntity.setPath(relativePath);
        pageEntity.setCode(pageResponse.getStatusCode());
        pageEntity.setContent(pageResponse.getContent());
        return pageEntity;
    }

    private void saveBatchPages(List<PageEntity> pageEntities) {
        pageRepository.saveAll(pageEntities);
        pageEntities.clear();
    }

    private void saveRemainingPages(List<PageEntity> pageEntities) {
        if (!pageEntities.isEmpty()) {
            pageRepository.saveAll(pageEntities);
        }
    }

    private void updateSiteStatusTime() {
        try {
            siteEntityLock.lock();
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);
            log.info("Обновлено status_time для сайта: {}", siteEntity.getUrl());
        } finally {
            if (siteEntityLock.isHeldByCurrentThread()) {
                siteEntityLock.unlock();
            }
        }
    }
}













