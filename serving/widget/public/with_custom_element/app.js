/**
 * Load the cloud search widget & auth libraries. Runs after
 * the initial gapi bootstrap library is ready.
 */
function onLoad() {
  gapi.load('client:auth2:cloudsearch-widget', initializeApp)
}

/**
 * Search box adapter that overrides creation of suggestion elements.
 */
function SearchBoxAdapter() {}
SearchBoxAdapter.prototype.createSuggestionElement = function(suggestion) {
  let template = document.querySelector('#suggestion_template');
  let fragment = document.importNode(template.content, true);
  fragment.querySelector('.suggested_query').textContent = suggestion.suggestedQuery;
  return fragment.firstElementChild;
}

/**
 * Results container adapter that overrides creation of result elements.
 */
function ResultsContainerAdapter() {}
ResultsContainerAdapter.prototype.createSearchResultElement = function(result) {
  let template = document.querySelector('#result_template');
  let fragment = document.importNode(template.content, true);
  fragment.querySelector('.title').textContent = result.title;
  fragment.querySelector('.title').href = result.url;
  let snippetText = result.snippet != null ?
    result.snippet.snippet : '';
  fragment.querySelector('.query_snippet').textContent = snippetText;
  return fragment.firstElementChild;
}

/**
 * Initialize the app after loading the Google API client &
 * Cloud Search widget.
 */
async function initializeApp() {
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
