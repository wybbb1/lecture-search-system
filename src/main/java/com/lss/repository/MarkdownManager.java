package com.lss.repository;

import com.lss.model.Index.LectureDocument;
import com.lss.util.MarkdownProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

@Component
@Slf4j
public class MarkdownManager {

    public String getContentByPath(Path path) throws IOException {
        return MarkdownProcessor.convertMarkdownToContent(path);
    }

}
