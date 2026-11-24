package com.roost.controller;

import com.roost.dto.BlockedUserDto;
import com.roost.dto.ReportUserRequestDto;
import com.roost.model.User;
import com.roost.service.UserSafetyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Block, unblock, list blocked users, and report a user. */
@RestController
@RequestMapping("/api/users")
public class UserSafetyController {

    private final UserSafetyService userSafetyService;

    public UserSafetyController(UserSafetyService userSafetyService) {
        this.userSafetyService = userSafetyService;
    }

    /** Blocks the user. Blocking someone already blocked succeeds without change. */
    @PostMapping("/{id}/block")
    public ResponseEntity<Map<String, Boolean>> block(@AuthenticationPrincipal User user, @PathVariable Long id) {
        if (user == null) return ResponseEntity.status(401).build();
        userSafetyService.blockUser(user, id);
        return ResponseEntity.ok(Map.of("blocked", true));
    }

    /** Unblocks the user. Unblocking someone not blocked succeeds without change. */
    @DeleteMapping("/{id}/block")
    public ResponseEntity<Map<String, Boolean>> unblock(@AuthenticationPrincipal User user, @PathVariable Long id) {
        if (user == null) return ResponseEntity.status(401).build();
        userSafetyService.unblockUser(user, id);
        return ResponseEntity.ok(Map.of("blocked", false));
    }

    /** The people the signed-in user has blocked, newest block first. */
    @GetMapping("/me/blocked")
    public ResponseEntity<List<BlockedUserDto>> blocked(@AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(userSafetyService.getBlockedUsers(user));
    }

    /** Reports the user for review. Answers 409 if the same report was filed in the last 24 hours. */
    @PostMapping("/{id}/report")
    public ResponseEntity<Map<String, Boolean>> report(@AuthenticationPrincipal User user,
                                                       @PathVariable Long id,
                                                       @Valid @RequestBody ReportUserRequestDto request) {
        if (user == null) return ResponseEntity.status(401).build();
        userSafetyService.reportUser(user, id, request);
        return ResponseEntity.ok(Map.of("reported", true));
    }
}
