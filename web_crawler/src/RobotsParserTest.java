public class RobotsParserTest {
    public static void main(String[] args) {

        RobotsParser parser = new RobotsParser();

        // GitHub disallows several paths
        System.out.println("GitHub /login : " + parser.isAllowed("github.com", "/login"));
        System.out.println("GitHub / : " + parser.isAllowed("github.com", "/"));

        // Wikipedia is mostly open
        System.out.println("Wikipedia / : " + parser.isAllowed("en.wikipedia.org", "/"));
        System.out.println("Wikipedia /wiki/Main_Page : " + parser.isAllowed("en.wikipedia.org", "/wiki/Main_Page"));

        // Reddit disallows a lot
        System.out.println("Reddit / : " + parser.isAllowed("www.reddit.com", "/"));
        System.out.println("Reddit /r/programming : " + parser.isAllowed("www.reddit.com", "/r/programming"));
    }
}