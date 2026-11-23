package com.example.literaturesearch.repository;

import com.example.literaturesearch.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NoteRepository extends JpaRepository<Note, Long> {

    /** 工作区笔记按文献加载：新笔记在前（id 单调递增近似时间序） */
    List<Note> findByUserIdAndLiteratureIdOrderByIdDesc(Long userId, Long literatureId);

    /** 笔记插件页：用户全部笔记 */
    List<Note> findByUserIdOrderByIdDesc(Long userId);

    /** 修改/删除前校验属主：id + userId 双条件，天然防越权 */
    Optional<Note> findByIdAndUserId(Long id, Long userId);

    @Modifying
    void deleteByLiteratureId(Long literatureId);
}
