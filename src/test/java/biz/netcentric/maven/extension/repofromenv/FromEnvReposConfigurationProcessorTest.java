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
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.cli.CliRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Repository;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Server;
import org.codehaus.plexus.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

class FromEnvReposConfigurationProcessorTest {

    private static final Path PATH_TO_REACTOR_ROOT = Paths.get("path", "to", "reactor", "root");

    @InjectMocks
    private FromEnvReposConfigurationProcessor fromEnvSettingsConfigurationProcessor = new FromEnvReposConfigurationProcessor();

    private Map<String, String> testEnv = new HashMap<>();

    @Mock
    private Logger logger;

    @Mock 
    private MavenExecutionRequest mavenExecutionRequest;

    @Captor
    private ArgumentCaptor<Server> serverCaptor;
    private List<Profile> profiles = new ArrayList<>();

    @TempDir
    Path projectRootDir;

    @BeforeEach
    void setup() {
        initMocks(this);
        when(mavenExecutionRequest.getProfiles()).thenReturn(profiles);
    }

    @Test
    void testGetReposFromEnvOne() {

        String testUrl = "https://repodomain.com/path/to/repo";
        String testUser = "user";
        String testPassword = "pass";
        testEnv.put("MVN_SETTINGS_REPO_URL", testUrl);
        testEnv.put("MVN_SETTINGS_REPO_USERNAME", testUser);
        testEnv.put("MVN_SETTINGS_REPO_PASSWORD", testPassword);

        List<RepoFromEnv> reposFromEnv = fromEnvSettingsConfigurationProcessor.getReposFromConfiguration(testEnv, PATH_TO_REACTOR_ROOT.toFile());
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

        List<RepoFromEnv> reposFromEnv = fromEnvSettingsConfigurationProcessor.getReposFromConfiguration(testEnv, PATH_TO_REACTOR_ROOT.toFile());
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
            fromEnvSettingsConfigurationProcessor.getReposFromConfiguration(testEnv, PATH_TO_REACTOR_ROOT.toFile());
        });
        
    }
    
    @Test
    void testGetReposFromEnvOnlyUserNoPassword() {

        String testUrl = "https://repodomain.com/path/to/repo";
        testEnv.put("MVN_SETTINGS_REPO_URL", testUrl);

        List<RepoFromEnv> reposFromEnv = fromEnvSettingsConfigurationProcessor.getReposFromConfiguration(testEnv, PATH_TO_REACTOR_ROOT.toFile());
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

        List<RepoFromEnv> reposFromEnv = fromEnvSettingsConfigurationProcessor.getReposFromConfiguration(testEnv, PATH_TO_REACTOR_ROOT.toFile());
        assertEquals(0, reposFromEnv.size());
    }

    @Test
    void testGetReposFileUrl() {

        String testUrl = "file://${maven.multiModuleProjectDirectory}/.mvn/repository";
        testEnv.put("MVN_SETTINGS_REPO_URL", testUrl);

        List<RepoFromEnv> reposFromEnv = fromEnvSettingsConfigurationProcessor.getReposFromConfiguration(testEnv, PATH_TO_REACTOR_ROOT.toFile());
        assertEquals(1, reposFromEnv.size());
        assertEquals(PATH_TO_REACTOR_ROOT.toUri()+"/.mvn/repository", reposFromEnv.get(0).getUrl());
    }
    
    @Test
    void testAddImplicitFileRepo() {

        ClassLoader classLoader = getClass().getClassLoader();
        File reactorRootDir = new File(classLoader.getResource("mavenRootDirTest/.mvn/repository/dummy.txt").getFile()).getParentFile()
                .getParentFile().getParentFile();
        List<RepoFromEnv> reposFromEnv = new ArrayList<RepoFromEnv>();
        fromEnvSettingsConfigurationProcessor.addImplicitFileRepo(reposFromEnv, reactorRootDir);
        assertEquals(1, reposFromEnv.size());
        assertEquals(reactorRootDir.toURI() + ".mvn/repository/", reposFromEnv.get(0).getUrl());
    }
    
    @Test
    void testOtherSettings() {

        testEnv.put(null, "test");
        testEnv.put("OTHER_KEY", "value");

        List<RepoFromEnv> reposFromEnv = fromEnvSettingsConfigurationProcessor.getReposFromConfiguration(testEnv, PATH_TO_REACTOR_ROOT.toFile());
        assertEquals(0, reposFromEnv.size());

    }

    @Test
    void testConfigureMavenExecutionNoEnvVars() {
        fromEnvSettingsConfigurationProcessor.configureMavenExecution(mavenExecutionRequest, Collections.emptyList(), false, false);
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

        Mirror mirror1 = new Mirror();
        Mirror mirror2 = new Mirror();
        mirror1.setMirrorOf("test1");
        mirror2.setMirrorOf("*");
        when(mavenExecutionRequest.getMirrors()).thenReturn(Arrays.asList(mirror1, mirror2));
        
        fromEnvSettingsConfigurationProcessor.configureMavenExecution(mavenExecutionRequest, 
                Arrays.asList(
                        new RepoFromEnv(repoId, repoUrl, repoUser, repoPw),
                        new RepoFromEnv(repo2Id, repo2Url, null, null)), false, false);
        
        verify(mavenExecutionRequest, times(1)).addServer(serverCaptor.capture());
        
        assertEquals(1, profiles.size());
        Profile profile = profiles.get(0);
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
        
        assertEquals("test1,!repoId1,!repoId2", mirror1.getMirrorOf());
        assertEquals("*,!repoId1,!repoId2", mirror2.getMirrorOf());

    }

    @Test
    void testProcess() throws Exception {
        CliRequest request = Mockito.mock(CliRequest.class);
        when(request.getRequest()).thenReturn(mavenExecutionRequest);
        when(request.getMultiModuleProjectDirectory()).thenReturn(projectRootDir.toFile());
        
        // configuration set up
        String testUrl = "file://${maven.multiModuleProjectDirectory}.mvn/repository";
        System.setProperty("MVN_SETTINGS_REPO_SPECIAL1_URL", testUrl);
        System.setProperty("MVN_SETTINGS_REPO_SPECIAL1_USERNAME", "user");
        System.setProperty("MVN_SETTINGS_REPO_SPECIAL1_PASSWORD", "password");
        
        fromEnvSettingsConfigurationProcessor.process(request);
        assertEquals(1, profiles.size());
        Profile profile = profiles.get(0);
        assertEquals(FromEnvReposConfigurationProcessor.PROFILE_ID_REPOSITORIES_FROM_ENV, profile.getId());
        assertEquals(1, profile.getRepositories().size());
        Repository repo1 = profile.getRepositories().get(0);
        assertEquals("sysEnvRepoSPECIAL1", repo1.getId());
        assertEquals(projectRootDir.toUri() + ".mvn/repository", repo1.getUrl());
    }
}
