/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// [START cloud_search_widget_on_load]
/**
* Load the cloud search widget & auth libraries. Runs after
* the initial gapi bootstrap library is ready.
*/
function onLoad() {
  gapi.load('client:auth2:cloudsearch-widget', initializeApp)
}
// [END cloud_search_widget_on_load]

// [START cloud_search_widget_config]
/**
* Client ID from OAuth credentials.
*/
var clientId = "...apps.googleusercontent.com";

/**
* Full resource name of the search application, such as
* "searchapplications/<your-id>".
*/
var searchApplicationName = "searchapplications/...";
// [END cloud_search_widget_config]

/**
 * Initializes required config parameters from the config.json
 * file.
 * @returns Promise
 */
function loadConfiguration() {
  return fetch('/config.json').then(function(response) {
    return response.json();
  }).then(function(config) {
    this.clientId = config.clientId;
    this.searchApplicationName = config.searchAppId;
    return config;
  });
}

// [START cloud_search_widget_init]
/**
 * Initialize the app after loading the Google API client &
 * Cloud Search widget.
 */
function initializeApp() {
  // Load client ID & search app.
  loadConfiguration().then(function() {
    // Set API version to v1.
    gapi.config.update('cloudsearch.config/apiVersion', 'v1');

    // Build the result container and bind to DOM elements.
    var resultsContainer = new gapi.cloudsearch.widget.resultscontainer.Builder()
      .setSearchApplicationId(searchApplicationName)
      .setSearchResultsContainerElement(document.getElementById('search_results'))
      .setFacetResultsContainerElement(document.getElementById('facet_results'))
      .build();

    // Build the search box and bind to DOM elements.
    var searchBox = new gapi.cloudsearch.widget.searchbox.Builder()
      .setSearchApplicationId(searchApplicationName)
      .setInput(document.getElementById('search_input'))
      .setAnchor(document.getElementById('suggestions_anchor'))
      .setResultsContainer(resultsContainer)
      .build();
  }).then(function() {
    // Init API/oauth client w/client ID.
    return gapi.auth2.init({
        'clientId': clientId,
        'scope': 'https://www.googleapis.com/auth/cloud_search.query'
    });
  });
}
// [END cloud_search_widget_init]
