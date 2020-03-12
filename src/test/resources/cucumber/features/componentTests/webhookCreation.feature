@WebHookFeature @ComponentTest
Feature: trying to create a WebHook to github

  Scenario: create a new webhook
    Given organizations were saved to file
    When adding webhook for "GitHub"
    Then webhook created
