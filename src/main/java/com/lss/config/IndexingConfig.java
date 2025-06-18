package com.lss.config;

import com.lss.constant.PathConstant;
import com.lss.repository.InvertedIndexManager;
import com.lss.service.IndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration // 标记为配置类
@Slf4j
public class IndexingConfig {

    // 配置一个固定大小的线程池，用于异步调用AI
    private static final int THREAD_POOL_SIZE = 10; // 例如，同时处理5个文档

    @Bean(destroyMethod = "shutdown") // 确保Spring在应用关闭时优雅地关闭线程池
    public ExecutorService indexingThreadPool() {
        return Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    }
    /**
     * 定义一个CommandLineRunner Bean，在Spring Boot应用启动后执行索引初始化。
     *
     * @param indexService 注入的IndexService
     * @param invertedIndexManager 注入的InvertedIndexManager
     * @return CommandLineRunner实例
     */
    @Bean
    public CommandLineRunner indexInitializationRunner(IndexService indexService, InvertedIndexManager invertedIndexManager, ExecutorService indexingThreadPool) {
        return args -> {
            log.info("Starting index initialization from IndexingConfig...");

            // 1. 在应用启动时，首先尝试加载已持久化的索引
            invertedIndexManager.loadIndex();

            // 2. 准备需要索引的Markdown文件路径
            Path dataDirectory = Paths.get(PathConstant.MD_Path); // 假设Markdown文档在此目录下
            List<Path> markdownFiles;
            if (Files.exists(dataDirectory) && Files.isDirectory(dataDirectory)) {
                try (Stream<Path> paths = Files.walk(dataDirectory)) {
                    markdownFiles = paths
                            .filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".md"))
                            .collect(Collectors.toList());

                    if (markdownFiles.isEmpty()) {
                        log.warn("No Markdown files found in {}. Indexing will be skipped.", dataDirectory.toAbsolutePath());
                    } else {
                        log.info("Found {} Markdown files for indexing.", markdownFiles.size());
                    }
                } catch (IOException e) {
                    log.error("Error walking data directory: {}. Cannot build index.", e.getMessage());
                    return; // 如果无法读取文件，直接返回，避免后续异常
                }
            } else {
                log.error("Data directory not found or is not a directory: {}. Please ensure it exists.", dataDirectory.toAbsolutePath());
                return; // 目录不存在，无法进行索引构建
            }

            // 3. 调用 IndexService 进行索引构建，并决定是否强制重建
            indexService.buildInitialIndex(markdownFiles, false, indexingThreadPool);

            log.info("Index initialization completed in IndexingConfig.");
        };
    }
}