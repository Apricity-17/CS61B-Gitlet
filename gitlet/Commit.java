package gitlet;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;


/** Represents a gitlet commit object.
 * A Commit object contains a log message, a timestamp, a reference to its
 * parent commit(s), and a mapping of file names to blob references (hashes).
 * Each commit is identified by a SHA-1 hash of its combined metadata and content.
 *
 *  @author LiuShengxi
 */
public class Commit implements Serializable {
    /**List all instance variables of the Commit class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used.
     */

    /** The message of this Commit. */
    private final String message;
    /** The timestamp of this Commit. */
    private final Date timestamp;
    /** The SHA-1 hash of the parent commit. */
    private final LinkedList<String> parents;
    /** Map of filenames to Blob SHA-1 hashes. */
    private final TreeMap<String, String> blobId;

    /** Create commit */
    public Commit(String message, LinkedList<String> parents, TreeMap<String, String> blobId) {
        this.message = message;
        this.parents = parents;
        this.blobId = blobId;

        if (parents.isEmpty()) {
            //initial commit
            this.timestamp = new Date(0);
        } else {
            //common commit
            this.timestamp = new Date();
        }
    }

    /** convert timestamp to specific format. */
    public String dateToTimeStamp() {
        DateFormat dateFormat = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z", Locale.US);
        return dateFormat.format(timestamp);
    }

    /** get blobId. */
    public TreeMap<String, String> getBlobId() {
        return blobId;
    }

    /** get specific blob hash */
    public String getBlobHash(String filename) {
        return blobId.get(filename);
    }

    /** get parents */
    public LinkedList<String> getParentsSha() {
        return parents;
    }

    /** get message. */
    public String getMessage() {
        return message;
    }

    /** get parents. */
    public LinkedList<String> getParents() {
        return parents;
    }

    /** check if contains a file. */
    public boolean containsFile(String fileName) {
        return blobId != null && blobId.containsKey(fileName);
    }

    /** print this commit log. */
    public void logCommit(String actSha) {
        System.out.println("===");
        System.out.println("commit " + actSha);
        if (parents.size() > 1) {
            String p1 = parents.get(0).substring(0, 7);
            String p2 = parents.get(1).substring(0, 7);
            System.out.printf("Merge: %s %s%n",  p1, p2);
        }
        System.out.println("Date: " + dateToTimeStamp());
        System.out.println(message);
        System.out.println();
    }

}
