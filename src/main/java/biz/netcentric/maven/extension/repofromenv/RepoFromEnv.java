/*
 * (C) Copyright 2020 Netcentric, A Cognizant Digital Business
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package biz.netcentric.maven.extension.repofromenv;

class RepoFromEnv {

    private final String id;
    private final String url;
    private final String username;
    private final String password;
    private final String apiToken;

    public RepoFromEnv(String id, String url, String username, String password) {
        this.id = id;
        this.url = url;
        this.username = username;
        this.password = password;
        this.apiToken = null;
    }
    
    public RepoFromEnv(String id, String url, String apiToken) {
        this.id = id;
        this.url = url;
        this.username = null;
        this.password = null;
        this.apiToken = apiToken;
    }

    public String getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
    
    public String getApiToken() {
        return apiToken;
    }

}