/*
 * (C) Copyright 2020 Netcentric, A Cognizant Digital Business
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package biz.netcentric.maven.extension.repofromenv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Repository;
import org.apache.maven.settings.Server;
import org.codehaus.plexus.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

class FromEnvReposConfigurationProcessorTest {

    @InjectMocks
    private FromEnvReposConfigurationProcessor fromEnvSettingsConfigurationProcessor = new FromEnvReposConfigurationProcessor();

    private Map<String, String> testEnv = new HashMap<>();

    @Mock
    private Logger logger;

    @Mock 
    private MavenExecutionRequest mavenExecutionRequest;
    
    @Captor
    private ArgumentCaptor<Profile> profileCaptor;

    @Captor
    private ArgumentCaptor<Server> serverCaptor;
    
    @BeforeEach
    void setup() {
        initMocks(this);
    }

    @Test
    void testGetReposFromEnvOne() {

        String testUrl = "https://repodomain.com/path/to/repo";
        String testUser = "user";
        String testPassword = "pass";
        testEnv.put("MVN_SETTINGS_REPO_URL", testUrl);
        testEnv.put("MVN_SETTINGS_REPO_USERNAME", testUser);
        testEnv.put("MVN_SETTINGS_REPO_PASSWORD", testPassword);

        List<RepoFromEnv> reposFromEnv = fromEnvSettingsConfigurationProcessor.getReposFromEnv(testEnv);
        assertEquals(1, reposFromEnv.size());
        assertEquals(testUrl, reposFromEnv.get(0).getUrl());
        assertEquals(testUser, reposFromEnv.get(0).getUsername());
        assertEquals(testPassword, reposFromEnv.get(0).getPassword());
        assertEquals(FromEnvReposConfigurationProcessor.REPO_ID_PREFIX, reposFromEnv.get(0).getId());
    }

    @Test
    void testGetReposFromEnvMultiple() {

        String testUrl1 = "https://repodomain.com/path/to/repo";
        String testUrl2 = "https://repodomain.com/path/to/special2";
        String testUser = "user";
        String testPassword = "pass";
        testEnv.put("MVN_SETTINGS_REPO_SPECIAL1_URL", testUrl1);
        testEnv.put("MVN_SETTINGS_REPO_SPECIAL1_USERNAME", testUser);
        testEnv.put("MVN_SETTINGS_REPO_SPECIAL1_PASSWORD", testPassword);
        testEnv.put("MVN_SETTINGS_REPO_SPECIAL2_URL", testUrl2);
        testEnv.put("MVN_SETTINGS_REPO_SPECIAL2_USERNAME", testUser);
        testEnv.put("MVN_SETTINGS_REPO_SPECIAL2_PASSWORD", testPassword);

        List<RepoFromEnv> reposFromEnv = fromEnvSettingsConfigurationProcessor.getReposFromEnv(testEnv);
        assertEquals(2, reposFromEnv.size());
        assertEquals(testUrl1, reposFromEnv.get(0).getUrl());
        assertEquals(testUser, reposFromEnv.get(0).getUsername());
        assertEquals(testPassword, reposFromEnv.get(0).getPassword());
        assertEquals(FromEnvReposConfigurationProcessor.REPO_ID_PREFIX + "SPECIAL1", reposFromEnv.get(0).getId());

        assertEquals(testUrl2, reposFromEnv.get(1).getUrl());
        assertEquals(testUser, reposFromEnv.get(1).getUsername());
        assertEquals(testPassword, reposFromEnv.get(1).getPassword());
        assertEquals(FromEnvReposConfigurationProcessor.REPO_ID_PREFIX + "SPECIAL2", reposFromEnv.get(1).getId());

    }

    @Test
    void testGetReposFromEnvNoUserPw() {

        String testUrl = "https://repodomain.com/path/to/repo";
        testEnv.put("MVN_SETTINGS_REPO_URL", testUrl);
        testEnv.put("MVN_SETTINGS_REPO_USERNAME", "testuser");
        // user set but no password set -> exception

        assertThrows(IllegalArgumentException.class, () -> {
            fromEnvSettingsConfigurationProcessor.getReposFromEnv(testEnv);
        });
        
    }
    
    @Test
    void testGetReposFromEnvOnlyUserNoPassword() {

        String testUrl = "https://repodomain.com/path/to/repo";
        testEnv.put("MVN_SETTINGS_REPO_URL", testUrl);

        List<RepoFromEnv> reposFromEnv = fromEnvSettingsConfigurationProcessor.getReposFromEnv(testEnv);
        assertEquals(1, reposFromEnv.size());
        assertEquals(testUrl, reposFromEnv.get(0).getUrl());
        assertEquals(null, reposFromEnv.get(0).getUsername());
        assertEquals(null, reposFromEnv.get(0).getPassword());
        assertEquals(FromEnvReposConfigurationProcessor.REPO_ID_PREFIX, reposFromEnv.get(0).getId());
    }

    @Test
    void testGetReposFromEnvBlankUrl() {

        String testUrl = "  ";
        testEnv.put("MVN_SETTINGS_REPO_URL", testUrl);

        List<RepoFromEnv> reposFromEnv = fromEnvSettingsConfigurationProcessor.getReposFromEnv(testEnv);
        assertEquals(0, reposFromEnv.size());
    }

    @Test
    void testOtherSettings() {

        testEnv.put(null, "test");
        testEnv.put("OTHER_KEY", "value");

        List<RepoFromEnv> reposFromEnv = fromEnvSettingsConfigurationProcessor.getReposFromEnv(testEnv);
        assertEquals(0, reposFromEnv.size());

    }

    @Test
    void testConfigureMavenExecutionNoEnvVars() {
        fromEnvSettingsConfigurationProcessor.configureMavenExecution(mavenExecutionRequest, Collections.emptyList());
        verifyNoInteractions(mavenExecutionRequest);
    }
    
    @Test
    void testConfigureMavenExecution() {

        String repoId = "repoId1";
        String repoUrl = "https://domain.org/test";
        String repoUser = "user";
        String repoPw = "pw";

        String repo2Id = "repoId2";
        String repo2Url = "https://domain.org/test2";

        fromEnvSettingsConfigurationProcessor.configureMavenExecution(mavenExecutionRequest, 
                Arrays.asList(
                        new RepoFromEnv(repoId, repoUrl, repoUser, repoPw),
                        new RepoFromEnv(repo2Id, repo2Url, null, null)));
        
        verify(mavenExecutionRequest, times(1)).addProfile(profileCaptor.capture());
        verify(mavenExecutionRequest, times(1)).addServer(serverCaptor.capture());
        
        Profile profile = profileCaptor.getValue();
        assertEquals(FromEnvReposConfigurationProcessor.PROFILE_ID_REPOSITORIES_FROM_ENV, profile.getId());
        assertEquals(2, profile.getRepositories().size());
        Repository repo1 = profile.getRepositories().get(0);
        assertEquals(repoId, repo1.getId());
        assertEquals(repoUrl, repo1.getUrl());
        Server server = serverCaptor.getValue();
        assertEquals(repoId, server.getId());
        assertEquals(repoUser, server.getUsername());
        assertEquals(repoPw, server.getPassword());
        
        Repository repo2 = profile.getRepositories().get(1);
        assertEquals(repo2Id, repo2.getId());
        assertEquals(repo2Url, repo2.getUrl());

    }

}
