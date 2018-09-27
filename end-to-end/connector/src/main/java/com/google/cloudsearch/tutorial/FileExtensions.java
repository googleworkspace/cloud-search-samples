package com.google.cloudsearch.tutorial;

import com.google.common.io.Files;

import java.util.HashMap;
import java.util.Map;

/**
 * Very naive utility class for detecting programming languages for
 * GitHub content. This is not intended to be an exhaustive list,
 * rather is provided to show the use of structured data and
 * faceting in Cloud Search.
 */
public class FileExtensions {
  private static final String DEFAULT_LANGUAGE = "Other";

  private static Map<String, String> extensionsToLanguage = new HashMap<>();

  static {
    extensionsToLanguage.put("c", "C/C++");
    extensionsToLanguage.put("cpp", "C/C++");
    extensionsToLanguage.put("h", "C/C++");
    extensionsToLanguage.put("json", "JSON");
    extensionsToLanguage.put("js", "JavaScript");
    extensionsToLanguage.put("gs", "JavaScript");
    extensionsToLanguage.put("java", "Java");
    extensionsToLanguage.put("py", "Python");
    extensionsToLanguage.put("md", "Markdown");
    extensionsToLanguage.put("yaml", "YAML");
    extensionsToLanguage.put("php", "PHP");
    extensionsToLanguage.put("go", "Go");
    extensionsToLanguage.put("rb", "Ruby");
    extensionsToLanguage.put("m", "Objective-C");
    extensionsToLanguage.put("swift", "Swift");
    extensionsToLanguage.put("css", "CSS");
  }

  /**
   * Retrieve the descriptive file type given a file name. Attempts
   * to detect the programming language based on extension.
   *
   * @param name file name
   * @return Expected programming language
   */
  static String getLanguageForFile(String name) {
    String extension = Files.getFileExtension(name).toLowerCase();
    return extensionsToLanguage.getOrDefault(extension, DEFAULT_LANGUAGE);
  }
}
