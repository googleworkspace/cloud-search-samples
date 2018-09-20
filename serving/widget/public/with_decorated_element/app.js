/**
 * Load the cloud search widget & auth libraries. Runs after
 * the initial gapi bootstrap library is ready.
 */
function onLoad() {
  gapi.load('client:auth2:cloudsearch-widget', initializeApp)
}

/**
 * Search box adapter that decorates suggestion elements by
 * adding a custom CSS class.
 */
function SearchBoxAdapter() {}
SearchBoxAdapter.prototype.decorateSuggestionElement = function(element) {
  element.classList.add('my-suggestion');
}

/**
 * Results container adapter that decorates suggestion elements by
 * adding a custom CSS class.
 */
function ResultsContainerAdapter() {}
ResultsContainerAdapter.prototype.decorateSearchResultElement = function(element) {
  element.classList.add('my-result');
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

    // Build the result container and bind to DOM elements.
    var resultsContainer = new gapi.cloudsearch.widget.resultscontainer.Builder()
      .setSearchApplicationId(searchConfig.searchAppId)
      .setAdapter(new ResultsContainerAdapter())
      .setSearchResultsContainerElement(document.getElementById('search_results'))
      .setFacetResultsContainerElement(document.getElementById('facet_results'))
      .build();

    // Build the search box and bind to DOM elements.
    var searchBox = new gapi.cloudsearch.widget.searchbox.Builder()
      .setSearchApplicationId(searchConfig.searchAppId)
      .setAdapter(new SearchBoxAdapter())
      .setInput(document.getElementById('search_input'))
      .setAnchor(document.getElementById('suggestions_anchor'))
      .setResultsContainer(resultsContainer)
      .build();
    return searchConfig;
  }).then(function(searchConfig) {
    // Init API/oauth client w/client ID.
    return gapi.auth2.init({
        'clientId': searchConfig.clientId,
        'scope': 'https://www.googleapis.com/auth/cloud_search.query'
    });
  });
}
