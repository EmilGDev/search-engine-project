package searchengine.services;

import searchengine.model.PageEntity;

public interface LemmaService {
    void processTextAndSaveLemmas(String text, PageEntity page);
}
