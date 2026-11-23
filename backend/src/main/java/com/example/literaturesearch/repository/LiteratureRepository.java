package com.example.literaturesearch.repository;

import com.example.literaturesearch.entity.Literature;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Repository
public interface LiteratureRepository extends JpaRepository<Literature, Long> {
    List<Literature> findByTitleContaining(String title);
    List<Literature> findByAuthorsContaining(String author);
    List<Literature> findByUserId(Long userId);
    Page<Literature> findByUserId(Long userId, Pageable pageable);
    /** 历史数据 is_public 为 NULL 视为公开（该字段引入前文献就是共享的） */
    List<Literature> findByIsPublicTrueOrIsPublicIsNull();
    /**
     * 总知识库按板块筛选。不用派生方法名：OR 的解析优先级是 A OR (B AND C)，
     * 会把所有公开文献都放进结果而忽略板块条件。
     */
    @Query("SELECT l FROM Literature l WHERE (l.isPublic = true OR l.isPublic IS NULL) AND l.category = :category")
    List<Literature> findPublicByCategory(@Param("category") String category);

    /** 分页：总知识库（社区公开贡献） */
    @Query("SELECT l FROM Literature l WHERE l.isPublic = true OR l.isPublic IS NULL")
    Page<Literature> findPublic(Pageable pageable);

    /** 分页：总知识库按板块 */
    @Query("SELECT l FROM Literature l WHERE (l.isPublic = true OR l.isPublic IS NULL) AND l.category = :category")
    Page<Literature> findPublicByCategory(@Param("category") String category, Pageable pageable);

    /** 分页："只看收藏"——先取收藏 id 列表，再限定属主后分页（ids 为空时调用方需短路） */
    @Query("SELECT l FROM Literature l WHERE l.userId = :userId AND l.id IN :ids")
    Page<Literature> findByUserIdAndIdIn(@Param("userId") Long userId,
                                         @Param("ids") Collection<Long> ids,
                                         Pageable pageable);

    /** 启动时调用：上一进程可能在后台处理中途被杀，PROCESSING 永远等不到收尾。 */
    @Modifying
    @Query("UPDATE Literature l SET l.processStatus = 'FAILED' WHERE l.processStatus = 'PROCESSING'")
    @Transactional
    int resetStuckProcessing();

    /** 板块自动回填的对象：解析已就绪但没有板块标签的文献。 */
    List<Literature> findByProcessStatusAndCategoryIsNull(String processStatus);

    /**
     * 原子补打板块：仅在板块仍为空时写入。用定向 UPDATE 而非实体 save，
     * 避免与摘要修复/后台处理的整行保存并发时用旧快照覆盖对方字段。
     */
    @Modifying
    @Query("UPDATE Literature l SET l.category = :category WHERE l.id = :id AND l.category IS NULL")
    @Transactional
    int backfillCategory(@Param("id") Long id, @Param("category") String category);
}