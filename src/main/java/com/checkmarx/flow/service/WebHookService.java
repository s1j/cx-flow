package com.checkmarx.flow.service;

import com.checkmarx.flow.dto.github.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WebHookService {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(WebHookService.class);
    private static String ADMIN = "admin";

    ObjectMapper mapper = new ObjectMapper();

    public void createOrganizationFile(String url, String token, Path pathToOrgFile) {
        JSONArray orgResults = getOrgList(url, token);
        try {
            Membership[] Memberships = new ObjectMapper().readValue(orgResults.toString(), Membership[].class);
            mapper.writeValue(pathToOrgFile.toFile(), Memberships);
        } catch (IOException e) {
            log.error("Could not write to file" + e.getMessage());
        }

    }

    public void createRepositoriesFile(String url, String token, Path orgFileToRead, Path reposFileToWrite) {
        List<RepositoryByOrganization> reposByOrgList = new ArrayList<>();

        try {
            InputStream input = new FileInputStream(ResourceUtils.getFile(orgFileToRead.toUri()));

            Membership[] orgs = mapper.readValue(input, Membership[].class);
            for (Membership org : orgs) {
                String orgUrl = org.getOrgUrl();
                if (!org.getRole().equals(ADMIN)) {
                    log.error("Cannot add webhook to org " + orgUrl + ", user is not admin for the organization");
                } else {
                    JSONArray orgResults = getReposForOrgList(url, org.getOrganization().getLogin(), token);
                    Repository[] repositoriesList = new ObjectMapper().readValue(orgResults.toString(), Repository[].class);
                    reposByOrgList.add(new RepositoryByOrganization(org.getOrgUrl(), Arrays.stream(repositoriesList).map(repo -> repo.getUrl()).collect(Collectors.toList())));
                }
            }
        } catch (FileNotFoundException e) {
            log.error("Could not read from file " + orgFileToRead + e.getMessage());
            return;
        } catch (IOException e) {
            log.error("Problem reading organization file content " + e.getMessage());
        }

        try {
            mapper.writeValue(reposFileToWrite.toFile(), reposByOrgList);
        } catch (IOException e) {
            log.error("Could not write to file " + reposFileToWrite + e.getMessage());
        }
    }

    private JSONArray getReposForOrgList(String url, String orgName, String token) {
        String repoForOrgApiUrl = String.format("%s/orgs/%s/repos", url, orgName);

        return getJSONArray(repoForOrgApiUrl, token);
    }

    private JSONArray getOrgList(String url, String token) {
        String orgApiUrl = String.format("%s/user/memberships/orgs", url);

        return getJSONArray(orgApiUrl, token);
    }

    public Map<String, String> generateOrgPushWebHook(String bugTrackerUrl, String token, Path fileToRead) {
        Map<String, String> failedWebHooks = new HashMap<>();
        try {
            InputStream input = new FileInputStream(ResourceUtils.getFile(fileToRead.toUri()));
            Membership[] orgs = mapper.readValue(input, Membership[].class);

            for (Membership org : orgs) {
                String orgUrl = org.getOrgUrl();
                if (!org.getRole().equals(ADMIN)) {
                    failedWebHooks.put(orgUrl, "Cannot add webhook to org " + orgUrl + ", user is not admin for the organization");
                } else {
                    try {
                        createWebHookForUrl(orgUrl, bugTrackerUrl, token);
                    } catch (RestClientException e) {
                        failedWebHooks.put(orgUrl, "rest call was not successful " + e.getMessage());
                    } catch (Exception e) {
                        failedWebHooks.put(orgUrl, "repository already has hooks configured");
                    }
                }

            }
        } catch (FileNotFoundException e) {
            log.error("Could not read from file " + fileToRead + e.getMessage());
        } catch (IOException e) {
            log.error("Problem reading organization file content " + e.getMessage());
        }
        return failedWebHooks;
    }

    public Map<String, String> generateRepoPushWebHook(String bugTrackerUrl, String token, Path fileToRead) {
        Map<String, String> failedWebHooks = new HashMap<>();
        try {
            InputStream input = new FileInputStream(ResourceUtils.getFile(fileToRead.toUri()));
            RepositoryByOrganization[] reposByOrgs = mapper.readValue(input, RepositoryByOrganization[].class);

            for (RepositoryByOrganization reposByOrg : reposByOrgs) {
                for (String repo : reposByOrg.getRepos()) {
                    try {
                        createWebHookForUrl(repo, bugTrackerUrl, token);
                    } catch (RestClientException e) {
                        failedWebHooks.put(repo, "rest call was not successful " + e.getMessage());
                    } catch (Exception e) {
                        failedWebHooks.put(repo, "repository already has hooks configured");
                    }
                }
            }
        } catch (FileNotFoundException e) {
            log.error("Could not read from file " + fileToRead + e.getMessage());
        } catch (IOException e) {
            log.error("Problem reading repositories file content " + e.getMessage());
        }
        return failedWebHooks;
    }

    public void createWebHookForUrl(String Url, String bugTrackerUrl, String token) throws Exception {
        final RestTemplate restTemplate = new RestTemplate();
        final HttpHeaders headers = getHeaders(token);
        Hook data = generateHookData(bugTrackerUrl, token);
        final HttpEntity<Hook> request = new HttpEntity<>(data, headers);

        String repoHooksBaseUrl = String.format("%s/hooks", Url);
        if (checkHookExist(repoHooksBaseUrl, token)) {
            throw new Exception("repository already has hooks configured");
        }
        restTemplate.postForEntity(repoHooksBaseUrl, request, String.class);
    }

    public boolean checkHookExist(String repoHooksUrl, String token) {
        JSONArray hooks = getJSONArray(repoHooksUrl, token);
        return hooks != null && !hooks.isEmpty();
    }

    private HttpHeaders getHeaders(String token) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "token " + token);
        return headers;
    }

    private JSONArray getJSONArray(String uri, String token) {
        ResponseEntity<String> response = doExchange(uri, token);
        String body = response.getBody();
        if (body == null) {
            return null;
        }
        return new JSONArray(body);
    }

    private ResponseEntity<String> doExchange(String uri, String token) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = getHeaders(token);
        HttpEntity<String> requestEntity = new HttpEntity<>(null, headers);
        return restTemplate.exchange(uri, HttpMethod.GET, requestEntity, String.class);
    }

    private Hook generateHookData(String url, String secret) {
        Hook hook = new Hook();
        hook.setName("web");
        hook.setActive(true);
        hook.setEvents(Arrays.asList("push", "pull_request"));
        Config config = new Config();
        config.setUrl(url);
        config.setContentType("json");
        config.setInsecureSsl("0");
        config.setSecret(secret);
        hook.setConfig(config);
        return hook;
    }
}
