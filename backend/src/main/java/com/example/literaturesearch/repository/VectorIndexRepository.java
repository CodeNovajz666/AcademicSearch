package com.example.literaturesearch.repository;

import com.example.literaturesearch.entity.VectorIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VectorIndexRepository extends JpaRepository<VectorIndex, Long> {
    List<VectorIndex> findByLiteratureId(Long literatureId);
    void deleteByLiteratureId(Long literatureId);
}