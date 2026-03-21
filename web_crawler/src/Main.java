import java.util.List;

public class Main {
    public static void main(String[] args) throws InterruptedException {

        CrawlerEngine crawler = new CrawlerEngine(List.of("https://en.wikipedia.org/wiki/Search_engine","https://github.com/" ));
        crawler.start();
    }
}