package searchengine.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class Morphology {

    private static RussianLuceneMorphology russianMorphology;
    private static EnglishLuceneMorphology englishMorphology;

    static {
        try {
            russianMorphology = new RussianLuceneMorphology();
            englishMorphology = new EnglishLuceneMorphology();
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    public Map<String, Integer> getLemmaList(String text) {
        Map<String, Integer> lemmaFrequencyMap = new HashMap<>();
        String[] words = text.split("\\s+");

        Arrays.stream(words).forEach(word ->
        {
            List<String> baseForms =  getLemmasForWord(word.toLowerCase());
            baseForms.forEach(baseForm ->
                    lemmaFrequencyMap.put(baseForm, lemmaFrequencyMap.getOrDefault(baseForm, 0) + 1));
        });
        return lemmaFrequencyMap;
    }

    private List<String> getLemmasForWord(String wordInLowerCase) {
        List<String> lemmas = new ArrayList<>();

        if (wordInLowerCase.matches(Constants.RUSSIAN_WORD_REGEX)) {
            lemmas.addAll(filterRussianServiceWords(russianMorphology.getNormalForms(wordInLowerCase), russianMorphology));
        } else if (wordInLowerCase.matches(Constants.ENGLISH_WORD_REGEX)) {
            lemmas.addAll(filterEnglishServiceWords(englishMorphology.getNormalForms(wordInLowerCase), englishMorphology));
        }
        return lemmas;
    }

    private Collection<String> filterRussianServiceWords(List<String> normalForms, RussianLuceneMorphology russianMorphology) {
        return normalForms.stream()
                .filter(lemma -> russianMorphology.getMorphInfo(lemma).stream().
                        noneMatch(info -> info.contains("ПРЕДЛ") || info.contains("СОЮЗ") || info.contains("МЕЖД") || info.contains("ЧАСТ")))
                .collect(Collectors.toList());
    }

    private Collection<String> filterEnglishServiceWords(List<String> normalForms, EnglishLuceneMorphology englishMorphology) {
        return normalForms.stream()
                .filter(lemma -> englishMorphology.getMorphInfo(lemma).stream()
                        .noneMatch(info -> info.contains("PREP") || info.contains("CONJ") || info.contains("ADV") || info.contains("ART")))
                .collect(Collectors.toList());
    }

    public String cleanHtmlTags(String htmlContent) {
        return Jsoup.parse(htmlContent).text();
    }


    public String generateSnippet(String content, List<String> lemmaTexts) {
        int snippetLength = 1500;

        String snippet = extractSnippet(content, lemmaTexts, snippetLength);

        if (snippet.isEmpty()) {
            return "";
        }

        return highlightSnippet(snippet, lemmaTexts);
    }

    public String extractSnippet(String content, List<String> lemmaTexts, int snippetLength) {
        String cleanContent = CleanHtmlCode.clear(content, "body");
        String lowerContent = cleanContent.toLowerCase();
        List<String> lowerLemmaTexts = lemmaTexts.stream().map(String::toLowerCase).toList();

        List<Integer> positions = new ArrayList<>();
        for (String lemma : lowerLemmaTexts) {
            int index = lowerContent.indexOf(lemma);
            while (index != -1) {
                positions.add(index);
                index = lowerContent.indexOf(lemma, index + 1); // Ищем следующее вхождение
            }
        }

        if (positions.isEmpty()) {
            return "";
        }

        int startPos = Collections.min(positions);
        int snippetStart = Math.max(0, startPos - snippetLength / 2);

        int snippetEnd = Math.min(cleanContent.length(), snippetStart + snippetLength);

        return cleanContent.substring(snippetStart, snippetEnd);
    }

    public String highlightSnippet(String snippet, List<String> lemmaTexts) {
        Set<String> lemmaSet = lemmaTexts.stream().map(String::toLowerCase).collect(Collectors.toSet());
        String[] words = snippet.split("\\s+");
        StringBuilder snippetBuilder = new StringBuilder();

        for (String word : words) {
            String wordLower = word.toLowerCase()
                    .replaceAll("[()\\[\\]{}]", "")
                    .replaceAll("[^a-zа-я0-9]", "");
            boolean isMatch = lemmaSet.stream().anyMatch(wordLower::contains);
            if (isMatch) { // Быстрая проверка через Set
                snippetBuilder.append("<b>").append(word).append("</b> ");
            } else {
                snippetBuilder.append(word).append(" ");
            }
        }

        if (snippet.startsWith(" ")) {
            snippetBuilder.insert(0, "... ");
        }
        if (snippet.endsWith(" ")) {
            snippetBuilder.append("...");
        }

        return snippetBuilder.toString().trim();
    }
}
