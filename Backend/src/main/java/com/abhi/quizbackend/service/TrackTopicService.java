package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.Topic;
import com.abhi.quizbackend.entity.Track;
import com.abhi.quizbackend.entity.TrackTopic;
import com.abhi.quizbackend.exception.DuplicateResourceException;
import com.abhi.quizbackend.exception.ResourceNotFoundException;
import com.abhi.quizbackend.models.TrackTopicResponse;
import com.abhi.quizbackend.repository.TopicRepository;
import com.abhi.quizbackend.repository.TrackTopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TrackTopicService {

    private final TrackService trackService;
    private final TopicRepository topicRepository;
    private final TrackTopicRepository trackTopicRepository;

    public void addTopic(
            Long trackId,
            Long topicId,
            Integer position,
            Long currentUserId) {

        Track track = trackService.findOwnedTrack(trackId, currentUserId);

        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Topic not found"));

        if (trackTopicRepository
                .existsByTrackTrackIdAndTopicTopicId(trackId, topicId)) {

            throw new DuplicateResourceException(
                    "topicId",
                    "Topic already belongs to this track"
            );
        }

        if (trackTopicRepository
                .existsByTrackTrackIdAndPosition(trackId, position)) {

            throw new DuplicateResourceException(
                    "position",
                    "Another topic already occupies this position"
            );
        }

        TrackTopic trackTopic = TrackTopic.builder()
                .track(track)
                .topic(topic)
                .position(position)
                .build();

        trackTopicRepository.save(trackTopic);
    }

    @Transactional(readOnly = true)
    public List<TrackTopicResponse> getTopics(Long trackId, Long currentUserId) {

        trackService.findReadableTrack(trackId, currentUserId);

        return trackTopicRepository
                .findByTrackTrackIdOrderByPositionAsc(trackId)
                .stream()
                .map(tt -> new TrackTopicResponse(
                        tt.getTopic().getTopicId(),
                        tt.getTopic().getName(),
                        tt.getTopic().getSlug(),
                        tt.getTopic().getDescription(),
                        tt.getPosition()
                ))
                .toList();
    }

    public void removeTopic(
            Long trackId,
            Long topicId,
            Long currentUserId) {

        trackService.findOwnedTrack(trackId, currentUserId);

        TrackTopic trackTopic = trackTopicRepository
                .findByTrackTrackIdAndTopicTopicId(trackId, topicId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Topic is not associated with this track"
                ));

        trackTopicRepository.delete(trackTopic);
    }
}
