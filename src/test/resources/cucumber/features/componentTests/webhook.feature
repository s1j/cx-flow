@WebHookFeature @ComponentTest
Feature: Processing WebHook requests from version control providers

  Scenario: High load on CxFlow due to frequent WebHook requests from GitHub
    Given CxFlow is running as a service
    When receive WebHook requests at a pace of 2 requests times per second
    Then each of the requests is answered in at most 500 ms

