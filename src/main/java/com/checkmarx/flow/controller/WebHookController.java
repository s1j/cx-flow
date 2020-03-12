package com.checkmarx.flow.controller;

import com.checkmarx.flow.dto.EventResponse;
import com.checkmarx.flow.service.WebHookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Paths;
import java.util.Map;


@RestController
@RequestMapping(value = "/webhook")
public class WebHookController {

    @Autowired
    private WebHookService webHookService;

    @PostMapping(value = {"/organizations"})
    public void saveOrgToFile(@RequestParam(value = "url", required = false) String url,
                              @RequestParam(value = "token", required = false) String token,
                              @RequestParam(value = "pathToOrgFile", required = false) String pathToOrgFile) {
        webHookService.createOrganizationFile(url, token, Paths.get(pathToOrgFile));


    }

    @PostMapping(value = {"/{type}", "/"})
    public ResponseEntity<EventResponse> createWebHook(
            @PathVariable(value = "type", required = false) String type,
            @RequestParam(value = "bugTrackerUrl", required = false) String bugTrackerUrl,
            @RequestParam(value = "token", required = false) String token,
            @RequestParam(value = "pathToFile", required = false) String fileToRead) {
        int hookId = 0;
        if (type != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(EventResponse.builder()
                    .message("Type" + type + " is not supported")
                    .success(false)
                    .build());
        }
        Map<String, String> failedWebHooks = webHookService.generateOrgPushWebHook(bugTrackerUrl, token, Paths.get(fileToRead));
        if (failedWebHooks != null && !failedWebHooks.isEmpty()) {
            StringBuilder failedWebHooksMsg = new StringBuilder("The following webHook failed: \n");
            for (String failedWebHook : failedWebHooks.keySet()) {
                failedWebHooksMsg.append(failedWebHook).append(" with error message: ").append(failedWebHooks.get(failedWebHook)).append("\n");
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(EventResponse.builder()
                    .message(failedWebHooksMsg.toString())
                    .success(false)
                    .build());
        }

        return ResponseEntity.status(HttpStatus.OK).body(EventResponse.builder()
                .message(String.format("WebHooks for %s were successfully added with hookId %s", type, hookId))
                .success(true)
                .build());


    }
}
