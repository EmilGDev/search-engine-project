package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.util.Morphology;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class LemmaServiceImpl implements LemmaService{

    private final Morphology morphology;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final ConcurrentHashMap<String, ReentrantLock> lemmaLocks = new ConcurrentHashMap<>();

    @Override
    public void processTextAndSaveLemmas(String text, PageEntity pageEntity) {

        Map<String, Integer> lemmaFrequencyMap = morphology.getLemmaList(text);

        for (Map.Entry<String, Integer> entry : lemmaFrequencyMap.entrySet()) {
            String lemmaText = entry.getKey();
            Integer frequency = entry.getValue();

            saveOrUpdateLemma(lemmaText, frequency, pageEntity);
        }
    }

    private void saveOrUpdateLemma(String lemmaText, Integer frequency, PageEntity pageEntity) {

        ReentrantLock lock = lemmaLocks.computeIfAbsent(lemmaText, k -> new ReentrantLock());
        lock.lock();
        try {
            LemmaEntity lemma = lemmaRepository.findByLemmaAndSiteEntity(lemmaText, pageEntity.getSiteEntity())
                    .map(existingLemma -> {
                        existingLemma.setFrequency(existingLemma.getFrequency() + 1);
                        return existingLemma;
                    })
                    .orElseGet(() -> new LemmaEntity(pageEntity.getSiteEntity(), lemmaText, 1));

            lemmaRepository.saveAndFlush(lemma);
            saveIndex(lemma, pageEntity, frequency);
        } finally {
            lock.unlock();
        }
    }

    private void saveIndex(LemmaEntity lemmaEntity, PageEntity pageEntity, int frequency) {
        IndexEntity index = new IndexEntity();
        index.setLemmaEntity(lemmaEntity);
        index.setPageEntity(pageEntity);
        index.setRank(frequency);
        indexRepository.save(index);
    }
}
