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
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.plexus.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import biz.netcentric.maven.extension.repofromenv.FromEnvReposConfigurationProcessor;
import biz.netcentric.maven.extension.repofromenv.RepoFromEnv;

class FromEnvReposConfigurationProcessorTest {

    @InjectMocks
    private FromEnvReposConfigurationProcessor fromEnvSettingsConfigurationProcessor = new FromEnvReposConfigurationProcessor();

    private Map<String, String> testEnv = new HashMap<>();

    @Mock
    private Logger logger;

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

        List<RepoFromEnv> reposFromEnv = fromEnvSettingsConfigurationProcessor.getReposFromEnv(testEnv);
        assertEquals(1, reposFromEnv.size());
        assertEquals(testUrl, reposFromEnv.get(0).getUrl());
        assertEquals(null, reposFromEnv.get(0).getUsername());
        assertEquals(null, reposFromEnv.get(0).getPassword());
        assertEquals(FromEnvReposConfigurationProcessor.REPO_ID_PREFIX, reposFromEnv.get(0).getId());

    }

    @Test
    void testOtherSettings() {

        testEnv.put(null, "test");
        testEnv.put("OTHER_KEY", "value");

        List<RepoFromEnv> reposFromEnv = fromEnvSettingsConfigurationProcessor.getReposFromEnv(testEnv);
        assertEquals(0, reposFromEnv.size());

    }

}
