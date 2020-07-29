/*
 * (C) Copyright 2020 Netcentric, A Cognizant Digital Business
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package biz.netcentric.maven.extension.repofromenv;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.cli.CliRequest;
import org.apache.maven.cli.configuration.ConfigurationProcessor;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Repository;
import org.apache.maven.model.RepositoryPolicy;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Server;
import org.codehaus.plexus.logging.Logger;

/** <p>Adds maven repositories as found in system environment to execution environment.</p>
 * 
 *  <p><strong>This module is used by configuration only (not via Java API), 
 * see <a href="https://github.com/Netcentric/maven-ext-repos-from-env/blob/develop/README.md">https://github.com/Netcentric/maven-ext-repos-from-env/blob/develop/README.md</a>
 * </strong></p> */
@Named("configuration-processor")
public class FromEnvReposConfigurationProcessor implements ConfigurationProcessor {

    static final String ENV_PROP_PREFIX_MVN_SETTINGS_REPO = "MVN_SETTINGS_REPO";
    static final String ENV_PROP_SUFFIX_URL = "_URL";
    static final String ENV_PROP_SUFFIX_USERNAME = "_USERNAME";
    static final String ENV_PROP_SUFFIX_PASSWORD = "_PASSWORD";

    static final String PROFILE_ID_REPOSITORIES_FROM_ENV = "repositoriesFromSysEnv";
    static final String REPO_ID_PREFIX = "sysEnvRepo";

    static final String ENV_PROP_MVN_SETTINGS_REPO_VERBOSE = "MVN_SETTINGS_REPO_LOG_VERBOSE";

    @Inject
    private Logger logger;

    private boolean isVerbose;
    
    @Override
    public void process(CliRequest cliRequest) throws Exception {
        
        Map<String, String> sysEnv = System.getenv();
        isVerbose = Boolean.valueOf(sysEnv.get(ENV_PROP_MVN_SETTINGS_REPO_VERBOSE));
        
        List<RepoFromEnv> reposFromEnv = getReposFromEnv(sysEnv);

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
                
                // minimal line that we always log (regardless of MVN_SETTINGS_REPO_LOG_VERBOSE or -X parameter)
                logger.info("Repository from system env: " + repoFromEnv.getUrl() + " (id: "+repoFromEnv.getId() 
                        + (repoFromEnv.getUsername() != null ? " user: " + repoFromEnv.getUsername(): "") + ")");
            }
    
            // activate profile
            repositoriesFromEnv.setId(PROFILE_ID_REPOSITORIES_FROM_ENV);
            request.addProfile(repositoriesFromEnv);
            request.addActiveProfile(PROFILE_ID_REPOSITORIES_FROM_ENV);
            
        }
    }

    private void logVerbose(String msg) {
        if(isVerbose) {
            logger.info(msg);
        } else {
            logger.debug(msg);
        }
    }

    private void logRepositoriesAndMirrors(MavenExecutionRequest request) {

        List<ArtifactRepository> repositories = request.getRemoteRepositories();
        List<ArtifactRepository> pluginRepositories = request.getPluginArtifactRepositories();
        List<Mirror> mirrors = request.getMirrors();
        logVerbose("Configured in settings.xml: repositores: "+
                repositories.stream().map(ArtifactRepository::getId).collect(Collectors.joining(",")) 
                + (pluginRepositories != null && !pluginRepositories.isEmpty() ? 
                        " plugin repositories: "+pluginRepositories.stream().map(ArtifactRepository::getId).collect(Collectors.joining(",")) 
                        : "")
                + (mirrors != null && !mirrors.isEmpty() ? 
                " mirrors: "+mirrors.stream().map(mirror -> mirror.getId() + "("+mirror.getMirrorOf()+","+mirror.getUrl()+")").collect(Collectors.joining(","))
                : ""))
        ;      

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
    
    List<RepoFromEnv> getReposFromEnv(Map<String, String> systemEnv) {

        return systemEnv.keySet().stream()
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
                    if(!isBlank(username) && isBlank(password)) {
                        throw new IllegalArgumentException("If property "+usernameProp+" is set, password property "+passwordProp+ " also has to be set along with it");
                    }
                    if(!isBlank(url)) {
                        if(isBlank(username)) {
                            logVerbose("Repository "+url+" has NOT configured credentials (env variables "+usernameProp+" and "+passwordProp + " are missing)");
                        }
                        return new RepoFromEnv(id, url, username, password);
                    } else {
                        logVerbose("Property "+urlProp+" is configured but blank, not adding a repository");
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

    }

    private boolean isBlank(String str) {
        return str==null || str.trim().isEmpty();
    }

}
