package server;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import util.Constants;
import util.Logger;

/**
 * Manages the dictionary data and operations.
 */
public class Dictionary {
    private final ConcurrentHashMap<String, List<String>> dictionary = new ConcurrentHashMap<>();
    private final String dictionaryFile;

    /**
     * Creates a new Dictionary and loads data from the specified file.
     *
     * @param dictionaryFile the file to load dictionary data from
     * @throws IOException if there's an error reading the file
     */
    public Dictionary(String dictionaryFile) throws IOException {
        this.dictionaryFile = dictionaryFile;
        loadDictionary();
    }

    /**
     * Loads dictionary data from the file.
     *
     * @throws IOException if there's an error reading the file
     */
    private void loadDictionary() throws IOException {
        File file = new File(dictionaryFile);

        // Create an empty dictionary file if it doesn't exist
        if (!file.exists()) {
            file.createNewFile();
            Logger.info("Created new dictionary file: " + dictionaryFile);
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                String[] parts = line.split(Constants.WORD_DELIMITER, 2);
                if (parts.length < 2) continue;

                String word = parts[0].trim();
                String[] meanings = parts[1].split(Constants.WORD_DELIMITER);

                List<String> meaningsList = new ArrayList<>();
                for (String meaning : meanings) {
                    String trimmedMeaning = meaning.trim();
                    if (!trimmedMeaning.isEmpty()) {
                        meaningsList.add(trimmedMeaning);
                    }
                }

                if (!meaningsList.isEmpty()) {
                    dictionary.put(word, meaningsList);
                }
            }
        }

        Logger.info("Loaded " + dictionary.size() + " words from dictionary file");
    }

    /**
     * Saves the current dictionary data to the file.
     *
     * @throws IOException if there's an error writing to the file
     */
    private synchronized void saveDictionary() throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(dictionaryFile))) {
            for (Map.Entry<String, List<String>> entry : dictionary.entrySet()) {
                String word = entry.getKey();
                List<String> meanings = entry.getValue();

                if (meanings.isEmpty()) continue;

                StringBuilder line = new StringBuilder(word);
                for (String meaning : meanings) {
                    line.append(Constants.WORD_SEPARATOR).append(meaning);
                }

                writer.write(line.toString());
                writer.newLine();
            }
        }

        Logger.info("Saved " + dictionary.size() + " words to dictionary file");
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
            return false; // Word not found
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