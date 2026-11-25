package com.abhi.quizbackend.service;

import com.abhi.quizbackend.exception.QuizGenerationException;
import com.abhi.quizbackend.exception.QuizSourceException;
import com.abhi.quizbackend.models.GeneratedQuiz;
import com.abhi.quizbackend.models.GeneratedQuiz.GeneratedQuestion;
import com.abhi.quizbackend.models.QuizQuestionResponse;
import com.abhi.quizbackend.properties.QuizProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Tests the generator with the model call replaced, so no API is called. */
class QuizGeneratorServiceTest {

    /** The generator with callModel() replaced by queued replies. */
    private static class FakeModelGenerator extends QuizGeneratorService {
        final Deque<GeneratedQuiz> replies = new ArrayDeque<>();
        final List<String> prompts = new ArrayList<>();
        final List<Integer> attachedFileCounts = new ArrayList<>();

        FakeModelGenerator(int questionCount) {
            super(mock(ChatClient.Builder.class), new QuestionAllocator(), properties(questionCount));
        }

        @Override
        GeneratedQuiz callModel(String prompt, Media[] files) {
            prompts.add(prompt);
            attachedFileCounts.add(files.length);
            return replies.poll();
        }

        private static QuizProperties properties(int questionCount) {
            QuizProperties properties = new QuizProperties();
            properties.setQuestionCount(questionCount);
            return properties;
        }
    }

    private static Media file(String name) {
        return Media.builder().mimeType(MimeTypeUtils.TEXT_PLAIN).data(new byte[] {1}).name(name).build();
    }

    private static GeneratedQuestion question(String text) {
        return new GeneratedQuestion(text, "right", List.of("wrong 1", "wrong 2", "wrong 3"), "because", "a.pdf");
    }

    private static GeneratedQuiz reply(int count, String prefix) {
        return new GeneratedQuiz(IntStream.range(0, count).mapToObj(i -> question(prefix + i)).toList());
    }

    @Test
    void oneCallListsEveryFileWithItsCountAndShufflesOptions() {
        FakeModelGenerator generator = new FakeModelGenerator(10);
        generator.replies.add(reply(10, "Q"));
        Media[] files = IntStream.range(0, 7).mapToObj(i -> file("f" + i + ".pdf")).toArray(Media[]::new);

        List<QuizQuestionResponse> quiz = generator.generate(files);

        assertThat(quiz).hasSize(10);
        assertThat(generator.prompts).hasSize(1);
        assertThat(generator.prompts.get(0)).contains("Write 10 multiple-choice questions", "1. f0.pdf: ", "7. f6.pdf: ");
        assertThat(generator.attachedFileCounts).containsExactly(7);
        for (QuizQuestionResponse q : quiz) {
            assertThat(q.options()).containsExactlyInAnyOrder("right", "wrong 1", "wrong 2", "wrong 3");
            assertThat(q.options().get(q.correctIndex())).isEqualTo("right");
        }
    }

    @Test
    void filesWithZeroQuestionsAreNotAttached() {
        FakeModelGenerator generator = new FakeModelGenerator(3);
        generator.replies.add(reply(3, "Q"));
        Media[] files = IntStream.range(0, 5).mapToObj(i -> file("f" + i + ".pdf")).toArray(Media[]::new);

        generator.generate(files);

        assertThat(generator.attachedFileCounts).containsExactly(3);
    }

    @Test
    void topsUpOnlyTheMissingQuestions() {
        FakeModelGenerator generator = new FakeModelGenerator(3);
        GeneratedQuestion bad = new GeneratedQuestion("Bad", "right", List.of("Right", "x", "y"), "e", "a.pdf");
        generator.replies.add(new GeneratedQuiz(List.of(question("Q1"), bad)));
        generator.replies.add(new GeneratedQuiz(List.of(question("Q2"), question("Q3"))));

        List<QuizQuestionResponse> quiz = generator.generate(new Media[] {file("a.pdf")});

        assertThat(quiz).extracting(QuizQuestionResponse::question).containsExactly("Q1", "Q2", "Q3");
        assertThat(generator.prompts.get(1)).contains("Write 2 multiple-choice questions", "- Q1");
    }

    @Test
    void failsAfterThreeCallsWithTooFewQuestions() {
        FakeModelGenerator generator = new FakeModelGenerator(3);
        generator.replies.add(reply(1, "Q"));

        assertThatThrownBy(() -> generator.generate(new Media[] {file("a.pdf")}))
                .isInstanceOf(QuizGenerationException.class)
                .hasMessageContaining("Could only write 1 good questions out of 3");
        assertThat(generator.prompts).hasSize(3);
    }

    @Test
    void noFilesIsASourceError() {
        assertThatThrownBy(() -> new FakeModelGenerator(10).generate(new Media[0]))
                .isInstanceOf(QuizSourceException.class);
    }
}
