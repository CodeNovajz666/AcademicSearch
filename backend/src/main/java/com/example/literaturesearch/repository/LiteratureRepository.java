package com.example.literaturesearch.repository;

import com.example.literaturesearch.entity.Literature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LiteratureRepository extends JpaRepository<Literature, Long> {
    List<Literature> findByTitleContaining(String title);
    List<Literature> findByAuthorsContaining(String author);
}