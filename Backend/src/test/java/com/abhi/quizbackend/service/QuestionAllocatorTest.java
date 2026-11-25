package com.abhi.quizbackend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionAllocatorTest {

    private final QuestionAllocator allocator = new QuestionAllocator();

    @Test
    void sevenFilesTenQuestions() {
        List<Integer> counts = allocator.allocate(7, 10, new Random(42));

        assertThat(counts).hasSize(7);
        assertThat(counts.stream().mapToInt(Integer::intValue).sum()).isEqualTo(10);
        assertThat(counts).allMatch(c -> c == 1 || c == 2);
        assertThat(counts.stream().filter(c -> c == 2).count()).isEqualTo(3);
    }

    @ParameterizedTest
    @CsvSource({"1,10", "2,10", "3,10", "10,10", "12,10", "4,0"})
    void countsAlwaysAddUpAndDifferByAtMostOne(int files, int questions) {
        List<Integer> counts = allocator.allocate(files, questions, new Random(files));

        assertThat(counts).hasSize(files);
        assertThat(counts.stream().mapToInt(Integer::intValue).sum()).isEqualTo(questions);
        int min = counts.stream().mapToInt(Integer::intValue).min().orElseThrow();
        int max = counts.stream().mapToInt(Integer::intValue).max().orElseThrow();
        assertThat(max - min).isLessThanOrEqualTo(1);
    }

    @Test
    void moreFilesThanQuestionsLeavesSomeFilesAtZero() {
        List<Integer> counts = allocator.allocate(12, 10, new Random(1));

        assertThat(counts.stream().filter(c -> c == 0).count()).isEqualTo(2);
    }

    @Test
    void theExtraQuestionsGoToDifferentFilesOnDifferentRuns() {
        List<Integer> first = allocator.allocate(7, 10, new Random(1));
        List<Integer> second = allocator.allocate(7, 10, new Random(2));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void rejectsZeroFiles() {
        assertThatThrownBy(() -> allocator.allocate(0, 10)).isInstanceOf(IllegalArgumentException.class);
    }
}
