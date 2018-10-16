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
// [START cloud_search_widget_custom_facet]
/**
 * Results container adapter that intercepts requests to dynamically
 * change which sources are enabled based on user selection.
 */
function ResultsContainerAdapter() {
  this.selectedSource = null;
}

ResultsContainerAdapter.prototype.createFacetResultElement = function(result) {
  // container for the facet
  var container = document.createElement('div');

  // Add a label describing the facet (operator/property)
  var label = document.createElement('div')
  label.classList.add('facet_label');
  label.textContent = result.operatorName;
  container.appendChild(label);

  // Add each bucket
  for(var i in result.buckets) {
    var bucket = document.createElement('div');
    bucket.classList.add('cloudsearch_facet_bucket_container');

    // Extract & render value from structured value
    // Note: implementation of renderValue() not shown
    var bucketValue = this.renderValue(result.buckets[i].value)
    var link = document.createElement('a');
    link.classList.add('cloudsearch_facet_bucket_clickable');
    link.textContent = bucketValue;
    bucket.appendChild(link);
    container.appendChild(bucket);
  }
  return container;
}

// Renders a value for user display
ResultsContainerAdapter.prototype.renderValue = function(value) {
  // [START_EXCLUDE]
  if (value.stringValue !== undefined) {
    return value.stringValue;
  } else if (value.integerValue !== undefined) {
    return value.integerValue.toString(10);
  } else if (value.doubleValue !== undefined) {
    return value.doubleValue.toString(10);
  } else if (value.timestampValue !== undefined) {
    return value.timestampValue;
  } else if (value.booleanValue !== undefined) {
    return value.booleanValue ? 'True' : 'False';
  } else if (value.dateValue !== undefined) {
    return value.dateValue.year +
     '-' + value.dateValue.month +
      '-' + value.dateValue.day;
  } else {
    throw 'No value present';
  }
  // [END_EXCLUDE]
}
// [END cloud_search_widget_custom_facet]

/**
 * Initialize the app after loading the Google API client &
 * Cloud Search widget.
 */
function initializeApp() {
  // Load client ID & search app.
  loadConfiguration().then(function() {
    // Set API version to v1.
    gapi.config.update('cloudsearch.config/apiVersion', 'v1');

    var resultsContainerAdapter = new ResultsContainerAdapter();
    // Build the result container and bind to DOM elements.
    var resultsContainer = new gapi.cloudsearch.widget.resultscontainer.Builder()
      .setSearchApplicationId(searchApplicationName)
      .setAdapter(resultsContainerAdapter)
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
