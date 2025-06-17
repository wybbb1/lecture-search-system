package com.lss.util;


import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class MarkdownProcessor {
    /**
     * 读取Markdown文件并将其内容转换为纯文本。
     * @param markdownFilePath Markdown文件路径
     * @return 提取的纯文本内容
     * @throws IOException 如果文件读取失败
     */
    public static String convertMarkdownToContent(Path markdownFilePath) throws IOException {
        List<String> allLines = Files.readAllLines(markdownFilePath, Charset.forName("GBK"));
        if (allLines.isEmpty()) {
            return ""; // 文件为空
        }
        List<String> bodyLines = allLines.subList(1, allLines.size());
        return bodyLines.stream().collect(Collectors.joining(System.lineSeparator()));
    }

    public static String convertMarkdownToTitle(Path markdownFilePath) throws IOException {
        List<String> allLines = Files.readAllLines(markdownFilePath, Charset.forName("GBK"));
        if (allLines.isEmpty()) {
            return ""; // 文件为空
        }
        return allLines.getFirst();
    }

    public static String convertMarkdownToFullText(Path markdownFilePath) throws IOException {
        String text = Files.readString(markdownFilePath, Charset.forName("GBK"));
        if (text.isEmpty()) {
            return ""; // 文件为空
        }
        return text;
    }
}