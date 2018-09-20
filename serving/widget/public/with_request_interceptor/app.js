/**
 * Load the cloud search widget & auth libraries. Runs after
 * the initial gapi bootstrap library is ready.
 */
function onLoad() {
  gapi.load('client:auth2:cloudsearch-widget', initializeApp)
}

/**
 * Results container adapter that intercepts requests to dynamically
 * change which sources are enabled based on user selection.
 */
function ResultsContainerAdapter() {
  this.selectedSource = null;
}
ResultsContainerAdapter.prototype.interceptSearchRequest = function(request) {
  if (this.selectedSource == null) {
    // Source did not change, leave request unmodified.
    return request;
  }
  if (this.selectedSource == 'ALL') {
    // Everything selected, fall back to sources defined in the search
    // application.
    request.dataSourceRestrictions = null;
  } else {
    // Restrict to a single selected source.
    request.dataSourceRestrictions = [
      {
        source: {
          predefinedSource: this.selectedSource
        }
      }
    ];
  }
  // Erase the source slection so the request is only modified when the value
  // changed. Prevents the interceptor from accidentally erasing additional
  // filters that might be applied from facets.
  this.selectedSource = null;
  return request;
}

/**
 * Initialize the app after loading the Google API client &
 * Cloud Search widget.
 */
function initializeApp() {
  // Load client ID & search app.
  fetch('/config.json').then(function(response) {
    return response.json();
  }).then(function(searchConfig) {
    // Set API version to v1.
    gapi.config.update('cloudsearch.config/apiVersion', 'v1');

    var resultsContainerAdapter = new ResultsContainerAdapter();
    // Build the result container and bind to DOM elements.
    var resultsContainer = new gapi.cloudsearch.widget.resultscontainer.Builder()
      .setSearchApplicationId(searchConfig.searchAppId)
      .setAdapter(resultsContainerAdapter)
      .setSearchResultsContainerElement(document.getElementById('search_results'))
      .setFacetResultsContainerElement(document.getElementById('facet_results'))
      .build();

    // Build the search box and bind to DOM elements.
    var searchBox = new gapi.cloudsearch.widget.searchbox.Builder()
      .setSearchApplicationId(searchConfig.searchAppId)
      .setInput(document.getElementById('search_input'))
      .setAnchor(document.getElementById('suggestions_anchor'))
      .setResultsContainer(resultsContainer)
      .build();

    // Handle source selection
    document.getElementById('sources').onchange = (e) => {
      resultsContainerAdapter.selectedSource = e.target.value;
      let request = resultsContainer.getCurrentRequest();
      if (request.query) {
        // Re-execute if there's a valid query. The source selection
        // will be applied in the interceptor.
        resultsContainer.resetState();
        resultsContainer.executeRequest(request);
      }
    }

    return searchConfig;
  }).then(function(searchConfig) {
    // Init API/oauth client w/client ID.
    return gapi.auth2.init({
        'clientId': searchConfig.clientId,
        'scope': 'https://www.googleapis.com/auth/cloud_search.query'
    });
  });
}
