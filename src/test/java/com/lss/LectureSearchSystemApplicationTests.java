package com.lss;

import com.lss.constant.PathConstant;
import com.lss.model.ChatDoc.ChatDocResponse;
import com.lss.service.LLMSegmenterService;
import com.lss.util.MarkdownProcessor;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootTest
class LectureSearchSystemApplicationTests {

    @Resource
    private LLMSegmenterService llmSegmenterService;

    /**
     * 测试大模型分词服务
     */
    @Test
    void LLMTest() throws IOException {
        Path markdownFilePath = Paths.get(PathConstant.DATA_PATH + "515993_大学生职业力赋能系列讲座之五职业生涯规划讲座报名开启.md");
        ChatDocResponse response = llmSegmenterService.segmentTextWithLlm(MarkdownProcessor.convertMarkdownToFullText(markdownFilePath));
        System.out.println("分词结果: " + response.toString());
    }

    @Test
    void MarkdownTest() throws IOException {
        Path markdownFilePath = Paths.get(PathConstant.DATA_PATH + "515993_大学生职业力赋能系列讲座之五职业生涯规划讲座报名开启.md");
        System.out.println(MarkdownProcessor.convertMarkdownToContent(markdownFilePath));
        System.out.println(MarkdownProcessor.convertMarkdownToTitle(markdownFilePath));
        System.out.println(MarkdownProcessor.convertMarkdownToFullText(markdownFilePath));

    }

}
