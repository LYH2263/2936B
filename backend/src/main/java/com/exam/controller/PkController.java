package com.exam.controller;

import com.exam.dto.PkAnswerRequest;
import com.exam.dto.PkRankingItem;
import com.exam.dto.PkStateResponse;
import com.exam.entity.PkAnswer;
import com.exam.entity.PkSession;
import com.exam.service.PkService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pk")
public class PkController {

    private final PkService pkService;

    public PkController(PkService pkService) {
        this.pkService = pkService;
    }

    @PostMapping("/match")
    public ResponseEntity<PkSession> createMatch(Principal principal) {
        PkSession session = pkService.createMatch(principal.getName());
        return ResponseEntity.ok(session);
    }

    @PostMapping("/bot-match")
    public ResponseEntity<PkSession> createBotMatch(Principal principal) {
        PkSession session = pkService.createBotMatch(principal.getName());
        return ResponseEntity.ok(session);
    }

    @PostMapping("/cancel")
    public ResponseEntity<PkSession> cancelMatch(Principal principal) {
        PkSession session = pkService.cancelMatch(principal.getName());
        return ResponseEntity.ok(session);
    }

    @GetMapping("/session/{id}")
    public ResponseEntity<PkStateResponse> getSessionState(@PathVariable Long id, Principal principal) {
        PkStateResponse state = pkService.getState(id, principal.getName());
        return ResponseEntity.ok(state);
    }

    @PostMapping("/session/{id}/answer")
    public ResponseEntity<PkAnswer> submitAnswer(@PathVariable Long id,
                                                  @RequestBody PkAnswerRequest request,
                                                  Principal principal) {
        PkAnswer answer = pkService.submitAnswer(id, principal.getName(), request);
        return ResponseEntity.ok(answer);
    }

    @PostMapping("/session/{id}/forfeit")
    public ResponseEntity<PkSession> forfeit(@PathVariable Long id, Principal principal) {
        PkSession session = pkService.forfeit(principal.getName());
        return ResponseEntity.ok(session);
    }

    @GetMapping("/ranking/weekly")
    public ResponseEntity<List<PkRankingItem>> getWeeklyRanking() {
        List<PkRankingItem> rankings = pkService.getWeeklyRanking();
        return ResponseEntity.ok(rankings);
    }
}
