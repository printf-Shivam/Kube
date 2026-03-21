import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class CrawlerEngine{

	private  final int MAX_THREAD =20;
	private  final long HOST_DELAY = 1000;
	private  final int MAX_PAGES = 100;

	private  ExecutorService executor = Executors.newFixedThreadPool(MAX_THREAD);
	private  Set<String> visited = ConcurrentHashMap.newKeySet();

	private  ConcurrentHashMap<String,BlockingQueue<String>> hostQueue = new ConcurrentHashMap<>();
	private  Set<String> activeHosts = ConcurrentHashMap.newKeySet();

	private  ConcurrentHashMap<String, Long> hostLastAccess = new ConcurrentHashMap<>();
	private  ConcurrentHashMap<String, Integer> hostPageCount= new ConcurrentHashMap<>();

	private final RobotsParser robotsParser = new RobotsParser();

	public CrawlerEngine(List<String> seedList) {
		for(String seed: seedList)
		addURL(seed);
	}

	public void start() {
		startDispatcher();
	
		while (!isCrawlComplete()) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				break;
			}
		}
	
		executor.shutdown();
		try {
			executor.awaitTermination(1, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	private boolean isCrawlComplete() {
		if (!activeHosts.isEmpty()) return false;
	
		for (BlockingQueue<String> queue : hostQueue.values()) {
			if (!queue.isEmpty()) return false;
		}
	
		return true;
	}
	
	private void startDispatcher() {
	Thread dispatcher = new Thread(()->{
		while (true) { 
			boolean dispatched = false;

			for(String host : hostQueue.keySet()){
				if(activeHosts.contains(host)) continue;

				long currentTime = System.currentTimeMillis();
				long lastAccess = hostLastAccess.getOrDefault(host, 0L);
				
				if(currentTime - lastAccess < HOST_DELAY) continue;

				if(!canCrawl(host)) continue;

				BlockingQueue<String> queue = hostQueue.get(host);
				
				if(queue == null || queue.isEmpty()) continue;

				String url = queue.poll();
				if(url == null) continue;

				if(!visited.add(url)) continue;

				activeHosts.add(host);
				hostLastAccess.put(host, System.currentTimeMillis());
				executor.execute(()-> processUrl(host, url));
				dispatched = true;
			}
			if(!dispatched){
				try {
					Thread.sleep(50);
				} catch (Exception e) {
					return;
				}
			}
		}
	});
	dispatcher.setDaemon(true);
	dispatcher.start();
}
	
	private void processUrl(String host, String url){
		try {
			Document doc = request(url);
			if(doc == null) return;

			incrementHostCount(host);

			for(Element link : doc.select("a[href]")){
				addURL(link.absUrl("href"));
			}

		} catch (Exception e) {
		}
		finally{
			activeHosts.remove(host);
		}
	}
	
	private void addURL(String seed){
		String url = canonicalize(seed);

		if(url == null) return ;


		String host = getHost(url);
		if(host == null) return;

		if(!canCrawl(host)) return;

		String path = getPath(url);
		if(!robotsParser.isAllowed(host, path)) return;

		hostQueue.computeIfAbsent(host, h -> new LinkedBlockingQueue<>()).offer(url);
	}

	private String getPath(String url) {
		try {
			return new URL(url).getPath();
		} catch (Exception e) {
			return "/";
		}
	}

	private Document request(String url) {
		try {
			Connection con = Jsoup.connect(url).timeout(5000).ignoreHttpErrors(true);
			Document doc = con.get();
			
			if(con.response().statusCode()==200) {
				System.out.println(Thread.currentThread().getName()+" visited: "+url);
			}
			return doc;
		}
		
		catch(IOException e) {
			System.err.println("error in request method");
			return null;
		}
	}

	private String canonicalize(String link){
		try {
			if(link == null || link.isEmpty()) return null;

			URL url = new URL(link);
			String protocol = url.getProtocol();
			if(!protocol.equals("https") && !protocol.equals("http")) 
				return null;

			String host = url.getHost().toLowerCase();
			String path = url.getPath();
			
			if(path==null || path.isEmpty())
				path = "/";
			return protocol + "://" + host + path;

		} catch (Exception e) {
			System.err.println("error in canonicalization method");
			return null;
		}
	}
	
	private String getHost(String url){
		try {
			return new URL(url).getHost().toLowerCase();
		} catch (Exception e) {
			System.err.println("error in fetching host");
			return null;
		}
	}

	private boolean canCrawl(String host){
		return hostPageCount.getOrDefault(host, 0) < MAX_PAGES;
	}

	private void incrementHostCount(String host){
		hostPageCount.merge(host,1,Integer::sum);
	}
}
