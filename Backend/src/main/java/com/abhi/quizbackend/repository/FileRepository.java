package com.abhi.quizbackend.repository;
import com.abhi.quizbackend.entity.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileRepository
        extends JpaRepository<FileEntity, Long> {
}
