package com.searchEngine.Kube;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KubeApplication {

	public static void main(String[] args) {
		SpringApplication.run(KubeApplication.class, args);
		System.out.println("server started");
	}

}
