package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.Quiz;
import com.abhi.quizbackend.entity.QuizQuestion;
import com.abhi.quizbackend.exception.ResourceNotFoundException;
import com.abhi.quizbackend.models.QuizQuestionResponse;
import com.abhi.quizbackend.properties.QuizProperties;
import com.abhi.quizbackend.repository.QuizRepository;
import com.abhi.quizbackend.repository.TopicRepository;

import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Keeps one current quiz per topic in the database. The model is called only
 * by {@link #prepare}, and only when a topic has no quiz yet, or its files,
 * prompt, model or question count changed.
 *
 * The model call takes up to a minute, so it runs outside any transaction: a
 * database connection is not held while the model writes.
 */
@Service
public class QuizCacheService {

    private final TopicMediaService topicMediaService;
    private final QuizGeneratorService quizGeneratorService;
    private final QuizRepository quizRepository;
    private final TopicRepository topicRepository;
    private final TransactionTemplate transactionTemplate;
    private final String generatorVersion;

    public QuizCacheService(
            TopicMediaService topicMediaService,
            QuizGeneratorService quizGeneratorService,
            QuizRepository quizRepository,
            TopicRepository topicRepository,
            PlatformTransactionManager transactionManager,
            QuizProperties quizProperties,
            @Value("${spring.ai.google.genai.chat.model:unknown}") String model) {
        this.topicMediaService = topicMediaService;
        this.quizGeneratorService = quizGeneratorService;
        this.quizRepository = quizRepository;
        this.topicRepository = topicRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        // A quiz made with another prompt, model or length is not reused.
        this.generatorVersion = "p" + QuizGeneratorService.PROMPT_VERSION
                + "/" + model
                + "/" + quizProperties.getQuestionCount();
    }

    /** The id of the topic's current quiz, if one exists. Never calls the model. */
    public Optional<Long> findCurrent(Long trackId, Long topicId) {
        return findQuizId(topicMediaService.getSources(trackId, topicId));
    }

    /**
     * Makes sure the topic has a current quiz, and returns its id. Calls the
     * model only if the quiz is missing.
     *
     * @throws com.abhi.quizbackend.exception.QuizSourceException if the day has no files the model can read
     */
    public Long prepare(Long trackId, Long topicId) {
        TopicMediaService.QuizSources sources = topicMediaService.getSources(trackId, topicId);

        Optional<Long> cached = findQuizId(sources);
        if (cached.isPresent()) {
            return cached.get();
        }

        Media[] media = topicMediaService.toMedia(sources.files());
        List<QuizQuestionResponse> generated = quizGeneratorService.generate(media);

        try {
            return save(sources, generated);
        } catch (DataIntegrityViolationException e) {
            // Another caller made a quiz for the same files at the same time and
            // saved it first. Use the saved one, so every user gets the same quiz.
            // If there is none, the topic was deleted while the model wrote.
            return findQuizId(sources)
                    .orElseThrow(() -> new ResourceNotFoundException("Topic not found in this track"));
        }
    }

    private Optional<Long> findQuizId(TopicMediaService.QuizSources sources) {
        return quizRepository.findQuizId(sources.topicId(), sources.sourceHash(), generatorVersion);
    }

    /**
     * Replaces the topic's old quizzes with this one, in one transaction.
     *
     * Phase 4 must keep an old quiz that a contest still uses.
     */
    private Long save(TopicMediaService.QuizSources sources, List<QuizQuestionResponse> questions) {
        return transactionTemplate.execute(status -> {
            quizRepository.deleteOtherVersions(sources.topicId(), sources.sourceHash(), generatorVersion);

            Quiz quiz = Quiz.builder()
                    .topic(topicRepository.getReferenceById(sources.topicId()))
                    .sourceHash(sources.sourceHash())
                    .generatorVersion(generatorVersion)
                    .build();
            IntStream.range(0, questions.size())
                    .mapToObj(i -> toRow(quiz, i, questions.get(i)))
                    .forEach(quiz.getQuestions()::add);

            // Flush here, so a clash with a concurrent save fails inside save().
            return quizRepository.saveAndFlush(quiz).getQuizId();
        });
    }

    private static QuizQuestion toRow(Quiz quiz, int position, QuizQuestionResponse q) {
        return QuizQuestion.builder()
                .quiz(quiz)
                .position(position)
                .question(q.question())
                .optionA(q.options().get(0))
                .optionB(q.options().get(1))
                .optionC(q.options().get(2))
                .optionD(q.options().get(3))
                .correctIndex((short) q.correctIndex())
                .explanation(q.explanation())
                .sourceFile(q.sourceFile())
                .build();
    }
}
