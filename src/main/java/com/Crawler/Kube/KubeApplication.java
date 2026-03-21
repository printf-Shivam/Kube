package com.Crawler.Kube;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;

@SpringBootApplication
public class KubeApplication {

	public static void main(String[] args) {

		SpringApplication.run(KubeApplication.class, args);
		CrawlerEngine crawler = new CrawlerEngine(List.of("https://en.wikipedia.org/wiki/Search_engine","https://github.com/" ));
		crawler.start();
	}

}
