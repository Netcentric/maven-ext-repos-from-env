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

/**
 * <p>
 * Adds maven repositories as found in system environment to execution environment.
 * </p>
 * 
 * <p>
 * <strong>This module is used by configuration only (not via Java API), see <a href=
 * "https://github.com/Netcentric/maven-ext-repos-from-env/blob/develop/README.md">https://github.com/Netcentric/maven-ext-repos-from-env/blob/develop/README.md</a>
 * </strong>
 * </p>
 */
@Named("configuration-processor")
public class FromEnvReposConfigurationProcessor implements ConfigurationProcessor {

    static final String ENV_PROP_PREFIX_MVN_SETTINGS_REPO = "MVN_SETTINGS_REPO";
    static final String ENV_PROP_SUFFIX_URL = "_URL";
    static final String ENV_PROP_SUFFIX_USERNAME = "_USERNAME";
    static final String ENV_PROP_SUFFIX_PASSWORD = "_PASSWORD";

    static final String PROFILE_ID_REPOSITORIES_FROM_ENV = "repositoriesFromSysEnv";
    static final String REPO_ID_PREFIX = "sysEnvRepo";

    static final String ENV_PROP_MVN_SETTINGS_REPO_VERBOSE = "MVN_SETTINGS_REPO_LOG_VERBOSE";

    static final String VAR_EXPR_MULTIMODULE_PROJECT_DIR = "${"+MavenCli.MULTIMODULE_PROJECT_DIRECTORY+"}";
    static final String PATH_IMPLICIT_FILE_REPO = ".mvn/repository";

    @Inject
    private Logger logger;

    private boolean isVerbose;

    @Override
    public void process(CliRequest cliRequest) throws Exception {

        Map<String, String> sysEnv = System.getenv();
        isVerbose = Boolean.valueOf(sysEnv.get(ENV_PROP_MVN_SETTINGS_REPO_VERBOSE));
        
        List<RepoFromEnv> reposFromEnv = getReposFromEnv(sysEnv, cliRequest.getMultiModuleProjectDirectory());
        
        addImplicitFileRepo(reposFromEnv, cliRequest.getMultiModuleProjectDirectory());

        configureMavenExecution(cliRequest.getRequest(), reposFromEnv);

    }

    void configureMavenExecution(MavenExecutionRequest request, List<RepoFromEnv> reposFromEnv) {
        if (!reposFromEnv.isEmpty()) {

            logRepositoriesAndMirrors(request);

            Profile repositoriesFromEnv = new Profile();

            for (RepoFromEnv repoFromEnv : reposFromEnv) {

                repositoriesFromEnv.addRepository(getRepository(repoFromEnv));

                repositoriesFromEnv.addPluginRepository(getRepository(repoFromEnv));

                if (repoFromEnv.getUsername() != null) {
                    request.addServer(getServer(repoFromEnv));
                }
            }

            // activate profile
            repositoriesFromEnv.setId(PROFILE_ID_REPOSITORIES_FROM_ENV);
            request.addProfile(repositoriesFromEnv);
            request.addActiveProfile(PROFILE_ID_REPOSITORIES_FROM_ENV);

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
            return repo.getId() + "(" + repo.getUrl() + ",releases:" + repo.getReleases() + ",snapshots:" + repo.getSnapshots() + ")";
        };
        logMessage("Configured in settings.xml:\nrepositores:\n  " +
                repositories.stream().map(repoMapper).collect(Collectors.joining(",  \n"))
                + (pluginRepositories != null && !pluginRepositories.isEmpty()
                        ? "\nplugin repositories:\n  "
                                + pluginRepositories.stream().map(repoMapper).collect(Collectors.joining(",  \n"))
                        : "")
                + (mirrors != null && !mirrors.isEmpty()
                        ? "\nmirrors:\n  "
                                + mirrors.stream().map(
                                        mirror -> mirror.getId() + "(mirrorOf:" + mirror.getMirrorOf() + ",url=" + mirror.getUrl() + ")")
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

    List<RepoFromEnv> getReposFromEnv(Map<String, String> systemEnv, File reactorRootDir) {

        List<RepoFromEnv> reposFromEnv = systemEnv.keySet().stream()
                .filter(Objects::nonNull)
                .filter(key -> key.startsWith(ENV_PROP_PREFIX_MVN_SETTINGS_REPO) && key.endsWith(ENV_PROP_SUFFIX_URL))
                .map(key -> key.substring(ENV_PROP_PREFIX_MVN_SETTINGS_REPO.length(), key.length() - ENV_PROP_SUFFIX_URL.length()))
                .map(repoEnvNameInKey -> {
                    // for the case the short form without like MVN_SETTINGS_REPO_URL is used, repoEnvNameInKey is and empty string
                    // for the case a key like MVN_SETTINGS_REPO_MYCOMP1_URL is used, the id is sysEnvRepoMYCOMP1
                    String id = REPO_ID_PREFIX + (repoEnvNameInKey.isEmpty() ? "" : repoEnvNameInKey.replaceFirst("_", ""));
                    String urlProp = ENV_PROP_PREFIX_MVN_SETTINGS_REPO + repoEnvNameInKey + ENV_PROP_SUFFIX_URL;
                    String url = systemEnv.get(urlProp);
                    String usernameProp = ENV_PROP_PREFIX_MVN_SETTINGS_REPO + repoEnvNameInKey + ENV_PROP_SUFFIX_USERNAME;
                    String username = systemEnv.get(usernameProp);
                    String passwordProp = ENV_PROP_PREFIX_MVN_SETTINGS_REPO + repoEnvNameInKey + ENV_PROP_SUFFIX_PASSWORD;
                    String password = systemEnv.get(passwordProp);
                    if (!isBlank(username) && isBlank(password)) {
                        throw new IllegalArgumentException("If property " + usernameProp + " is set, password property " + passwordProp
                                + " also has to be set along with it");
                    }
                    if (!isBlank(url)) {
                        if (isBlank(username)) {
                            logMessage("Repository " + url + " has NOT configured credentials (env variables " + usernameProp + " and "
                                    + passwordProp + " are missing)");
                        }
                        if(url.contains(VAR_EXPR_MULTIMODULE_PROJECT_DIR)) {
                            String reactorRootDirPath = reactorRootDir.getAbsolutePath();
                            url = url.replace(VAR_EXPR_MULTIMODULE_PROJECT_DIR, reactorRootDirPath);
                            logMessage("Replaced "+VAR_EXPR_MULTIMODULE_PROJECT_DIR+" in url with "+reactorRootDirPath);
                        }
                        
                        return new RepoFromEnv(id, url, username, password);
                    } else {
                        logMessage("Property " + urlProp + " is configured but blank, not adding a repository");
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        reposFromEnv.stream().forEach(repoFromEnv -> 
            // minimal line that we always log directly (regardless of MVN_SETTINGS_REPO_LOG_VERBOSE or -X parameter)
            logger.info("Repository added from system environment variables: " + repoFromEnv.getUrl()  + " (id: " + repoFromEnv.getId() + (repoFromEnv.getUsername() != null ? " user: " + repoFromEnv.getUsername() : "") + ")")
        );
        
        return reposFromEnv;

    }


    void addImplicitFileRepo(List<RepoFromEnv> reposFromEnv, File multiModuleProjectDirectory) {
        File implicitRepo = new File(multiModuleProjectDirectory, PATH_IMPLICIT_FILE_REPO);
        if(implicitRepo.exists()) {
            reposFromEnv.add(new RepoFromEnv(PATH_IMPLICIT_FILE_REPO, implicitRepo.toURI().toString(), null, null));
            logger.info("Implicit file repository added for directory " + PATH_IMPLICIT_FILE_REPO);
        }
    }

    
    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

}
