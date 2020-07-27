# Maven Extension: Repos from System Env

This maven extension allows to add remote repositories to be be added to maven execution by solely using OS level system environment variables. 

While most of the time a combination of settings.xml (and potentially pom-configured repositories) are the recommended approach, for cases where the settings.xml is not fully under the development teams control it can be useful to use this extension. 

For the case the relevant environment variables are not set, this extension has no effect. This allows nicely to 

* Minimise the changes in the regular project setup (only the extension has to be added, all mirrors, repositories from `settings.xml` may remain active for local developers or CI servers)
* For constraint build environments (without full control over the `settings.xml` file), the environment variables can be set (and hence the repo/credentials automatically become active)

## Simple Usage

**Step 1: Configure the extension for your repository**

In directory `.mvn`, create (or adjust) the file `extensions.xml`:

```
<extensions xmlns="http://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.0.0 http://maven.apache.org/xsd/core-extensions-1.0.0.xsd">
    <extension>
        <groupId>biz.netcentric.maven.extension</groupId>
        <artifactId>maven-ext-repos-from-env</artifactId>
        <version>1.0.0</version>
    </extension>
</extensions>

```
See [Maven documentation](https://maven.apache.org/configure.html#mvn-folder) for reference on how extensions can be activated.

**Step 2: Setup the environment for your build**

The following environment variables can be set to have the Maven extension above automatically add both the repository (could be otherwise in `pom.xml` or in `settings.xml`) and the authentication for the server (would have to be configured in `settings.xml` without this extension):

```
export MVN_SETTINGS_REPO_URL=https://repo.myorg.com/path/to/repo
export MVN_SETTINGS_REPO_USERNAME=username
export MVN_SETTINGS_REPO_PASSWORD=password
```

For the case no authentication is necessary, setting only one env variable is sufficient:

```
export MVN_SETTINGS_REPO_URL=https://repo.myorg.com/path/to/repo/no_auth
```

For this case, no virtual `server` entry is generated for this server.

It is also possible to use multiple repositories:

```
# REPO_NAME1
export MVN_SETTINGS_REPO_NAME1_URL=https://repo.myorg.com/path/to/repo
export MVN_SETTINGS_REPO_NAME1_USERNAME=username1
export MVN_SETTINGS_REPO_NAME1_PASSWORD=password1
# REPO_NAME2
export MVN_SETTINGS_REPO_NAME2_URL=https://repo.myorg.com/path/to/repo
export MVN_SETTINGS_REPO_NAME2_USERNAME=username2
export MVN_SETTINGS_REPO_NAME12_PASSWORD=password2
```
For this case two repositories and two virutal server entries for are created.

## Use with Adobe Experience Manager Cloud Manager

**Step 1: Configure the extension for your repository**

Add the maven extension to the `.mvn/extensions.xml` file as described above.

**Step 2: Setup the environment for your build using Adobe IO**

*Prerequisites:*

* Add the `Cloud Manager API` to your Adobe IO project at [https://console.adobe.io/](https://console.adobe.io/)
* Install [aio-cli](https://github.com/adobe/aio-cli/blob/master/README.md#usage)
* Setup Adobe IO CLI in general [Getting Started](https://www.adobe.io/apis/experienceplatform/project-firefly/docs.html#!AdobeDocs/project-firefly/master/getting_started/setup.md)
* Install [aio-cli-plugin-cloudmanager](https://github.com/adobe/aio-cli-plugin-cloudmanager#installation)
* Setup [Adobe IO authentication with Cloud Manager](https://github.com/adobe/aio-cli-plugin-cloudmanager#authentication)

Once everything is set up, the environment variables of the cloud manager build can be set as follows:

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

The parameters `<PIPELINE_ID>` and `<PROGRAM_ID>` can be derived from URLs when browsing the cloud manager. The call needs to be made for each pipeline as set up in cloud manager (all non-prod and the prod pipeline).

See also official [Adobe documentation](https://docs.adobe.com/content/help/en/experience-manager-cloud-service/onboarding/getting-access/creating-aem-application-project.html#pipeline-variables) and [reference on GitHub](https://github.com/adobe/aio-cli-plugin-cloudmanager#aio-cloudmanagerset-pipeline-variables-pipelineid)
