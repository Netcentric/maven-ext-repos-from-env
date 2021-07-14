[![Maven Central](https://maven-badges.herokuapp.com/maven-central/biz.netcentric.maven.extension/maven-ext-repos-from-env/badge.svg)](https://maven-badges.herokuapp.com/maven-central/biz.netcentric.maven.extension/maven-ext-repos-from-env) ![Java CI/CD](https://github.com/Netcentric/maven-ext-repos-from-env/workflows/Java%20CI/CD/badge.svg) [![License](https://img.shields.io/badge/License-EPL%202.0-red.svg)](https://www.eclipse.org/legal/epl-v20.html)

# Maven Extension: Repos from System Env

This Maven extension allows to add additional remote repositories to the Maven execution by solely using OS level system environment variables (without touching `settings.xml` nor `pom.xml`). Furthermore it allows to place certain artifacts directly under `.mvn/repository` (see details [below](#using-file-repositories)).

While most of the time setting the remote repositories in the `settings.xml` (and potentially also in `pom.xml`) is the recommended approach, for cases where the `settings.xml` is not under the development team's control it can be useful to configure this extension. 

In case the relevant environment variables are not set this extension has no effect (apart from the implicit file repo `.mvn/repository` if it exists). This allows to 

* Minimise the changes in the regular project setup (only the extension has to be added, all mirrors, repositories from `settings.xml` may remain active for local developers or CI servers)
* For constraint build environments (without full control over the `settings.xml` file), the environment variables can be set and for this case the repositories/credentials automatically become active

## Simple Usage

### Step 1: Configure the extension for your repository

In directory `.mvn`, create (or adjust) the file `extensions.xml`:

```
<extensions xmlns="http://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.0.0 http://maven.apache.org/xsd/core-extensions-1.0.0.xsd">
    <extension>
        <groupId>biz.netcentric.maven.extension</groupId>
        <artifactId>maven-ext-repos-from-env</artifactId>
        <version>1.1.0</version>
    </extension>
</extensions>

```
See [Maven documentation](https://maven.apache.org/configure.html#mvn-folder) for reference on how extensions can be activated.

The artifact is available at [Maven Central](https://search.maven.org/search?q=g:biz.netcentric.maven.extension%20AND%20a:maven-ext-repos-from-env).

### Step 2: Setup the environment for your build

#### Remote https repo using credentials

The following environment variables can be set to have the Maven extension above automatically add both the repository (could be otherwise in `pom.xml` or in `settings.xml`) and the authentication for the server (would have to be configured in `settings.xml` without this extension):

```
export MVN_SETTINGS_REPO_URL=https://repo.myorg.com/path/to/repo
export MVN_SETTINGS_REPO_USERNAME=username
export MVN_SETTINGS_REPO_PASSWORD=password
```

#### Remote https repo without authentication

For the case no authentication is necessary, setting only one env variable is sufficient:

```
export MVN_SETTINGS_REPO_URL=https://repo.myorg.com/path/to/repo_no_auth
```

For this case, no virtual `server` entry is generated for this server.

#### Using multiple repositories

It is also possible to use multiple repositories:

```
# REPO_NAME1
export MVN_SETTINGS_REPO_NAME1_URL=https://repo.myorg.com/path/to/repo
export MVN_SETTINGS_REPO_NAME1_USERNAME=username1
export MVN_SETTINGS_REPO_NAME1_PASSWORD=password1
# REPO_NAME2
export MVN_SETTINGS_REPO_NAME2_URL=https://repo.myorg.com/path/to/repo
export MVN_SETTINGS_REPO_NAME2_USERNAME=username2
export MVN_SETTINGS_REPO_NAME2_PASSWORD=password2
```

For this case two repositories and two virtual server entries for are created. The order can be important for performance reasons, the repositories are added in natural order of their names (alphabetical). 

#### Using file repositories

As generally true for Maven repositories, it is also possible to use file urls. To reference a file repository within the build repository itself, use the property `maven.multiModuleProjectDirectory` in the value of `MVN_SETTINGS_REPO_URL`, e.g. `MVN_SETTINGS_REPO_URL=file://${maven.multiModuleProjectDirectory}/vendor1/repository`. If the directory `.mvn/repository` exists, it is automatically added as file repository.

This approach can be useful for parent poms or importing dependencies (scope `import`).

#### Using the verbose logging mode

With the environment variable `MVN_SETTINGS_REPO_LOG_VERBOSE`, some more logging can be activated:

```
export MVN_SETTINGS_REPO_LOG_VERBOSE=true
```

#### Using a mirror repository

By default, the repositories as configured in env are queried **after** the default repositories in settings.xml. For the case that the system env repo is a mirror repository (containing all required artifacts), it can be forced to be used first (before the repositories from settings.xml).

```
export MVN_SETTINGS_ENV_REPOS_FIRST=true
```

Setting this flag is only relevant for performance reasons, the overall build will work with or without this flag.

## Usage with Adobe Experience Manager Cloud Manager

### Step 1: Configure the extension for your repository

Reference the Maven extension in the `.mvn/extensions.xml` file as described above.

### Step 2: Setup the environment for your build using Adobe IO

*Prerequisites:*

* Add the `Cloud Manager API` to your Adobe IO project at [https://console.adobe.io/](https://console.adobe.io/)
  * Add a Service Account (JWT based) by either uploading a public key (from a manually generated private/public key pair) or letting the wizard generate both private and public key for you. In both cases make sure to store the private key in a safe place.
  * Setting environment variables requires [permissions of role `Deployment Manager`](https://www.adobe.io/apis/experiencecloud/cloud-manager/docs.html#!AdobeDocs/cloudmanager-api-docs/master/permissions.md) so make sure that the service account has at least that permission
* Install [aio-cli](https://github.com/adobe/aio-cli/blob/master/README.md#usage)
* Setup Adobe IO CLI in general [Getting Started](https://www.adobe.io/apis/experienceplatform/project-firefly/docs.html#!AdobeDocs/project-firefly/master/getting_started/setup.md)
* Install [aio-cli-plugin-cloudmanager](https://github.com/adobe/aio-cli-plugin-cloudmanager#installation)
* Setup [Adobe IO authentication with Cloud Manager](https://github.com/adobe/aio-cli-plugin-cloudmanager#authentication)

Once everything is set up, the environment variables of the Cloud Manager build can be set as follows:

```
aio cloudmanager:set-pipeline-variables \
   <PIPELINE_ID> \
   --programId=<PROGRAM_ID> \
   --variable \
     MVN_SETTINGS_REPO_URL <REPO_URL> \
     MVN_SETTINGS_REPO_USERNAME <REPO_USER> \
   --secret \
     MVN_SETTINGS_REPO_PASSWORD <REPO_PASSWORD>  
```

The parameters `<PIPELINE_ID>` and `<PROGRAM_ID>` can be derived from URLs when browsing the Cloud Manager. The call needs to be made for each pipeline as set up in cloud manager (all non-prod and the prod pipeline).

See also official [Adobe documentation](https://docs.adobe.com/content/help/en/experience-manager-cloud-service/onboarding/getting-access/creating-aem-application-project.html#pipeline-variables) and [reference on GitHub](https://github.com/adobe/aio-cli-plugin-cloudmanager#aio-cloudmanagerset-pipeline-variables-pipelineid)
