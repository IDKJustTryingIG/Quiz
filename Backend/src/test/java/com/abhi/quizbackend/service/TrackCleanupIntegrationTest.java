package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.FileEntity;
import com.abhi.quizbackend.entity.Topic;
import com.abhi.quizbackend.entity.TopicFile;
import com.abhi.quizbackend.entity.Track;
import com.abhi.quizbackend.entity.TrackTopic;
import com.abhi.quizbackend.entity.TrackVisibility;
import com.abhi.quizbackend.entity.User;
import com.abhi.quizbackend.exception.InvalidTrackEditException;
import com.abhi.quizbackend.exception.ResourceNotFoundException;
import com.abhi.quizbackend.models.CreateFullTrackRequest;
import com.abhi.quizbackend.models.CreateFullTrackRequest.TopicDraft;
import com.abhi.quizbackend.models.EditTrackRequest;
import com.abhi.quizbackend.models.EditTrackRequest.DayEdit;
import com.abhi.quizbackend.models.TrackResponse;
import com.abhi.quizbackend.properties.FileStorageProperties;
import com.abhi.quizbackend.repository.FileRepository;
import com.abhi.quizbackend.repository.TopicFileRepository;
import com.abhi.quizbackend.repository.TopicRepository;
import com.abhi.quizbackend.repository.TrackRepository;
import com.abhi.quizbackend.repository.TrackTopicRepository;
import com.abhi.quizbackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs track deletion and editing against a real JPA stack on H2, with real
 * files on disk. Each service call commits its own transaction, as in the app,
 * so the after-commit file deletion really runs.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@Import({TrackService.class, TrackCreationService.class, TrackEditService.class,
        OrphanCleaner.class, TrackFileStore.class, LocalFileStorageService.class})
@EnableConfigurationProperties(FileStorageProperties.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TrackCleanupIntegrationTest {

    private static final Path STORAGE = createStorage();

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.file-storage.location", STORAGE::toString);
    }

    @Autowired TrackService trackService;
    @Autowired TrackCreationService trackCreationService;
    @Autowired TrackEditService trackEditService;
    @Autowired UserRepository userRepository;
    @Autowired TrackRepository trackRepository;
    @Autowired TrackTopicRepository trackTopicRepository;
    @Autowired TopicRepository topicRepository;
    @Autowired TopicFileRepository topicFileRepository;
    @Autowired FileRepository fileRepository;
    @Autowired PlatformTransactionManager transactionManager;

    private Long ownerId;
    private Long otherUserId;

    @BeforeEach
    void setUp() {
        ownerId = user("owner").getUserId();
        otherUserId = user("other").getUserId();
    }

    // ---------- delete ----------

    @Test
    void deleteRemovesDaysTopicsFileRowsAndBytes() {
        TrackResponse track = createTrack(ownerId, List.of("Day one", "Day two"),
                List.of(List.of(pdf("a.pdf"), pdf("b.pdf")), List.of(pdf("c.pdf"))));
        List<Long> topicIds = topicIdsOf(track.trackId());
        List<String> keys = storageKeysOf(topicIds);
        assertThat(keys).hasSize(3).allMatch(this::onDisk);

        trackService.delete(track.trackId(), ownerId);

        assertThat(trackRepository.findById(track.trackId())).isEmpty();
        assertThat(topicRepository.findAllById(topicIds)).isEmpty();
        assertThat(keys).noneMatch(this::onDisk);
        assertThat(keys).allMatch(key -> inTx(() -> fileRepository.findAll().stream()
                .noneMatch(f -> f.getStorageKey().equals(key))));
    }

    @Test
    void deleteKeepsATopicThatAnotherTrackStillUses() {
        TrackResponse first = createTrack(ownerId, List.of("Shared day"), List.of(List.of(pdf("shared.pdf"))));
        TrackResponse second = createTrack(otherUserId, List.of("Own day"), List.of(List.of()));
        Long sharedTopicId = topicIdsOf(first.trackId()).get(0);
        attach(second.trackId(), sharedTopicId, 1);
        List<String> keys = storageKeysOf(List.of(sharedTopicId));

        trackService.delete(first.trackId(), ownerId);

        assertThat(topicRepository.findById(sharedTopicId)).isPresent();
        assertThat(keys).allMatch(this::onDisk);
    }

    @Test
    void onlyTheOwnerCanDelete() {
        TrackResponse track = createTrack(ownerId, List.of("Day"), List.of(List.of(pdf("a.pdf"))),
                TrackVisibility.PUBLIC);

        assertThatThrownBy(() -> trackService.delete(track.trackId(), otherUserId))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(trackRepository.findById(track.trackId())).isPresent();
    }

    // ---------- edit ----------

    @Test
    void editRenamesRemovesAndAddsDaysAndFiles() {
        TrackResponse track = createTrack(ownerId, List.of("Arrays", "Trees"),
                List.of(List.of(pdf("a.pdf"), pdf("b.pdf")), List.of(pdf("c.pdf"))));
        List<Long> topicIds = topicIdsOf(track.trackId());
        Long arrays = topicIds.get(0);
        Long trees = topicIds.get(1);
        Long keepA = fileIdNamed(arrays, "a.pdf");
        String keyB = storageKeyNamed(arrays, "b.pdf");
        String keyC = storageKeyNamed(trees, "c.pdf");

        trackEditService.edit(track.trackId(),
                new EditTrackRequest("New description", TrackVisibility.PUBLIC, List.of(
                        new DayEdit(arrays, "Arrays and hashing", List.of(keepA)),
                        new DayEdit(null, "Graphs", null))),
                List.of(List.of(pdf("d.pdf")), List.of(pdf("e.pdf"))),
                ownerId);

        inTx(() -> {
            Track saved = trackRepository.findById(track.trackId()).orElseThrow();
            assertThat(saved.getName()).isEqualTo("Track " + track.name().substring(6));
            assertThat(saved.getDescription()).isEqualTo("New description");
            assertThat(saved.getVisibility()).isEqualTo(TrackVisibility.PUBLIC);
            List<TrackTopic> days = trackTopicRepository.findByTrackTrackIdOrderByPositionAsc(track.trackId());
            assertThat(days).extracting(TrackTopic::getPosition).containsExactly(0, 1);
            assertThat(days).extracting(tt -> tt.getTopic().getName()).containsExactly("Arrays and hashing", "Graphs");
            assertThat(fileNames(days.get(0).getTopic().getTopicId())).containsExactly("a.pdf", "d.pdf");
            assertThat(fileNames(days.get(1).getTopic().getTopicId())).containsExactly("e.pdf");
            return null;
        });
        assertThat(topicRepository.findById(trees)).isEmpty();
        assertThat(onDisk(keyB)).isFalse();
        assertThat(onDisk(keyC)).isFalse();
    }

    @Test
    void anInvalidEditChangesNothingAndWritesNoFiles() {
        TrackResponse track = createTrack(ownerId, List.of("Arrays"), List.of(List.of(pdf("a.pdf"))));
        Long arrays = topicIdsOf(track.trackId()).get(0);
        long filesBefore = filesOnDisk();

        assertThatThrownBy(() -> trackEditService.edit(track.trackId(),
                new EditTrackRequest(null, TrackVisibility.PRIVATE, List.of(
                        new DayEdit(arrays, "Renamed", List.of(999_999L)))),
                List.of(List.of(pdf("new.pdf"))),
                ownerId))
                .isInstanceOf(InvalidTrackEditException.class);

        assertThat(filesOnDisk()).isEqualTo(filesBefore);
        assertThat(inTx(() -> topicRepository.findById(arrays).orElseThrow().getName())).isEqualTo("Arrays");
    }

    @Test
    void editingASharedTopicCopiesItAndLeavesTheOtherTrackAlone() {
        TrackResponse first = createTrack(ownerId, List.of("Shared"), List.of(List.of(pdf("x.pdf"), pdf("y.pdf"))));
        TrackResponse second = createTrack(otherUserId, List.of("Own"), List.of(List.of()));
        Long shared = topicIdsOf(first.trackId()).get(0);
        attach(second.trackId(), shared, 1);
        Long keepX = fileIdNamed(shared, "x.pdf");
        String keyY = storageKeyNamed(shared, "y.pdf");

        trackEditService.edit(first.trackId(),
                new EditTrackRequest(null, TrackVisibility.PRIVATE, List.of(
                        new DayEdit(shared, "Renamed copy", List.of(keepX)))),
                List.of(List.of()),
                ownerId);

        inTx(() -> {
            assertThat(topicRepository.findById(shared).orElseThrow().getName()).isEqualTo("Shared");
            assertThat(fileNames(shared)).containsExactly("x.pdf", "y.pdf");
            TrackTopic firstDay = trackTopicRepository.findByTrackTrackIdOrderByPositionAsc(first.trackId()).get(0);
            assertThat(firstDay.getTopic().getTopicId()).isNotEqualTo(shared);
            assertThat(firstDay.getTopic().getName()).isEqualTo("Renamed copy");
            assertThat(fileNames(firstDay.getTopic().getTopicId())).containsExactly("x.pdf");
            return null;
        });
        // y.pdf is still in the other track's topic, so its bytes stay.
        assertThat(onDisk(keyY)).isTrue();
    }

    @Test
    void onlyTheOwnerCanEdit() {
        TrackResponse publicTrack = createTrack(ownerId, List.of("Day"), List.of(List.of()), TrackVisibility.PUBLIC);
        TrackResponse privateTrack = createTrack(ownerId, List.of("Day"), List.of(List.of()));
        EditTrackRequest edit = new EditTrackRequest(null, TrackVisibility.PUBLIC, List.of(new DayEdit(null, "X", null)));

        assertThatThrownBy(() -> trackEditService.edit(publicTrack.trackId(), edit, List.of(List.of()), otherUserId))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> trackEditService.edit(privateTrack.trackId(), edit, List.of(List.of()), otherUserId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- helpers ----------

    private User user(String prefix) {
        User user = new User();
        String unique = prefix + UUID.randomUUID().toString().substring(0, 8);
        user.setEmail(unique + "@example.com");
        user.setUsername(unique);
        return userRepository.save(user);
    }

    private TrackResponse createTrack(Long owner, List<String> days, List<List<MultipartFile>> files) {
        return createTrack(owner, days, files, TrackVisibility.PRIVATE);
    }

    private TrackResponse createTrack(Long owner, List<String> days, List<List<MultipartFile>> files,
                                      TrackVisibility visibility) {
        String name = "Track " + UUID.randomUUID();
        return trackCreationService.create(
                new CreateFullTrackRequest(name, null, visibility, days.stream().map(TopicDraft::new).toList()),
                files, owner);
    }

    /** Links an existing topic into another track, as POST /api/tracks/{id}/topics/{topicId} does. */
    private void attach(Long trackId, Long topicId, int position) {
        inTx(() -> trackTopicRepository.save(TrackTopic.builder()
                .track(trackRepository.findById(trackId).orElseThrow())
                .topic(topicRepository.findById(topicId).orElseThrow())
                .position(position)
                .build()));
    }

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", ("%PDF " + name).getBytes());
    }

    private List<Long> topicIdsOf(Long trackId) {
        return inTx(() -> trackTopicRepository.findByTrackTrackIdOrderByPositionAsc(trackId).stream()
                .map(tt -> tt.getTopic().getTopicId()).toList());
    }

    private List<String> storageKeysOf(List<Long> topicIds) {
        return inTx(() -> topicIds.stream()
                .flatMap(id -> topicFileRepository.findByTopicTopicIdOrderByPositionAsc(id).stream())
                .map(tf -> tf.getFile().getStorageKey()).toList());
    }

    private List<String> fileNames(Long topicId) {
        return topicFileRepository.findByTopicTopicIdOrderByPositionAsc(topicId).stream()
                .map(tf -> tf.getFile().getOriginalFilename()).toList();
    }

    private Long fileIdNamed(Long topicId, String name) {
        return inTx(() -> fileNamed(topicId, name).getFileId());
    }

    private String storageKeyNamed(Long topicId, String name) {
        return inTx(() -> fileNamed(topicId, name).getStorageKey());
    }

    private FileEntity fileNamed(Long topicId, String name) {
        return topicFileRepository.findByTopicTopicIdOrderByPositionAsc(topicId).stream()
                .map(TopicFile::getFile)
                .filter(f -> f.getOriginalFilename().equals(name))
                .findFirst().orElseThrow();
    }

    private boolean onDisk(String storageKey) {
        return Files.exists(STORAGE.resolve(storageKey));
    }

    private long filesOnDisk() {
        Path resources = STORAGE.resolve("resources");
        if (!Files.exists(resources)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(resources)) {
            return files.count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private <T> T inTx(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    private static Path createStorage() {
        try {
            return Files.createTempDirectory("track-cleanup-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
