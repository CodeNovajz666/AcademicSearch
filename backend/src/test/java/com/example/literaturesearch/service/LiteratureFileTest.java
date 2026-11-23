package com.example.literaturesearch.service;

import com.example.literaturesearch.entity.Literature;
import com.example.literaturesearch.exception.BusinessException;
import com.example.literaturesearch.exception.LiteratureNotFoundException;
import com.example.literaturesearch.repository.LiteratureRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiteratureFileTest {

    private final LiteratureRepository repository = mock(LiteratureRepository.class);
    private final LiteratureService service = new LiteratureService(repository, null, null, null, null);

    @TempDir
    Path tempDir;

    private Literature literatureWithFile(String path) {
        return Literature.builder().id(1L).title("T").fileName("paper.pdf").filePath(path).build();
    }

    @Test
    void openFile_existingFile_returnsPathAndName() throws Exception {
        Path pdf = tempDir.resolve("paper.pdf");
        Files.writeString(pdf, "%PDF-1.4 fake");
        when(repository.findById(1L)).thenReturn(Optional.of(literatureWithFile(pdf.toString())));

        LiteratureService.LiteratureFile file = service.openFile(1L, 1L);
        assertEquals(pdf, file.path());
        assertEquals("paper.pdf", file.fileName());
    }

    @Test
    void openFile_recordMissing_throwsNotFound() {
        when(repository.findById(2L)).thenReturn(Optional.empty());
        assertThrows(LiteratureNotFoundException.class, () -> service.openFile(2L, 1L));
    }

    @Test
    void openFile_fileMissingOnDisk_throwsBusiness() {
        Path missing = tempDir.resolve("deleted.pdf");
        when(repository.findById(1L)).thenReturn(Optional.of(literatureWithFile(missing.toString())));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.openFile(1L, 1L));
        assertTrue(ex.getMessage().contains("不存在"));
    }
}
