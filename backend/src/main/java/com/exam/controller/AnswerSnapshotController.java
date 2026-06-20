package com.exam.controller;

import com.exam.dto.AnswerSnapshotDTO;
import com.exam.dto.ReplayDataDTO;
import com.exam.entity.AnswerSnapshot;
import com.exam.service.AnswerSnapshotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/snapshots")
public class AnswerSnapshotController {

    private final AnswerSnapshotService snapshotService;

    public AnswerSnapshotController(AnswerSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @PostMapping
    public ResponseEntity<?> createSnapshot(@RequestBody AnswerSnapshotDTO dto, Principal principal) {
        try {
            AnswerSnapshot snapshot = snapshotService.createSnapshot(dto, principal.getName());
            if (snapshot == null) {
                return ResponseEntity.ok(Map.of("message", "No changes detected, snapshot skipped"));
            }
            return ResponseEntity.ok(Map.of("id", snapshot.getId(), "success", true));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/replay/{submissionId}")
    public ResponseEntity<?> getReplayData(@PathVariable Long submissionId, Principal principal) {
        try {
            ReplayDataDTO data = snapshotService.getReplayData(submissionId, principal.getName());
            return ResponseEntity.ok(data);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/can-view/{submissionId}")
    public ResponseEntity<?> canViewReplay(@PathVariable Long submissionId, Principal principal) {
        boolean canView = snapshotService.canViewReplay(submissionId, principal.getName());
        return ResponseEntity.ok(Map.of("canView", canView));
    }
}
