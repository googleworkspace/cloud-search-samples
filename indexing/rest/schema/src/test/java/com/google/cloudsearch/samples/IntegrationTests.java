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

package com.google.cloudsearch.samples;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


/**
 * Simple integration tests for schema samples.
 * Requires two environment variables:
 * <p>
 * `CLOUDSEARCH_DATASOURCE_ID` - ID of a valid datasource
 * `GOOGLE_APPLICATION_CREDENTIALS` - path to service account credentials
 */
@RunWith(JUnit4.class)
public class IntegrationTests {
  private String dataSourceId;
  private String schemaFile;

  @Before
  public void setUp() {
    this.dataSourceId = System.getenv("CLOUDSEARCH_DATASOURCE_ID");
    this.schemaFile = "schema.json";
    assumeTrue(dataSourceId != null);
  }

  @After
  public void tearDown() {
    System.setOut(null);
  }

  private ByteArrayOutputStream captureOutput() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outputStream));
    return outputStream;
  }

  @Test
  public void testUpdateSchema() throws Exception {
    ByteArrayOutputStream outputStream = captureOutput();
    SchemaSample.main(new String[]{"update", "--datasource", dataSourceId});
    assertThat(outputStream.toString()).contains("Schema updated.");
  }

  @Test
  public void testDeleteSchema() throws Exception {
    ByteArrayOutputStream outputStream = captureOutput();
    SchemaSample.main(new String[]{"update", "--datasource", dataSourceId});
    SchemaSample.main(new String[]{"delete", "--datasource", dataSourceId});
    assertThat(outputStream.toString()).contains("Schema deleted.");
  }

  @Test
  public void testGetSchema() throws Exception {
    ByteArrayOutputStream outputStream = captureOutput();
    SchemaSample.main(new String[]{"update", "--datasource", dataSourceId});
    SchemaSample.main(new String[]{"get", "--datasource", dataSourceId});
    assertThat(outputStream.toString()).contains("objectDefinitions");
  }

}
