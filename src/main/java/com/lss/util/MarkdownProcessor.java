package com.lss.util;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class MarkdownProcessor {

    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownProcessor() {
        MutableDataSet options = new MutableDataSet();
        this.parser = Parser.builder(options).build();
        this.renderer = HtmlRenderer.builder(options).build();
    }

    /**
     * 读取Markdown文件并将其内容转换为纯文本。
     * @param markdownFilePath Markdown文件路径
     * @return 提取的纯文本内容
     * @throws IOException 如果文件读取失败
     */
    public String convertMarkdownToPlainText(Path markdownFilePath) throws IOException {
        String markdownContent = Files.readString(markdownFilePath);
        return renderer.render(parser.parse(markdownContent));
    }
}