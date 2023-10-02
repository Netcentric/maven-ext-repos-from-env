/*
 * (C) Copyright 2020 Netcentric, A Cognizant Digital Business
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package biz.netcentric.maven.extension.repofromenv;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.collections4.map.CompositeMap;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.cli.CliRequest;
import org.apache.maven.cli.MavenCli;
import org.apache.maven.cli.configuration.ConfigurationProcessor;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Repository;
import org.apache.maven.model.RepositoryPolicy;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Server;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * <p>
 * Adds Maven repositories as found in environment variables or system
 * properties to execution environment.
 * </p>
 * 
 * <p>
 * <strong>This module is used by configuration only (not via Java API), see
 * <a href=
 * "https://github.com/Netcentric/maven-ext-repos-from-env/blob/develop/README.md">https://github.com/Netcentric/maven-ext-repos-from-env/blob/develop/README.md</a>
 * </strong>
 * </p>
 */
@Named("configuration-processor")
public class FromEnvReposConfigurationProcessor implements ConfigurationProcessor {

    static final String KEY_PREFIX_MVN_SETTINGS_REPO = "MVN_SETTINGS_REPO";
    static final String KEY_SUFFIX_URL = "_URL";
    static final String KEY_SUFFIX_USERNAME = "_USERNAME";
    static final String KEY_SUFFIX_PASSWORD = "_PASSWORD";
    static final String KEY_SUFFIX_API_TOKEN = "_API_TOKEN";

    static final String PROFILE_ID_REPOSITORIES_FROM_ENV = "repositoriesFromSysEnv";
    static final String REPO_ID_PREFIX = "sysEnvRepo";

    static final String KEY_MVN_SETTINGS_REPO_VERBOSE = "MVN_SETTINGS_REPO_LOG_VERBOSE";
    static final String KEY_ENV_REPOS_FIRST = "MVN_SETTINGS_ENV_REPOS_FIRST";

    static final String VAR_EXPR_MULTIMODULE_PROJECT_DIR = "${" + MavenCli.MULTIMODULE_PROJECT_DIRECTORY + "}";
    static final String IMPLICIT_FILE_REPO_PATH = ".mvn/repository";
    static final String IMPLICIT_FILE_REPO_ID = "repository-in-mvn-ext-folder";

    static final String KEY_DISABLE_BYPASS_MIRRORS = "MVN_DISABLE_BYPASS_MIRRORS";

    @Inject
    private Logger logger;

    private boolean isVerbose;

    @Override
    public void process(CliRequest cliRequest) throws Exception {
        Map<String, String> systemProperties = System.getProperties().entrySet().stream()
                .collect(Collectors.toMap(e -> (String) e.getKey(), e -> (String) e.getValue()));
        CompositeMap<String, String> configurationMap = new CompositeMap<>(systemProperties, System.getenv());
        isVerbose = Boolean.parseBoolean(configurationMap.get(KEY_MVN_SETTINGS_REPO_VERBOSE));
        boolean envReposFirst = Boolean.parseBoolean(configurationMap.get(KEY_ENV_REPOS_FIRST));
        boolean disableBypassMirrors = Boolean.parseBoolean(configurationMap.get(KEY_DISABLE_BYPASS_MIRRORS));
        List<RepoFromEnv> reposFromEnv = getReposFromConfiguration(configurationMap,
                cliRequest.getMultiModuleProjectDirectory());

        addImplicitFileRepo(reposFromEnv, cliRequest.getMultiModuleProjectDirectory());

        configureMavenExecution(cliRequest.getRequest(), reposFromEnv, disableBypassMirrors, envReposFirst);

    }

    void configureMavenExecution(MavenExecutionRequest request, List<RepoFromEnv> reposFromEnv,
            boolean disableBypassMirrors, boolean envReposFirst) {
        if (!reposFromEnv.isEmpty()) {

            logRepositoriesAndMirrors(request);

            Profile repositoriesFromEnv = new Profile();

            // add the maven repos
            for (RepoFromEnv repoFromEnv : reposFromEnv) {

                repositoriesFromEnv.addRepository(getRepository(repoFromEnv));

                repositoriesFromEnv.addPluginRepository(getRepository(repoFromEnv));

                if (repoFromEnv.getUsername() != null) {
                    request.addServer(getServer(repoFromEnv));
                }
                if (repoFromEnv.getApiToken() != null) {
                    request.addServer(getServerUsingApiToken(repoFromEnv));
                }
            }

            // activate profile
            repositoriesFromEnv.setId(PROFILE_ID_REPOSITORIES_FROM_ENV);
            if (envReposFirst) {
                logMessage("Repos from this extension are queried *before* default repositories from settings.xml ("
                        + KEY_ENV_REPOS_FIRST + "=true)");
                request.addProfile(repositoriesFromEnv);
            } else {
                logMessage("Repos from this extension are queried *after* default repositories from settings.xml ("
                        + KEY_ENV_REPOS_FIRST + "=false)");
                request.getProfiles().add(0, repositoriesFromEnv);
            }

            request.addActiveProfile(PROFILE_ID_REPOSITORIES_FROM_ENV);

            if (!disableBypassMirrors) {
                bypassMirrorsForRepositoryIds(request,
                        reposFromEnv.stream().map(RepoFromEnv::getId).collect(Collectors.toList()));
            }
        }
    }

    private void bypassMirrorsForRepositoryIds(MavenExecutionRequest request, List<String> repositoryIds) {
        String mirrorOfSuffix = repositoryIds.stream().map(id -> "!" + id).collect(Collectors.joining(","));
        for (Mirror mirror : request.getMirrors()) {
            String mirrorOf = mirror.getMirrorOf() != null ? mirror.getMirrorOf() + "," : "";
            mirrorOf += mirrorOfSuffix;
            mirror.setMirrorOf(mirrorOf);
            logMessage("Reconfigured mirror " + mirror.getId() + " to only act as mirror of " + mirrorOf);
        }
    }

    private void logMessage(String msg) {
        if (isVerbose) {
            logger.info(msg);
        } else {
            logger.debug(msg);
        }
    }

    private void logRepositoriesAndMirrors(MavenExecutionRequest request) {

        List<ArtifactRepository> repositories = request.getRemoteRepositories();
        List<ArtifactRepository> pluginRepositories = request.getPluginArtifactRepositories();
        List<Mirror> mirrors = request.getMirrors();
        Function<? super ArtifactRepository, ? extends String> repoMapper = (ArtifactRepository repo) -> {
            return repo.getId() + "(" + repo.getUrl() + ",releases:" + repo.getReleases() + ",snapshots:"
                    + repo.getSnapshots() + ")";
        };
        logMessage(
                "Configured in settings.xml:\nrepositores:\n  "
                        + repositories.stream().map(repoMapper).collect(Collectors.joining(",  \n"))
                        + (pluginRepositories != null && !pluginRepositories.isEmpty() ? "\nplugin repositories:\n  "
                                + pluginRepositories.stream().map(repoMapper).collect(Collectors.joining(",  \n")) : "")
                        + (mirrors != null && !mirrors.isEmpty()
                                ? "\nmirrors:\n  "
                                        + mirrors.stream()
                                                .map(mirror -> mirror.getId() + "(mirrorOf:" + mirror.getMirrorOf()
                                                        + ",url=" + mirror.getUrl() + ")")
                                                .collect(Collectors.joining(",  \n"))
                                : ""));

    }

    private Repository getRepository(RepoFromEnv repoFromEnv) {
        Repository repository = new Repository();
        repository.setId(repoFromEnv.getId());
        repository.setUrl(repoFromEnv.getUrl());

        RepositoryPolicy repositoryPolicyReleases = new RepositoryPolicy();
        repositoryPolicyReleases.setEnabled(true);
        repository.setReleases(repositoryPolicyReleases);
        RepositoryPolicy repositoryPolicySnapshots = new RepositoryPolicy();
        repositoryPolicySnapshots.setEnabled(true);
        repository.setSnapshots(repositoryPolicySnapshots);
        return repository;
    }

    private Server getServer(RepoFromEnv repoFromEnv) {
        Server server = new Server();
        server.setId(repoFromEnv.getId());
        server.setUsername(repoFromEnv.getUsername());
        server.setPassword(repoFromEnv.getPassword());
        return server;
    }

    /**
     * @author Rampai94
     * 
     */
    private Server getServerUsingApiToken(RepoFromEnv repoFromEnv) {
        Server server = new Server();
        Xpp3Dom value = new Xpp3Dom("value");
        value.setValue("Bearer " + repoFromEnv.getApiToken());
        Xpp3Dom name = new Xpp3Dom("name");
        name.setValue("Authorization");
        Xpp3Dom property = new Xpp3Dom("property");
        property.addChild(name);
        property.addChild(value);
        Xpp3Dom httpHeaders = new Xpp3Dom("httpHeaders");
        httpHeaders.addChild(property);
        Xpp3Dom serverConfiguration = new Xpp3Dom("configuration");
        serverConfiguration.addChild(httpHeaders);
        Xpp3Dom wagonProvider = new Xpp3Dom("wagonProvider");
        wagonProvider.setValue("httpClient");
        serverConfiguration.addChild(wagonProvider);
        server.setId(repoFromEnv.getId());
        server.setConfiguration(serverConfiguration);
        return server;
    }

    List<RepoFromEnv> getReposFromConfiguration(Map<String, String> configMap, File reactorRootDir) {

        List<RepoFromEnv> reposFromEnv = configMap.keySet().stream().filter(Objects::nonNull).sorted()
                .filter(key -> key.startsWith(KEY_PREFIX_MVN_SETTINGS_REPO) && key.endsWith(KEY_SUFFIX_URL))
                .map(key -> key.substring(KEY_PREFIX_MVN_SETTINGS_REPO.length(),
                        key.length() - KEY_SUFFIX_URL.length()))
                .map(repoEnvNameInKey -> {
                    // for the case the short form without like MVN_SETTINGS_REPO_URL is used,
                    // repoEnvNameInKey is and empty string
                    // for the case a key like MVN_SETTINGS_REPO_MYCOMP1_URL is used, the id is
                    // sysEnvRepoMYCOMP1
                    String id = REPO_ID_PREFIX
                            + (repoEnvNameInKey.isEmpty() ? "" : repoEnvNameInKey.replaceFirst("_", ""));
                    String urlKey = KEY_PREFIX_MVN_SETTINGS_REPO + repoEnvNameInKey + KEY_SUFFIX_URL;
                    String url = configMap.get(urlKey);
                    String usernameKey = KEY_PREFIX_MVN_SETTINGS_REPO + repoEnvNameInKey + KEY_SUFFIX_USERNAME;
                    String username = configMap.get(usernameKey);
                    String passwordKey = KEY_PREFIX_MVN_SETTINGS_REPO + repoEnvNameInKey + KEY_SUFFIX_PASSWORD;
                    String password = configMap.get(passwordKey);
                    String apiTokenKey = KEY_PREFIX_MVN_SETTINGS_REPO + repoEnvNameInKey + KEY_SUFFIX_API_TOKEN;
                    String apiToken = configMap.get(apiTokenKey);
                    if (!isBlank(username) && isBlank(password)) {
                        throw new IllegalArgumentException("If property " + usernameKey + " is set, password property "
                                + passwordKey + " also has to be set along with it");
                    }
                    if (!isBlank(url)) {
                        if (url.contains(VAR_EXPR_MULTIMODULE_PROJECT_DIR)) {
                            String reactorRootDirPath = reactorRootDir.toURI().getPath(); // only use forward slashes on
                                                                                          // all platforms
                            url = url.replace(VAR_EXPR_MULTIMODULE_PROJECT_DIR, reactorRootDirPath);
                            logMessage("Replaced " + VAR_EXPR_MULTIMODULE_PROJECT_DIR + " in url with "
                                    + reactorRootDirPath);
                        }
                        if (isBlank(username)) {
                            logMessage("Repository " + url + " has NOT configured credentials (env variables "
                                    + usernameKey + " and " + passwordKey + " are missing)");
                        }
                        if (isBlank(apiToken)) {
                            logMessage("Repository " + url + " has NOT configured token (env variable " + apiTokenKey
                                    + " is missing)");
                        } else {
                            return new RepoFromEnv(id, url, apiToken);
                        }
                        return new RepoFromEnv(id, url, username, password);
                    } else {
                        logMessage("Property/Variable " + urlKey + " is configured but blank, not adding a repository");
                        return null;
                    }
                }).filter(Objects::nonNull).collect(Collectors.toList());

        reposFromEnv.stream().forEach(repoFromEnv ->
        // minimal line that we always log directly (regardless of
        // MVN_SETTINGS_REPO_LOG_VERBOSE or -X parameter)
        logger.info(
                "Repository added from system properties or environment variables: " + repoFromEnv.getUrl() + " (id: "
                        + repoFromEnv.getId()
                        + (repoFromEnv.getUsername() != null ? " user: " + repoFromEnv.getUsername()
                                : repoFromEnv.getApiToken() != null ? " token: " + repoFromEnv.getApiToken() : "")
                        + ")"));

        return reposFromEnv;

    }

    void addImplicitFileRepo(List<RepoFromEnv> reposFromEnv, File multiModuleProjectDirectory) {
        File implicitRepo = new File(multiModuleProjectDirectory, IMPLICIT_FILE_REPO_PATH);
        if (implicitRepo.exists()) {
            reposFromEnv.add(0, new RepoFromEnv(IMPLICIT_FILE_REPO_ID, implicitRepo.toURI().toString(), null, null));
            logger.info("Implicit file repository added for directory " + IMPLICIT_FILE_REPO_PATH);
        }
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

}
