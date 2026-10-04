package com.abhi.quizbackend.service;

import com.example.quiz.QuizProperties;
import com.example.quiz.model.GeneratedQuiz;
import com.example.quiz.model.GeneratedQuiz.GeneratedQuestion;
import com.example.quiz.model.QuizQuestion;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class QuizGeneratorService {

    private static final String SYSTEM_PROMPT = """
            You write multiple-choice quiz questions from study material.

            Rules:
            - Every question must be answerable from the provided documents alone, not general knowledge.
            - Spread questions across ALL the documents and across different topics within them.
            - Test understanding of concepts, not trivia like page numbers or headings.
            - Each question has exactly one correct answer and exactly 3 distractors.
            - Distractors must be plausible and the same kind of thing as the answer
              (if the answer is a year, all options are years; if it's a term, all are terms).
            - All four options should be about the same length. Don't make the correct one the longest.
            - Never use "All of the above", "None of the above", or "Both A and B".
            - No two questions should test the same fact.
            - Copy sourceFile and sourcePage exactly from the FILE/PAGE markers.
            """;

    /** Max extra LLM calls to top up when some questions fail validation. */
    private static final int MAX_TOP_UP_CALLS = 2;

    private final ChatClient chatClient;
    private final QuizProperties props;

    public QuizGeneratorService(ChatClient.Builder builder, QuizProperties props) {
        // Spring AI auto-configures the builder from application.properties.
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
        this.props = props;
    }

    public List<QuizQuestion> generate(String documentsText) {
        int wanted = props.questionCount();
        List<GeneratedQuestion> accepted = new ArrayList<>();

        // First call, then up to MAX_TOP_UP_CALLS more if validation dropped some.
        for (int attempt = 0; attempt <= MAX_TOP_UP_CALLS && accepted.size() < wanted; attempt++) {
            int missing = wanted - accepted.size();
            GeneratedQuiz raw = callLlm(documentsText, missing, accepted);

            if (raw != null && raw.questions() != null) {
                for (GeneratedQuestion q : raw.questions()) {
                    if (accepted.size() < wanted && isValid(q, accepted)) {
                        accepted.add(q);
                    }
                }
            }
        }

        if (accepted.size() < wanted) {
            throw new QuizGenerationException(
                    "Could only generate " + accepted.size() + " valid questions out of " + wanted);
        }
        return accepted.stream().map(this::shuffleOptions).toList();
    }

    private GeneratedQuiz callLlm(String documentsText, int count, List<GeneratedQuestion> alreadyHave) {
        String avoid = alreadyHave.isEmpty() ? "" : """

                These questions already exist. Do NOT repeat or rephrase them:
                %s
                """.formatted(alreadyHave.stream()
                .map(q -> "- " + q.question())
                .collect(Collectors.joining("\n")));

        String userMessage = """
                Generate exactly %d multiple-choice questions from the documents below.
                %s
                <documents>
                %s
                </documents>
                """.formatted(count, avoid, documentsText);

        return chatClient.prompt()
                .user(userMessage)
                .call()
                // This is where the structured output happens:
                //  - Spring AI generates a JSON Schema from GeneratedQuiz.
                //  - useProviderStructuredOutput(): the schema is sent to the provider as an
                //    API-level constraint, so the model can't return malformed JSON.
                //  - validateSchema(): if the reply still doesn't match the schema, Spring AI
                //    sends the validation error back to the model and retries (3 attempts by default).
                .entity(GeneratedQuiz.class, spec -> spec
                        .useProviderStructuredOutput()
                        .validateSchema());
    }

    /**
     * Business rules the JSON Schema can't express. The schema guarantees the
     * shape; this checks the content.
     */
    private boolean isValid(GeneratedQuestion q, List<GeneratedQuestion> accepted) {
        if (isBlank(q.question()) || isBlank(q.correctAnswer()) || isBlank(q.explanation())) {
            return false;
        }
        if (q.distractors() == null || q.distractors().size() != 3) {
            return false;
        }

        // All four options must be non-blank and distinct (case-insensitive).
        Set<String> options = new HashSet<>();
        options.add(normalize(q.correctAnswer()));
        for (String d : q.distractors()) {
            if (isBlank(d) || !options.add(normalize(d))) {
                return false;
            }
        }

        // Reject banned option patterns.
        for (String opt : options) {
            if (opt.contains("all of the above") || opt.contains("none of the above")) {
                return false;
            }
        }

        // Simple duplicate-question check. For better dedup, compare embeddings.
        String qNorm = normalize(q.question());
        return accepted.stream().noneMatch(a -> normalize(a.question()).equals(qNorm));
    }

    /** Puts the correct answer at a random position. Never trust the LLM's ordering. */
    private QuizQuestion shuffleOptions(GeneratedQuestion q) {
        List<String> options = new ArrayList<>(4);
        options.add(q.correctAnswer().strip());
        q.distractors().forEach(d -> options.add(d.strip()));
        // ThreadLocalRandom.current() must be called on the thread that uses it, never stored in a field.
        Collections.shuffle(options, ThreadLocalRandom.current());

        int correctIndex = options.indexOf(q.correctAnswer().strip());
        return new QuizQuestion(q.question().strip(), List.copyOf(options), correctIndex,
                q.explanation().strip(), q.sourceFile(), q.sourcePage());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
*
    private static String normalize(String s) {
        return s.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public static class QuizGenerationException extends RuntimeException {
        public QuizGenerationException(String message) {
            super(message);
        }
    }
}
