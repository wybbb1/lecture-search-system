// src/main/java/com/lss/controller/SearchController.java
package com.lss.controller;

import com.lss.model.Result;
import com.lss.service.QueryAdviceAssistant;
import com.lss.service.SearchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/search")
@Slf4j
public class SearchController {

    @Resource
    private SearchService searchService;

    /**
     * 处理搜索请求。
     * @param query 用户查询字符串
     * @param topN 返回结果数量，默认为10
     * @return 搜索结果的Mono<SearchResult>对象
     */
    @GetMapping()
    public Result performSearch(Integer type, String query, @RequestParam(value = "topN", defaultValue = "10") int topN) {
        log.info("Received search query: '{}', topN: {}", query, topN);
        return searchService.search(type, query, topN);
    }

    @GetMapping("/advice")
    public Result getSearchAdvice(@RequestParam String query) {
        log.info("Received search advice request for query: '{}'", query);
        return searchService.queryAdvice(query);
    }
}