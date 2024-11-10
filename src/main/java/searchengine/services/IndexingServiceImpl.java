package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.responses.IndexingResponse;
import searchengine.dto.responses.PageResponse;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.parser.HtmlLinkParser;
import searchengine.parser.WebPageNode;
import searchengine.parser.WebPageRecursiveAction;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.util.Morphology;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class IndexingServiceImpl implements IndexingService {

    private ForkJoinPool forkJoinPool;
    private final SitesList sitesList;
    private final Morphology morphology;
    private final LemmaService lemmaService;
    private final HtmlLinkParser htmlLinkParser;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;
    private volatile boolean isStopping = false;
    private final ReentrantLock stopLock = new ReentrantLock();
    private final ReentrantLock lemmaLock = new ReentrantLock();
    private final ReentrantLock siteEntityLock = new ReentrantLock();
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    public IndexingServiceImpl(SitesList sitesList,
                               Morphology morphology,
                               LemmaService lemmaService,
                               SiteRepository siteRepository,
                               PageRepository pageRepository,
                               IndexRepository indexRepository,
                               LemmaRepository lemmaRepository,
                               HtmlLinkParser htmlLinkParser) {
        this.sitesList = sitesList;
        this.morphology = morphology;
        this.lemmaService = lemmaService;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.indexRepository = indexRepository;
        this.lemmaRepository = lemmaRepository;
        this.htmlLinkParser = htmlLinkParser;

        int poolParallelism = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        this.forkJoinPool = new ForkJoinPool(poolParallelism);
        log.info("Создан ForkJoinPool с количеством потоков: " + poolParallelism);
    }

    @Override
    public IndexingResponse startIndexing() {
        if (isIndexingInProgress()) {
            return new IndexingResponse(false, "\"Индексация уже запущена\"");
        }

        isStopping = false;

        if (executorService.isShutdown() || executorService.isTerminated()) {
            executorService = Executors.newSingleThreadExecutor();
        }

        if (forkJoinPool.isShutdown() || forkJoinPool.isTerminated()) {
            int poolParallelism = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
            forkJoinPool = new ForkJoinPool(poolParallelism);
            log.info("Создан новый ForkJoinPool с количеством потоков: " + poolParallelism);
        }

        log.info("ExecutorService статус: завершен? {}. ForkJoinPool статус: завершен? {}",
                executorService.isShutdown(), forkJoinPool.isShutdown());

        executorService.submit(this::performAsyncIndexing);

        return new IndexingResponse(true);
    }

    public void performAsyncIndexing() {
        try {
            for (Site siteConfig : sitesList.getSites()) {
                log.info("Запуск индексации для сайта: " + siteConfig.getUrl());

                ConcurrentHashMap<String, Boolean> visitedLinksMap = new ConcurrentHashMap<>();
                indexSite(siteConfig, visitedLinksMap);
            }

            forkJoinPool.awaitQuiescence(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

            if (!isStopping) {
                updateAllSitesStatus(Status.INDEXED, null);
            } else {
                log.info("Индексация была остановлена пользователем, не устанавливаем статус INDEXED");
            }

        } catch (Exception e) {
            Thread.currentThread().interrupt();
            log.error("Ошибка при завершении ForkJoinPool", e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void indexSite(Site siteConfig, ConcurrentHashMap<String, Boolean> visitedLinksMap) {
        SiteEntity siteEntity = new SiteEntity();
        try {

            clearOldSiteData(siteConfig);
            siteEntity = initializeSiteEntity(siteConfig, Status.INDEXING);
            startSiteIndexing(siteConfig, siteEntity, visitedLinksMap);

        } catch (Exception e) {
            String levelMessage = "Ошибка на уровне индексации сайта: ";
            log.error(levelMessage + e.getMessage());
            updateSiteStatusInCatch(siteEntity, levelMessage, e);
        }
    }

    private void clearOldSiteData(Site siteConfig) {
        siteRepository.deleteSiteByUrl(siteConfig.getUrl());
    }

    private SiteEntity initializeSiteEntity(Site siteConfig, Status status) {
        SiteEntity newSiteEntity = new SiteEntity();
        newSiteEntity.setUrl(siteConfig.getUrl());
        newSiteEntity.setName(siteConfig.getName());
        newSiteEntity.setStatus(status);
        newSiteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(newSiteEntity);
        return newSiteEntity;
    }

    void startSiteIndexing(Site siteConfig, SiteEntity siteEntity,
                           ConcurrentHashMap<String, Boolean> visitedLinksMap) {
        WebPageNode rootNode = new WebPageNode(siteConfig.getUrl());
        forkJoinPool.submit(new WebPageRecursiveAction(
                siteEntity,
                morphology,
                rootNode,
                lemmaLock,
                siteEntityLock,
                lemmaService,
                pageRepository,
                siteRepository,
                htmlLinkParser,
                this,
                visitedLinksMap));
    }
    
    @Override
    public IndexingResponse stopIndexing() {
        stopLock.lock();
        try {
            if (!isIndexingInProgress()) {
                return new IndexingResponse(false, "Индексация не запущена");
            }

            isStopping = true;
            log.info("Запрос на завершение всех задач...");
            waitForTaskCompetition();

            forkJoinPool.shutdown();
            if (!forkJoinPool.awaitQuiescence(3, TimeUnit.MINUTES)) {
                log.warn("ForkJoinPool не завершил свою работу за 3 минуты, принудительная остановка.");
                forkJoinPool.shutdownNow();
            }

            executorService.shutdown();
            if (!executorService.awaitTermination(3, TimeUnit.MINUTES)) {
                log.warn("ExecutorService не завершил свою работу за 3 минуты, принудительная остановка.");
                executorService.shutdownNow();
            }

            forkJoinPool.shutdownNow();
            executorService.shutdownNow();
            updateAllSitesStatus(Status.FAILED, "Индексация остановлена пользователем");
            return new IndexingResponse(true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Ошибка при завершении потоков", e);
            return new IndexingResponse(false, "Ошибка при завершении потоков");
        } finally {
            stopLock.unlock();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void updateAllSitesStatus(Status status, String lastError) {
        List<SiteEntity> indexingSites = siteRepository.findAllByStatus(Status.INDEXING);
        indexingSites.forEach(siteEntity -> {
            siteEntity.setStatus(status);
            siteEntity.setStatusTime(LocalDateTime.now());
            if (status == Status.FAILED && lastError != null) {
                siteEntity.setLastError(lastError);
            }
        });
        siteRepository.saveAll(indexingSites);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IndexingResponse indexPage(String url) {
        Optional<Site> optionalSite = getSiteFromConfig(url);
        if (optionalSite.isEmpty()) {
            return new IndexingResponse(false,
                    "Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
        }

        SiteEntity siteEntity = getOrInitializeSiteEntity(optionalSite.get());

        try {
            cleanUpExistingPage(url, siteEntity);

            PageResponse pageResponse = htmlLinkParser.getPageResponse(url);
            int code = pageResponse.getStatusCode();

            if (code != 200) {
                return saveErrorPage(siteEntity, url, code);
            }

            savePageAndProcessLemmas(url, siteEntity, pageResponse);

            return new IndexingResponse(true);
        } catch (Exception e) {
            String levelMessage = "Ошибка при индексации страницы: ";
            log.error(levelMessage + e.getMessage());
            updateSiteStatusInCatch(siteEntity, levelMessage, e);
            return new IndexingResponse(false, levelMessage + e.getMessage());
        }
    }

    private SiteEntity getOrInitializeSiteEntity(Site siteConfig) {
        return siteRepository.findByUrl(siteConfig.getUrl())
                .orElseGet(() -> {
                    Optional<SiteEntity> doubleCheckSiteEntity = siteRepository.findByUrl(siteConfig.getUrl());
                    return doubleCheckSiteEntity.orElseGet(() -> initializeSiteEntity(siteConfig, Status.INDEXED));
                });
    }

    private void cleanUpExistingPage(String url, SiteEntity siteEntity) {
        Optional<PageEntity> existingPage = pageRepository.findByPathAndSiteEntity(url, siteEntity);
        existingPage.ifPresent(page -> {
            indexRepository.decrementLemmaFrequencyByPage(page.getId());
            pageRepository.delete(page);
            lemmaRepository.deleteUnusedLemmas();
        });
    }

    private void savePageAndProcessLemmas(String url, SiteEntity siteEntity, PageResponse pageResponse) {
        String htmlContent = pageResponse.getContent();
        String cleanedText = morphology.cleanHtmlTags(htmlContent);

        PageEntity newPageEntity = createPageEntity(siteEntity, url, htmlContent, pageResponse.getStatusCode());
        pageRepository.save(newPageEntity);

        lemmaService.processTextAndSaveLemmas(cleanedText, newPageEntity);
    }

    private void updateSiteStatusInCatch(SiteEntity siteEntity, String message, Exception e) {
        siteEntity.setStatus(Status.FAILED);
        siteEntity.setLastError(message + e.getMessage());
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);
    }
    
    private Optional<Site> getSiteFromConfig(String url) {
        log.info("Проверка URL: {}", url);
        return sitesList.getSites().stream()
                .filter(site -> url.startsWith(site.getUrl()))
                .findFirst();
    }

    private IndexingResponse saveErrorPage(SiteEntity siteEntity, String url, int code) {
        PageEntity errorPageEntity = createPageEntity(siteEntity, url, "", code);
        pageRepository.save(errorPageEntity);

        return new IndexingResponse(false,
                "Не удалось индексировать страницу, код ответа: " + code);
    }

    private PageEntity createPageEntity(SiteEntity siteEntity, String url, String content, int code) {
        PageEntity pageEntity = new PageEntity();
        pageEntity.setSiteEntity(siteEntity);
        pageEntity.setPath(url);
        pageEntity.setContent(content);
        pageEntity.setCode(code);
        return pageEntity;
    }

    private void waitForTaskCompetition() throws InterruptedException {
        int retries = 0;
        while (retries < 30) {
            if (forkJoinPool.isQuiescent()) {
                log.info("Все задачи в ForkJoinPool завершены");
                break;
            }
            Thread.sleep(1000);
            retries++;
        }
    }

    private boolean isIndexingInProgress() {
        return siteRepository.existsByStatus(Status.INDEXING);
    }

    public boolean isStopping() {
        return isStopping;
    }
}