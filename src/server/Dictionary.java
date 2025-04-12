package server;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import util.Constants;
import util.Logger;

/**
 * Manages the dictionary data and operations.
 * Supports a human-readable dictionary format.
 */
public class Dictionary {
    private final ConcurrentHashMap<String, List<String>> dictionary = new ConcurrentHashMap<>();
    private File dictionaryFile = new File("resources/dictionary.txt");

    /**
     * Creates a new Dictionary and loads data from the specified file.
     *
     * @param dictionaryFilePath the file to load dictionary data from
     * @throws IOException if there's an error reading the file
     */
    public Dictionary(String dictionaryFilePath) throws IOException {
        this.dictionaryFile = findDictionaryFile(dictionaryFilePath);

        if (dictionaryFile.exists()) {
            loadDictionary();
        }
    }

    /**
     * Tries to find the dictionary file in several locations:
     * 1. Resources folder
     * 2. Absolute path
     * 3. Relative to current directory
     *
     * @param filePath the file path to search for
     * @return the File object if found, null otherwise
     */
    private File findDictionaryFile(String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            Logger.info("Using dictionary file from absolute path: " + file.getAbsolutePath());
            return file;
        }

        Logger.info("Dictionary file not found, will create a new one at: " + this.dictionaryFile.getAbsolutePath());
        return this.dictionaryFile;
    }

    /**
     * Loads dictionary data from the file.
     * Supports human-readable format with indentation for meanings.
     *
     * @throws IOException if there's an error reading the file
     */
    private void loadDictionary() throws IOException {
        if (!dictionaryFile.exists()) {
            return;
        }

        dictionary.clear();

        try (BufferedReader reader = new BufferedReader(new FileReader(dictionaryFile))) {
            String line;
            String currentWord = null;
            List<String> meanings = null;

            while ((line = reader.readLine()) != null) {
                // Skip empty lines
                if (line.trim().isEmpty()) {
                    continue;
                }

                // If line starts with indentation (spaces), it's a meaning
                if (line.startsWith("    ")) {
                    // If we have a current word, add this meaning to it
                    if (currentWord != null) {
                        String meaning = line.trim();
                        if (!meaning.isEmpty()) {
                            meanings.add(meaning);
                        }
                    }
                } else {
                    // If we have a word in progress, save it before starting a new one
                    if (currentWord != null && !meanings.isEmpty()) {
                        dictionary.put(currentWord, meanings);
                    }

                    // Start a new word
                    currentWord = line.trim();
                    meanings = new ArrayList<>();
                }
            }

            // Don't forget to add the last word
            if (currentWord != null && !meanings.isEmpty()) {
                dictionary.put(currentWord, meanings);
            }
        }

        Logger.info("Loaded " + dictionary.size() + " words from dictionary file: " + dictionaryFile.getName());

        // Debug output of loaded dictionary
        for (Map.Entry<String, List<String>> entry : dictionary.entrySet()) {
            Logger.debug("Word: " + entry.getKey() + ", Meanings: " + entry.getValue().size());
        }
    }

    /**
     * Saves the current dictionary data to the file in a human-readable format.
     *
     * @throws IOException if there's an error writing to the file
     */
    private synchronized void saveDictionary() throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(dictionaryFile))) {
            // Sort words alphabetically for better readability
            List<String> sortedWords = new ArrayList<>(dictionary.keySet());
            Collections.sort(sortedWords);

            boolean firstWord = true;
            for (String word : sortedWords) {
                List<String> meanings = dictionary.get(word);

                if (meanings.isEmpty()) continue;

                // Add a blank line between words (except before the first word)
                if (!firstWord) {
                    writer.newLine();
                }
                firstWord = false;

                // Write the word
                writer.write(word);
                writer.newLine();

                // Write each meaning with indentation
                for (String meaning : meanings) {
                    writer.write("    " + meaning);
                    writer.newLine();
                }
            }
        }

        Logger.info("Updated dictionary file " + dictionaryFile.getName() + " with " + dictionary.size() + " words");
    }

    /**
     * Searches for a word in the dictionary.
     *
     * @param word the word to search for
     * @return a list of meanings, or null if the word is not found
     */
    public synchronized List<String> search(String word) {
        Logger.debug("Searching for word: " + word);
        return dictionary.get(word);
    }

    /**
     * Adds a new word with its meanings to the dictionary.
     *
     * @param word the word to add
     * @param meanings the meanings of the word
     * @return true if the word was added, false if it already exists
     * @throws IOException if there's an error saving the dictionary
     */
    public synchronized boolean addWord(String word, String... meanings) throws IOException {
        Logger.debug("Adding word: " + word);

        if (dictionary.containsKey(word)) {
            return false; // Word already exists
        }

        if (meanings.length == 0) {
            return false; // Word must have at least one meaning
        }

        List<String> meaningsList = new ArrayList<>();
        for (String meaning : meanings) {
            meaningsList.add(meaning.trim());
        }

        dictionary.put(word, meaningsList);
        saveDictionary();
        return true;
    }

    /**
     * Removes a word from the dictionary.
     *
     * @param word the word to remove
     * @return true if the word was removed, false if it was not found
     * @throws IOException if there's an error saving the dictionary
     */
    public synchronized boolean removeWord(String word) throws IOException {
        Logger.debug("Removing word: " + word);

        if (!dictionary.containsKey(word)) {
            return false;
        }

        dictionary.remove(word);
        saveDictionary();
        return true;
    }

    /**
     * Adds a new meaning to an existing word.
     *
     * @param word the word to add a meaning to
     * @param meaning the meaning to add
     * @return true if the meaning was added, false if the word doesn't exist or the meaning already exists
     * @throws IOException if there's an error saving the dictionary
     */
    public synchronized boolean addMeaning(String word, String meaning) throws IOException {
        Logger.debug("Adding meaning to word: " + word);

        List<String> meanings = dictionary.get(word);
        if (meanings == null) {
            return false; // Word not found
        }

        meaning = meaning.trim();
        if (meanings.contains(meaning)) {
            return false; // Meaning already exists
        }

        meanings.add(meaning);
        saveDictionary();
        return true;
    }

    /**
     * Updates a meaning for an existing word.
     *
     * @param word the word to update a meaning for
     * @param oldMeaning the meaning to replace
     * @param newMeaning the new meaning
     * @return true if the meaning was updated, false if the word or old meaning doesn't exist
     * @throws IOException if there's an error saving the dictionary
     */
    public synchronized boolean updateMeaning(String word, String oldMeaning, String newMeaning) throws IOException {
        Logger.debug("Updating meaning for word: " + word);

        List<String> meanings = dictionary.get(word);
        if (meanings == null) {
            return false; // Word not found
        }

        oldMeaning = oldMeaning.trim();
        newMeaning = newMeaning.trim();

        int index = meanings.indexOf(oldMeaning);
        if (index == -1) {
            return false; // Old meaning not found
        }

        meanings.set(index, newMeaning);
        saveDictionary();
        return true;
    }

    /**
     * Gets the number of words in the dictionary.
     *
     * @return the number of words
     */
    public int size() {
        return dictionary.size();
    }
}