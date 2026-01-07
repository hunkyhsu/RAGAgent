package com.hunkyhsu.ragagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.hunkyhsu.ragagent.repository")
@EntityScan(basePackages = "com.hunkyhsu.ragagent.entity")
public class RagagentApplication {

	public static void main(String[] args) {
		SpringApplication.run(RagagentApplication.class, args);
	}

}
