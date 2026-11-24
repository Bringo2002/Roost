package com.roost.service;

import com.roost.dto.BlockedUserDto;
import com.roost.dto.ReportUserRequestDto;
import com.roost.exception.ApiException;
import com.roost.model.User;
import com.roost.model.UserBlock;
import com.roost.model.UserReport;
import com.roost.repository.UserBlockRepository;
import com.roost.repository.UserReportRepository;
import com.roost.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Blocking and reporting other users. A block is one-way and is enforced by
 * {@link ChatService}; a report is only stored for a person to review.
 */
@Service
public class UserSafetyService {

    private static final Logger log = LoggerFactory.getLogger(UserSafetyService.class);

    /** The same person cannot report the same user again inside this window. */
    static final Duration DUPLICATE_REPORT_WINDOW = Duration.ofHours(24);

    private final UserBlockRepository userBlockRepository;
    private final UserReportRepository userReportRepository;
    private final UserRepository userRepository;

    public UserSafetyService(UserBlockRepository userBlockRepository,
                             UserReportRepository userReportRepository,
                             UserRepository userRepository) {
        this.userBlockRepository = userBlockRepository;
        this.userReportRepository = userReportRepository;
        this.userRepository = userRepository;
    }

    /**
     * Blocks a user. Blocking someone who is already blocked does nothing.
     *
     * @param blocker  the signed-in user doing the blocking
     * @param targetId the id of the user to block
     * @throws ApiException 400 when blocking yourself; 404 when the user does not exist
     */
    @Transactional
    public void blockUser(User blocker, Long targetId) {
        User target = requireOtherUser(blocker, targetId, "You can't block yourself");
        if (!userBlockRepository.existsByBlockerAndBlocked(blocker, target)) {
            userBlockRepository.save(new UserBlock(blocker, target));
            log.info("User {} blocked user {}", blocker.getId(), target.getId());
        }
    }

    /**
     * Removes a block. Unblocking someone who is not blocked does nothing.
     *
     * @param blocker  the signed-in user
     * @param targetId the id of the user to unblock
     * @throws ApiException 404 when the user does not exist
     */
    @Transactional
    public void unblockUser(User blocker, Long targetId) {
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (userBlockRepository.deleteByPair(blocker, target) > 0) {
            log.info("User {} unblocked user {}", blocker.getId(), target.getId());
        }
    }

    /**
     * Lists the people a user has blocked, newest block first.
     *
     * @param blocker the signed-in user
     * @return the blocked people, empty when there are none
     */
    @Transactional(readOnly = true)
    public List<BlockedUserDto> getBlockedUsers(User blocker) {
        return userBlockRepository.findWithBlockedByBlocker(blocker).stream()
                .map(BlockedUserDto::from)
                .toList();
    }

    /**
     * Files a report against a user. The note is trimmed and a blank note is
     * stored as no note. The same person cannot report the same user twice
     * within {@link #DUPLICATE_REPORT_WINDOW}.
     *
     * @param reporter the signed-in user filing the report
     * @param targetId the id of the user being reported
     * @param request  the reason and optional note
     * @throws ApiException 400 for a missing reason, an overlong note or reporting yourself;
     *                      404 when the user does not exist; 409 for a duplicate report
     */
    @Transactional
    public void reportUser(User reporter, Long targetId, ReportUserRequestDto request) {
        if (request == null || request.getReason() == null) {
            throw ApiException.badRequest("Reason is required");
        }
        String note = normalizeNote(request.getNote());
        if (note != null && note.length() > UserReport.MAX_NOTE_LENGTH) {
            throw ApiException.badRequest("Note cannot exceed " + UserReport.MAX_NOTE_LENGTH + " characters");
        }

        User target = requireOtherUser(reporter, targetId, "You can't report yourself");

        LocalDateTime since = LocalDateTime.now().minus(DUPLICATE_REPORT_WINDOW);
        if (userReportRepository.countByReporterAndReportedAndCreatedAtAfter(reporter, target, since) > 0) {
            throw ApiException.conflict("You've already reported this user. Our team will review it.");
        }

        userReportRepository.save(new UserReport(reporter, target, request.getReason(), note));
        log.info("User {} reported user {} for {}", reporter.getId(), target.getId(), request.getReason());
    }

    private User requireOtherUser(User actor, Long targetId, String selfMessage) {
        if (actor.getId().equals(targetId)) {
            throw ApiException.badRequest(selfMessage);
        }
        return userRepository.findById(targetId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private static String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
