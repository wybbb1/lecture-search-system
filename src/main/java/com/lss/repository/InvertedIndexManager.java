package com.lss.repository;

import com.lss.constant.PathConstant;
import com.lss.model.InvertedIndex;
import com.lss.model.LectureDocument;
import com.lss.model.Posting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class InvertedIndexManager {

    // 使用volatile确保多线程下对invertedIndex的可见性
    private volatile InvertedIndex invertedIndex;
    // 使用PathConstant来定义索引文件路径
    private final Path indexPath = Paths.get(PathConstant.Inverted_Index);

    // 使用AtomicBoolean来确保索引只加载/初始化一次
    private final AtomicBoolean indexLoaded = new AtomicBoolean(false);

    public InvertedIndexManager() {
        // 构造函数只负责初始化空的索引对象，不在此处进行加载
        // 实际的加载操作将由外部（如CommandLineRunner）显式调用loadIndex()方法触发
        this.invertedIndex = new InvertedIndex();
    }

    /**
     * 获取当前的倒排索引实例。
     * 在调用此方法前，应确保索引已加载或构建。
     * @return InvertedIndex实例
     * @throws IllegalStateException 如果索引尚未加载或构建
     */
    public InvertedIndex getInvertedIndex() {
        if (!indexLoaded.get()) {
            // 如果索引未加载，抛出异常或返回null，取决于期望行为。
            // 推荐在应用启动时就确保加载，这样业务逻辑调用时不会出现未加载的情况。
            throw new IllegalStateException("倒排索引未初始化");
        }
        return invertedIndex;
    }

    /**
     * 添加文档到倒排索引。
     * 这个方法会被 IndexService 调用。
     * @param document 文档对象
     * @param termsInDocument 文档分词后的词项列表
     * @param fieldType 词项所属的域
     */
    public void addDocumentToIndex(LectureDocument document, List<String> termsInDocument, String fieldType) {
        // 在添加文档前确保索引实例可用
        if (invertedIndex == null) {
            throw new IllegalStateException("Inverted index is not initialized. Call loadIndex() or build it first.");
        }
        invertedIndex.addDocument(document, termsInDocument, fieldType);
    }

    /**
     * 从文件中加载倒排索引。
     * 通常在应用启动时调用，只会加载一次。
     * 如果文件不存在或加载失败，会初始化一个空的索引。
     */
    public synchronized void loadIndex() { // 使用 synchronized 确保只有一个线程进行加载
        if (indexLoaded.get()) {
            log.info("Inverted index already loaded or loading in progress.");
            return;
        }

        if (Files.exists(indexPath)) {
            try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(indexPath))) {
                Object readObject = ois.readObject();
                if (readObject instanceof InvertedIndex) {
                    this.invertedIndex = (InvertedIndex) readObject;
                    indexLoaded.set(true);
                    log.info("Inverted index loaded successfully from {}. Contains {} terms and {} documents.",
                            indexPath, invertedIndex.getDictionary().size(), invertedIndex.getTotalDocuments());
                } else {
                    log.error("Loaded object is not an InvertedIndex instance. Starting with empty index.");
                    this.invertedIndex = new InvertedIndex();
                }
            } catch (IOException | ClassNotFoundException e) {
                log.error("Failed to load inverted index from {}. Starting with empty index.", indexPath, e);
                this.invertedIndex = new InvertedIndex(); // 加载失败，使用新的空索引
            }
        } else {
            log.info("Inverted index file not found at {}. Starting with empty index.", indexPath);
            this.invertedIndex = new InvertedIndex(); // 文件不存在，使用新的空索引
        }
        indexLoaded.set(true); // 无论加载成功与否，都标记为已尝试加载
    }

    /**
     * 将倒排索引持久化到文件。
     * 通常在索引构建完成后调用。
     */
    public void persistIndex() {
        if (invertedIndex == null) {
            log.warn("Attempted to persist null inverted index. No data saved.");
            return;
        }
        try {
            Files.createDirectories(indexPath.getParent()); // 确保父目录存在
            try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(indexPath))) {
                oos.writeObject(invertedIndex);
                log.info("Inverted index persisted successfully to {}. Contains {} terms and {} documents.",
                        indexPath, invertedIndex.getDictionary().size(), invertedIndex.getTotalDocuments());
            }
        } catch (IOException e) {
            log.error("Failed to persist inverted index to {}.", indexPath, e);
        }
    }

    // 查询接口
    public List<Posting> getPostingsList(String term) {
        return getInvertedIndex().getPostings(term);
    }

    public int getDocumentFrequency(String term) {
        return getInvertedIndex().getDocumentFrequency(term);
    }

    public int getTotalDocumentsCount() {
        return getInvertedIndex().getTotalDocuments();
    }

    public LectureDocument getDocumentById(String docId) {
        return getInvertedIndex().getDocument(docId);
    }
}