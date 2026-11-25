package com.abhi.quizbackend.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/**
 * Decides how many questions to make from each file.
 *
 * Every file gets questionCount / fileCount questions. The remainder
 * (questionCount % fileCount) goes to that many files picked at random,
 * one extra question each. Example: 7 files, 10 questions gives
 * 1 question to every file and 1 more to 3 random files, e.g. [1, 2, 1, 1, 2, 1, 2].
 * If there are more files than questions, some files get 0.
 */
@Component
public class QuestionAllocator {

    /**
     * @return one entry per file, in file order; the entries add up to questionCount
     */
    public List<Integer> allocate(int fileCount, int questionCount) {
        return allocate(fileCount, questionCount, ThreadLocalRandom.current());
    }

    /** Same as {@link #allocate(int, int)}, with a given Random so tests can repeat a result. */
    List<Integer> allocate(int fileCount, int questionCount, Random random) {
        if (fileCount <= 0) {
            throw new IllegalArgumentException("fileCount must be positive");
        }
        if (questionCount < 0) {
            throw new IllegalArgumentException("questionCount must not be negative");
        }

        int base = questionCount / fileCount;
        int remainder = questionCount % fileCount;

        List<Integer> counts = new ArrayList<>(Collections.nCopies(fileCount, base));

        // Pick `remainder` different files at random for the extra question.
        List<Integer> fileIndexes = new ArrayList<>(IntStream.range(0, fileCount).boxed().toList());
        Collections.shuffle(fileIndexes, random);
        for (int i = 0; i < remainder; i++) {
            int file = fileIndexes.get(i);
            counts.set(file, counts.get(file) + 1);
        }
        return counts;
    }
}
