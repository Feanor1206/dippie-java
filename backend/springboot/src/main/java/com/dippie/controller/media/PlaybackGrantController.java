package com.dippie.controller.media;

import com.dippie.dto.media.PlaybackGrantResponse;
import com.dippie.service.media.PlaybackGrantService;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// endpoint cấp vé phát tạm thời cho thẻ video bên frontend
@RestController
@RequestMapping("/api/media/grant")
public class PlaybackGrantController {

    private final PlaybackGrantService playbackGrantService;

    public PlaybackGrantController(PlaybackGrantService playbackGrantService) {
        this.playbackGrantService = playbackGrantService;
    }

    @PostMapping("/{assetId}")
    public PlaybackGrantResponse grant(@PathVariable String assetId) {
        return playbackGrantService.issueGrant(assetId);
    }
}
