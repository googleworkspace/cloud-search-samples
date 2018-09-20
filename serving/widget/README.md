# Updating schemas

[![Open in Cloud Shell][cloudshell-badge]][cloudshell-open]

[Google Cloud Search][cloud-search] allows enterprises to index and search
for information across a wide variety of data sources. These samples
illustrate building a custom search interface using the
[search widget][widget-guide].

## Set up the samples

1. [Set up a project][project-setup] for Cloud Search
1. [Create a client ID][create-client-id] for the sample.
   1. Use the origin `http://localhost:8080` when creating the client ID.
1. Edit `public/config.json` and replace `[client-id]` with the client ID
   you created.
1. Run the command to install dependencies:
   ```sh
   npm Install
   ```

### Run the sample

To start the server, run:

```sh
npm run start
```

Open the url `http://localhost:8080` in your browser and try each
of the samples.

[cloudshell-badge]: http://gstatic.com/cloudssh/images/open-btn.png
[cloudshell-open]: https://console.cloud.google.com/cloudshell/open?git_repo=https://github.com/gsuitedevs/cloud-search-samples&page=editor&open_in_editor=serving/widget/README.md
[cloud-search]: https://developers.google.com/cloud-search/
[project-setup]: https://developers.google.com/cloud-search/docs/guides/project-setup
[maven-install]: http://maven.apache.org/install.html
[widget-guide]: https://developers.google.com/cloud-search/docs/guides/search-widget
[create-client-id]: https://developers.google.com/cloud-search/docs/guides/search-widget#generate_a_client_id_for_the_application
