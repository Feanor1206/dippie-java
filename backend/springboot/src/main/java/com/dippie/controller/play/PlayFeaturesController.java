package com.dippie.controller.play;

import com.dippie.common.web.ApiResponse;
import com.dippie.dto.play.LootDropAnswerRequest;
import com.dippie.dto.play.LootDropAnswerResponse;
import com.dippie.dto.play.LootDropMissRequest;
import com.dippie.dto.play.LootDropOpenRequest;
import com.dippie.dto.play.LootDropOpenResponse;
import com.dippie.dto.play.LootDropSpawnResponse;
import com.dippie.dto.play.LootStatusView;
import com.dippie.dto.play.PlayFeaturesStatusResponse;
import com.dippie.dto.play.RecapQuizAnswerRequest;
import com.dippie.dto.play.RecapQuizAnswerResponse;
import com.dippie.dto.play.RecapQuizResponse;
import com.dippie.dto.play.WatchPetMedicineAnswerRequest;
import com.dippie.dto.play.WatchPetMedicineAnswerResponse;
import com.dippie.dto.play.WatchPetMedicineChallengeResponse;
import com.dippie.dto.play.WatchPetStatusView;
import com.dippie.service.play.LootDropService;
import com.dippie.service.play.PlayFeaturesQueryService;
import com.dippie.service.play.RecapQuizService;
import com.dippie.service.play.WatchPetService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// /api/users/play-features phục vụ thú ảo nghỉ mắt, rương báu bay ngang và câu hỏi ôn tập qua tập cho chế độ trẻ em
@RestController
@RequestMapping("/api/users/play-features")
public class PlayFeaturesController {

    private final PlayFeaturesQueryService queryService;
    private final WatchPetService watchPetService;
    private final LootDropService lootDropService;
    private final RecapQuizService recapQuizService;

    public PlayFeaturesController(
            PlayFeaturesQueryService queryService,
            WatchPetService watchPetService,
            LootDropService lootDropService,
            RecapQuizService recapQuizService
    ) {
        this.queryService = queryService;
        this.watchPetService = watchPetService;
        this.lootDropService = lootDropService;
        this.recapQuizService = recapQuizService;
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<PlayFeaturesStatusResponse>> status(
            @RequestParam(required = false) String seriesId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.getStatus(seriesId)));
    }

    @PostMapping("/watch-pet/rest")
    public ResponseEntity<ApiResponse<WatchPetStatusView>> startPetRest() {
        return ResponseEntity.ok(ApiResponse.ok(
                watchPetService.startRest(),
                "Thú ảo đang đi ngủ nghỉ mắt rồi, bé thư giãn mắt nhé!"
        ));
    }

    @GetMapping("/watch-pet/medicine")
    public ResponseEntity<ApiResponse<WatchPetMedicineChallengeResponse>> getMedicineChallenge() {
        return ResponseEntity.ok(ApiResponse.ok(watchPetService.issueMedicineChallenge()));
    }

    @PostMapping("/watch-pet/medicine")
    public ResponseEntity<ApiResponse<WatchPetMedicineAnswerResponse>> answerMedicineChallenge(
            @RequestBody(required = false) WatchPetMedicineAnswerRequest request
    ) {
        WatchPetService.MedicineOutcome outcome = watchPetService.submitMedicineAnswer(request);
        return ResponseEntity.ok(ApiResponse.ok(outcome.response(), outcome.message()));
    }

    @PostMapping("/loot/spawn")
    public ResponseEntity<ApiResponse<LootDropSpawnResponse>> spawnLootDrop() {
        return ResponseEntity.ok(ApiResponse.ok(lootDropService.spawnDrop()));
    }

    @PostMapping("/loot/open")
    public ResponseEntity<ApiResponse<LootDropOpenResponse>> openLootDrop(
            @RequestBody(required = false) LootDropOpenRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(lootDropService.openDrop(request)));
    }

    @PostMapping("/loot/answer")
    public ResponseEntity<ApiResponse<LootDropAnswerResponse>> answerLootDrop(
            @RequestBody(required = false) LootDropAnswerRequest request
    ) {
        LootDropService.LootAnswerOutcome outcome = lootDropService.answerDrop(request);
        return ResponseEntity.ok(ApiResponse.ok(outcome.response(), outcome.message()));
    }

    @PostMapping("/loot/miss")
    public ResponseEntity<ApiResponse<LootStatusView>> missLootDrop(
            @RequestBody(required = false) LootDropMissRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(lootDropService.reportMiss(request)));
    }

    @GetMapping("/recap/quiz")
    public ResponseEntity<ApiResponse<RecapQuizResponse>> getRecapQuiz(@RequestParam String episodeId) {
        return ResponseEntity.ok(ApiResponse.ok(recapQuizService.createQuiz(episodeId)));
    }

    @PostMapping("/recap/answer")
    public ResponseEntity<ApiResponse<RecapQuizAnswerResponse>> answerRecapQuiz(
            @RequestBody(required = false) RecapQuizAnswerRequest request
    ) {
        RecapQuizService.RecapAnswerOutcome outcome = recapQuizService.submitAnswer(request);
        return ResponseEntity.ok(ApiResponse.ok(outcome.response(), outcome.message()));
    }
}
