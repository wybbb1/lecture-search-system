// src/main/java/com/lss/controller/SearchController.java
package com.lss.controller;

import com.lss.model.Result;
import com.lss.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/search")
@Slf4j
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * 处理搜索请求。
     * @param query 用户查询字符串
     * @param topN 返回结果数量，默认为10
     * @return 搜索结果的Mono<SearchResult>对象
     */
    @GetMapping()
    public Result performSearch(String query, @RequestParam(value = "topN", defaultValue = "10") int topN) {
        log.info("Received search query: '{}', topN: {}", query, topN);
        return searchService.search(query, topN);
    }



    // 您也可以添加一个用于渲染HTML页面的Controller，或者纯前端项目则不需要
    // @GetMapping("/page")
    // public String searchPage() {
    //     return "search_page"; // 返回Thymeleaf模板名称
    // }
}