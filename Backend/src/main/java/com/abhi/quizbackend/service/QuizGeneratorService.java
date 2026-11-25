package com.abhi.quizbackend.service;

import com.abhi.quizbackend.exception.QuizGenerationException;
import com.abhi.quizbackend.exception.QuizSourceException;
import com.abhi.quizbackend.models.GeneratedQuiz;
import com.abhi.quizbackend.models.GeneratedQuiz.GeneratedQuestion;
import com.abhi.quizbackend.models.QuizQuestionResponse;
import com.abhi.quizbackend.properties.QuizProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Makes a quiz from a topic's files with one model call. The prompt tells the
 * model how many questions to write from each file, as decided by
 * {@link QuestionAllocator}.
 */
@Service
public class QuizGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(QuizGeneratorService.class);

    private static final String SYSTEM_PROMPT = """
            You write multiple-choice quiz questions from study material.

            Rules:
            - The files are attached in the same order as the file list in the request.
            - Write exactly the number of questions given for each file, from that file only.
            - Every question must be answerable from its file alone, not from general knowledge.
            - Test understanding of concepts, not trivia like page numbers or headings.
            - Each question has exactly one correct answer and exactly 3 distractors.
            - Distractors must be plausible and the same kind of thing as the answer.
            - All four options should be about the same length. Do not make the correct one the longest.
            - Never use "All of the above", "None of the above" or "Both A and B".
            - No two questions should test the same fact.
            - Copy the file name exactly into sourceFile.
            """;

    /**
     * Part of the cache key of every stored quiz. Raise it when SYSTEM_PROMPT,
     * buildPrompt or isValid change, so quizzes made the old way are replaced.
     */
    public static final int PROMPT_VERSION = 1;

    /** Extra calls when some questions fail the checks. One call is the normal case. */
    private static final int MAX_TOP_UP_CALLS = 2;

    private final ChatClient chatClient;
    private final QuestionAllocator allocator;
    private final QuizProperties properties;

    public QuizGeneratorService(ChatClient.Builder builder, QuestionAllocator allocator, QuizProperties properties) {
        this.chatClient = builder.build();
        this.allocator = allocator;
        this.properties = properties;
    }

    /**
     * @param files the topic's files, each with its file name as the Media name
     * @throws QuizSourceException     if there are no files
     * @throws QuizGenerationException if the model does not give enough good questions
     */
    public List<QuizQuestionResponse> generate(Media[] files) {
        if (files.length == 0) {
            throw new QuizSourceException("This topic has no files to make a quiz from. Add a PDF, image, text or Markdown file.");
        }

        int wanted = properties.getQuestionCount();
        List<GeneratedQuestion> accepted = new ArrayList<>();

        // First call, then up to MAX_TOP_UP_CALLS more for the questions that failed the checks.
        for (int call = 0; call <= MAX_TOP_UP_CALLS && accepted.size() < wanted; call++) {
            int missing = wanted - accepted.size();
            List<Integer> counts = allocator.allocate(files.length, missing);

            GeneratedQuiz reply = callModel(buildPrompt(files, counts, accepted), filesWithQuestions(files, counts));
            if (reply == null || reply.questions() == null) {
                continue;
            }
            for (GeneratedQuestion question : reply.questions()) {
                if (accepted.size() < wanted && isValid(question, accepted)) {
                    accepted.add(question);
                }
            }
        }

        if (accepted.size() < wanted) {
            throw new QuizGenerationException(
                    "Could only write " + accepted.size() + " good questions out of " + wanted + ". Try again.");
        }
        return accepted.stream().map(this::shuffleOptions).toList();
    }

    /**
     * One model call. Returns null if the call failed, so the loop can try again.
     * Package-private so tests can replace it.
     */
    GeneratedQuiz callModel(String prompt, Media[] files) {
        try {
            return chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(user -> user.text(prompt).media(files))
                    .call()
                    // Gemini enforces the JSON schema made from GeneratedQuiz, and
                    // Spring AI retries if the reply still does not match it.
                    .entity(GeneratedQuiz.class, spec -> spec.useProviderStructuredOutput().validateSchema());
        } catch (RuntimeException e) {
            log.error("Quiz generation call failed", e);
            return null;
        }
    }

    /**
     * The request text: the questions per file, and the questions to avoid.
     * Only files with at least one question are listed, in the order they are attached.
     */
    String buildPrompt(Media[] files, List<Integer> counts, List<GeneratedQuestion> alreadyHave) {
        StringBuilder fileList = new StringBuilder();
        int number = 1;
        int total = 0;
        for (int i = 0; i < files.length; i++) {
            if (counts.get(i) == 0) {
                continue;
            }
            fileList.append(number++).append(". ").append(files[i].getName())
                    .append(": ").append(counts.get(i))
                    .append(counts.get(i) == 1 ? " question" : " questions").append('\n');
            total += counts.get(i);
        }

        String avoid = alreadyHave.isEmpty() ? "" : """

                These questions already exist. Do not repeat or rephrase them:
                %s
                """.formatted(alreadyHave.stream()
                .map(q -> "- " + q.question())
                .collect(Collectors.joining("\n")));

        return """
                Write %d multiple-choice questions in total from the attached files.

                Files, in attachment order, and the questions to write from each:
                %s%s""".formatted(total, fileList, avoid);
    }

    /** The files that get at least one question, in the same order as in the prompt. */
    private static Media[] filesWithQuestions(Media[] files, List<Integer> counts) {
        List<Media> selected = new ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            if (counts.get(i) > 0) {
                selected.add(files[i]);
            }
        }
        return selected.toArray(Media[]::new);
    }

    /** Rules the JSON schema cannot express. The schema guarantees the shape; this checks the content. */
    private boolean isValid(GeneratedQuestion q, List<GeneratedQuestion> accepted) {
        if (isBlank(q.question()) || isBlank(q.correctAnswer()) || isBlank(q.explanation())) {
            return false;
        }
        if (q.distractors() == null || q.distractors().size() != 3) {
            return false;
        }

        // All four options must be non-blank and different, ignoring case and spaces.
        Set<String> options = new HashSet<>();
        options.add(normalize(q.correctAnswer()));
        for (String distractor : q.distractors()) {
            if (isBlank(distractor) || !options.add(normalize(distractor))) {
                return false;
            }
        }
        for (String option : options) {
            if (option.contains("all of the above") || option.contains("none of the above")) {
                return false;
            }
        }

        String question = normalize(q.question());
        return accepted.stream().noneMatch(a -> normalize(a.question()).equals(question));
    }

    /** Puts the correct answer at a random position. The model never decides the position. */
    private QuizQuestionResponse shuffleOptions(GeneratedQuestion q) {
        List<String> options = new ArrayList<>(4);
        options.add(q.correctAnswer().strip());
        q.distractors().forEach(d -> options.add(d.strip()));
        Collections.shuffle(options, ThreadLocalRandom.current());

        return new QuizQuestionResponse(
                q.question().strip(),
                List.copyOf(options),
                options.indexOf(q.correctAnswer().strip()),
                q.explanation().strip(),
                q.sourceFile());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String normalize(String s) {
        return s.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
