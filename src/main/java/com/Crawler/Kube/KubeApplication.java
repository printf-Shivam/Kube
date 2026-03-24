package com.Crawler.Kube;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;

@SpringBootApplication
public class KubeApplication {

	public static void main(String[] args) {

		SpringApplication.run(KubeApplication.class, args);
		List<String> seeds = List.of(
				"https://portswigger.net/web-security/all-materials",
				"https://owasp.org/www-community/attacks/",
				"https://www.baeldung.com/java-concurrency",
				"https://developer.salesforce.com/docs/atlas.en-us.secure_coding_guide.meta/secure_coding_guide/secure_coding_sql_injection.htm"
		);

		CrawlerEngine engine = new CrawlerEngine(seeds);
		engine.start();
	}

}
