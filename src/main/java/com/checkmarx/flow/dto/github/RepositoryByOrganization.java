package com.checkmarx.flow.dto.github;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RepositoryByOrganization {
    @JsonProperty("org_url")
    String org_url;
    @JsonProperty("repos")
    List<String> repos;

    public String getOrg_url() {
        return org_url;
    }

    public void setOrg_url(String org_url) {
        this.org_url = org_url;
    }

    public List<String> getRepos() {
        return repos;
    }

    public void setRepos(List<String> repos) {
        this.repos = repos;
    }

    public RepositoryByOrganization() {
    }

    public RepositoryByOrganization(String org_url, List<String> repos) {
        this.org_url = org_url;
        this.repos = repos;
    }
}
