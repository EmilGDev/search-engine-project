package searchengine.util;

import lombok.experimental.UtilityClass;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

@UtilityClass
public class CleanHtmlCode {
    public static String clear(String content, String... selectors) {
        Document doc = Jsoup.parse(content);
        StringBuilder text = new StringBuilder();

        for (String selector : selectors) {
            Elements elements = doc.select(selector);
            for (Element el : elements) {
                text.append(el.text()).append(" ");
            }
        }
        return text.toString().trim();
    }
}
