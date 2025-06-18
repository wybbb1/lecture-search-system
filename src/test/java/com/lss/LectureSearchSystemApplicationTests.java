package com.lss;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.lss.constant.PathConstant;
import com.lss.model.Index.InvertedIndex;
import com.lss.model.Index.LectureDocument;
import com.lss.model.Index.Posting;
import com.lss.model.Result;
import com.lss.model.RetrieveDocsItems;
import com.lss.repository.InvertedIndexManager;
import com.lss.service.LLMSegmenterService;
import com.lss.service.SearchService;
import com.lss.util.MarkdownProcessor;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@SpringBootTest
class LectureSearchSystemApplicationTests {

    @Resource
    private LLMSegmenterService llmSegmenterService;
    @Resource
    private InvertedIndexManager invertedIndexManager;
    @Resource
    private SearchService searchService;

    /**
     * 测试大模型分词服务
     */
    @Test
    void LLMTest() throws IOException {
        Path markdownFilePath = Paths.get(PathConstant.DATA_PATH + "515993_大学生职业力赋能系列讲座之五职业生涯规划讲座报名开启.md");
        //ChatDocResponse response = llmSegmenterService.segmentTextWithLlm(MarkdownProcessor.convertMarkdownToFullText(markdownFilePath));
        //System.out.println("分词结果: " + response.toString());
    }

    @Test
    void MarkdownTest() throws IOException {
        Path markdownFilePath = Paths.get(PathConstant.DATA_PATH + "515993_大学生职业力赋能系列讲座之五职业生涯规划讲座报名开启.md");
        System.out.println(MarkdownProcessor.convertMarkdownToContent(markdownFilePath));
        System.out.println(MarkdownProcessor.convertMarkdownToTitle(markdownFilePath));
        System.out.println(MarkdownProcessor.convertMarkdownToFullText(markdownFilePath));

    }

    @Test
    void InvertedIndexTest() {
        InvertedIndex invertedIndex = invertedIndexManager.getInvertedIndex();
        Map<String, List<Posting>> dictionary = invertedIndex.getDictionary();
        for (Map.Entry<String, List<Posting>> entry : dictionary.entrySet()) {
            String term = entry.getKey();
            List<Posting> postings = entry.getValue();
            System.out.println("Term: " + term);
            for (Posting posting : postings) {
                System.out.println("  Document ID: " + posting.getDocumentId() + ", Frequency: " + posting.getTermFrequency() + ", Positions: " + posting.getPositions());
            }
        }
    }

    @Test
    void getDocumentByIdTest(){
        LectureDocument document = invertedIndexManager.getDocumentById("515993");
        System.out.println(document.getOriginalFilePath());
    }

    @Test
    void searchTest(){
        Result result = searchService.search("民族宗教", 10);
        System.out.println(result.toString());
    }

    @Test
    void fileTest(){
        try (BufferedInputStream BIS = new BufferedInputStream(new FileInputStream(PathConstant.TopN_Content));
        ){
            if (BIS.available() > 0) {
                // 清空文件内容
                new PrintWriter(new FileOutputStream(PathConstant.TopN_Content, false)).close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void jiebaTest(){
        JiebaSegmenter segmenter = new JiebaSegmenter();
        String sentence2 = "小明硕士毕业于中国科学院计算所，后在日本京都大学深造";
        List<String> queryTerms = segmenter.sentenceProcess(sentence2);
        System.out.println(queryTerms);
    }
}
