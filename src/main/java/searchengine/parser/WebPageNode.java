package searchengine.parser;

import java.util.concurrent.ConcurrentLinkedQueue;

public class WebPageNode {
    private String url;
    private ConcurrentLinkedQueue<WebPageNode> childrenList;

    public WebPageNode(String url) {
        this.url = url;
        childrenList = new ConcurrentLinkedQueue<>();
    }

    public void addChild(WebPageNode child) {
        childrenList.add(child);
    }

    public ConcurrentLinkedQueue<WebPageNode> getChildrenList() {
        return childrenList;
    }

    public String getUrl() {
        return url;
    }
}
