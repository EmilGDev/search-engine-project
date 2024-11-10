package searchengine.parser;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import searchengine.dto.responses.PageResponse;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.lang.Thread.sleep;

@Slf4j
@Component
public class HtmlLinkParser {

    private final String userAgent;
    private final String referrer;

    public HtmlLinkParser(@Value("${indexing-settings.user-agent}") String userAgent,
                          @Value("${indexing-settings.referrer}") String referrer) {
        this.userAgent = userAgent;
        this.referrer = referrer;
    }

    private static final Set<String> FILE_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".pdf", ".eps",
            ".xlsx", ".doc", ".pptx", ".docx", ".zip", ".sql");

    private static boolean isNonHtmlResource(String link) {
        String lowerCaseLink = link.toLowerCase();
        return FILE_EXTENSIONS
                .stream()
                .anyMatch(lowerCaseLink::contains);
    }

    private boolean isValidHttpUrl(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }

    public ConcurrentLinkedQueue<String> parseLinks(String url) {
        ConcurrentLinkedQueue<String> links = new ConcurrentLinkedQueue<>();
        try {
            sleep(700);
            Document doc = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .referrer(referrer)
                    .timeout(5000)
                    .followRedirects(false)
                    .ignoreHttpErrors(true)
                    .get();

            Elements elements = doc.select("a[href]");

            for (Element el : elements) {
                String link = el.absUrl("href");
                if (isValidHttpUrl(link) && !isNonHtmlResource(link)) {
                    links.add(link);
                } else {
                    log.warn("Пропускаем недопустимый URL: {}", link);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Поток был прерван при обработке URL: {}. Ошибка: {}", url, e.getMessage());
        } catch (IOException e) {
            log.error("Ошибка при обработке URL: {}. Ошибка: {}", url, e.getMessage());
        }

        return links;
    }

    public PageResponse getPageResponse(String url) {
        if (!isValidHttpUrl(url)) {
            log.warn("Пропускаем недопустимый URL: {}", url);
            return new PageResponse(400, "Недопустимый URL");
        }

        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .referrer(referrer)
                    .timeout(5000)
                    .followRedirects(false)
                    .ignoreHttpErrors(true)
                    .execute();

            int statusCode = response.statusCode();
            String content = response.parse().html();

            return new PageResponse(statusCode, content);
        } catch (IOException e) {
            log.error("Ошибка при обработке URL: {}. Ошибка: {}", url, e.getMessage());
            return new PageResponse(400, "Ошибка при обработке URL");
        }
    }
}