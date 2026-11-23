package com.example.literaturesearch.repository;

import com.example.literaturesearch.entity.UserLiteratureState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserLiteratureStateRepository extends JpaRepository<UserLiteratureState, Long> {

    Optional<UserLiteratureState> findByUserIdAndLiteratureId(Long userId, Long literatureId);

    List<UserLiteratureState> findByUserId(Long userId);

    List<UserLiteratureState> findByLiteratureId(Long literatureId);

    /** 用户收藏的文献 id（分页"只看收藏"先取 id 列表再按属主分页，收藏数小，in 查询足够） */
    @Query("SELECT s.literatureId FROM UserLiteratureState s WHERE s.userId = :userId AND s.starred = true")
    List<Long> findStarredLiteratureIds(@Param("userId") Long userId);

    @Modifying
    void deleteByLiteratureId(Long literatureId);
}
