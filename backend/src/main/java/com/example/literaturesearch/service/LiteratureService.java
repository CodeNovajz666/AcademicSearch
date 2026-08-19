package com.example.literaturesearch.service;

import com.example.literaturesearch.dto.response.LiteratureResponse;
import com.example.literaturesearch.entity.Literature;
import com.example.literaturesearch.exception.LiteratureNotFoundException;
import com.example.literaturesearch.repository.LiteratureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiteratureService {

    private final LiteratureRepository literatureRepository;
    private final VectorService vectorService;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Transactional
    public LiteratureResponse uploadLiterature(MultipartFile file, String title, String authors, 
                                               String journal, Integer year, String doi) throws IOException {
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || !originalFileName.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("只支持PDF格式文件");
        }

        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String storedFileName = System.currentTimeMillis() + "_" + originalFileName;
        Path filePath = uploadPath.resolve(storedFileName);
        file.transferTo(filePath.toFile());

        String content = extractTextFromPdf(filePath.toString());
        
        String extractedTitle = title;
        String extractedAuthors = authors;
        String extractedAbstract = extractAbstract(content);
        
        if (extractedTitle == null || extractedTitle.isEmpty()) {
            extractedTitle = extractTitle(content);
            if (extractedTitle == null || extractedTitle.isEmpty()) {
                extractedTitle = originalFileName.replace(".pdf", "");
            }
        }

        Literature literature = Literature.builder()
                .title(extractedTitle)
                .authors(extractedAuthors)
                .abstractText(extractedAbstract)
                .journal(journal)
                .year(year)
                .doi(doi)
                .fileName(originalFileName)
                .filePath(filePath.toString())
                .content(content)
                .build();

        Literature savedLiterature = literatureRepository.save(literature);
        
        if (content != null && !content.isEmpty()) {
            vectorService.createVectorIndex(savedLiterature.getId(), content);
        }

        log.info("Literature uploaded: {}", savedLiterature.getTitle());
        return LiteratureResponse.fromEntity(savedLiterature);
    }

    private String extractTextFromPdf(String filePath) throws IOException {
        try (PDDocument document = Loader.loadPDF(new java.io.File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String extractTitle(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && line.length() > 5 && line.length() < 200) {
                return line;
            }
        }
        return null;
    }

    private String extractAbstract(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        
        int abstractStart = content.toLowerCase().indexOf("abstract");
        int keywordsStart = content.toLowerCase().indexOf("keywords");
        int introductionStart = content.toLowerCase().indexOf("introduction");
        
        if (abstractStart == -1) {
            abstractStart = content.toLowerCase().indexOf("摘要");
        }
        
        if (abstractStart == -1) {
            return null;
        }
        
        int start = abstractStart + "abstract".length();
        int end = content.length();
        
        if (keywordsStart > abstractStart) {
            end = Math.min(end, keywordsStart);
        }
        if (introductionStart > abstractStart) {
            end = Math.min(end, introductionStart);
        }
        
        String abstractText = content.substring(start, end).trim();
        if (abstractText.length() > 3000) {
            abstractText = abstractText.substring(0, 3000);
        }
        
        return abstractText;
    }

    public LiteratureResponse getLiteratureById(Long id) {
        Literature literature = literatureRepository.findById(id)
                .orElseThrow(() -> new LiteratureNotFoundException(id));
        return LiteratureResponse.fromEntity(literature);
    }

    public List<LiteratureResponse> getAllLiteratures() {
        return literatureRepository.findAll().stream()
                .map(LiteratureResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteLiterature(Long id) {
        Literature literature = literatureRepository.findById(id)
                .orElseThrow(() -> new LiteratureNotFoundException(id));
        
        try {
            Files.deleteIfExists(Paths.get(literature.getFilePath()));
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", literature.getFilePath());
        }
        
        vectorService.deleteVectorsByLiteratureId(id);
        literatureRepository.delete(literature);
        
        log.info("Literature deleted: {}", id);
    }
}