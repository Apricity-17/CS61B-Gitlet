package gitlet;

import java.io.File;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static gitlet.Utils.*;


/** Represents a gitlet repository.
 *  For files IO, cooperating with classes.
 *  does at a high level.
 *
 *  @author LiuShengxi
 */
public class Repository {
    /**
     *  List all instance variables of the Repository class here with a useful
      comment above them describing what that variable represents and how that
      variable is used.
     */

    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    /** The object directory. */
    public static final File OBJECT_DIR = join(GITLET_DIR, "objects");
    /** The heads directory */
    public static final File HEADS_DIR = join(GITLET_DIR, "refs", "heads");
    /** The remote directory. */
    public static final File REMOTES_DIR = join(GITLET_DIR, "refs", "remotes");
    /** The remote config. */
    public static final File REMOTE_FILE = join(GITLET_DIR, "refs", "remote");
    /** The head location. */
    public static final File HEAD_FILE = join(GITLET_DIR, "HEAD");
    /** The stage location. */
    public static final File STAGE_FILE = join(GITLET_DIR, "stage");

    /** HEAD. */
    private static String curBranch;
    /** current commit. */
    private static Commit headCommit;
    /** stage. */
    private static Stage stage;

    /** before args beginning, load variables. */
    private static void loadMetadata() {
        if (!GITLET_DIR.exists()) {
            throw error("Not in an initialized Gitlet directory.");
        }
        curBranch = readContentsAsString(HEAD_FILE);

        File branchFile = getBranchFile(curBranch);
        if (!branchFile.exists()) {
            throw error("Current branch file not found: " + curBranch);
        }

        String headCommitSha = readContentsAsString(branchFile);
        headCommit = readObject(shaToPath(headCommitSha), Commit.class);
        stage = readObject(STAGE_FILE, Stage.class);
    }

    /** init all directory, initial commit, master, head and stage.
     *  .gitlet
     *      |--objects
     *      |     |--commit and blob
     *      |--refs
     *      |    |--heads
     *      |         |--master
     *      |    |--remotes
     *      |         |--origin
     *      |              |--master
     *      |--HEAD
     *      |--stage
     *      |--remote_config
     */
    public static void init() {
        if (!GITLET_DIR.exists()) {
            GITLET_DIR.mkdirs();
        } else {
            throw error("A Gitlet version-control system already exists in the current directory.");
        }

        OBJECT_DIR.mkdirs();
        HEADS_DIR.mkdirs();
        REMOTES_DIR.mkdirs();

        // create initial commit
        LinkedList<String> parents = new LinkedList<>();
        TreeMap<String, String> blobID = new TreeMap<>();
        headCommit = new Commit("initial commit", parents, blobID);
        saveObject(headCommit);

        // create initial branch -- master
        String headCommitSha = generateSerObjHash(headCommit);
        createBranch("master", headCommitSha);

        // create head
        curBranch = "master";
        writeContents(HEAD_FILE, curBranch);

        // create stage
        stage = new Stage();
        writeObject(STAGE_FILE, stage);
    }

    /** create a new branch. */
    public static void createBranch(String branchName, String sha) {
        File file = join(HEADS_DIR, branchName);
        if (file.exists()) {
            throw error("A branch with that name already exists.");
        }
        writeContents(file, sha);
    }

    /** Adds a copy of the file as it currently exists to the staging area. */
    public static void add(String filename) {

        if (!GITLET_DIR.exists()) {
            throw error("Not in an initialized Gitlet directory.");
        }
        File file = join(CWD, filename);
        if (!file.exists()) {
            throw error("File does not exist.");
        }
        byte[] fileContent = Utils.readContents(file);

        // load head commit, stage, current branch.
        loadMetadata();

        String shaFile = Utils.sha1(fileContent);
        String headBlobHash = headCommit.getBlobId().get(filename);

        if (Objects.equals(headBlobHash, shaFile)) {
            stage.getAdded().remove(filename);
            stage.getRemoved().remove(filename);
        } else {
            Blob blob = new Blob(fileContent);
            saveObject(blob);
            stage.getAdded().put(filename, shaFile);
            stage.getRemoved().remove(filename);
        }

        writeObject(STAGE_FILE, stage);
    }

    /** Saves a snapshot of tracked files in the current commit. */
    public static void commit(String message) {
        loadMetadata();
        if (message == null || message.isBlank()) {
            throw error("Please enter a commit message.");
        }
        if (stage.isEmpty()) {
            throw error("No changes added to the commit.");
        }

        LinkedList<String> parents = new LinkedList<>();
        parents.add(generateSerObjHash(headCommit));
        TreeMap<String, String> blobID = new TreeMap<>(headCommit.getBlobId());
        blobID.putAll(stage.getAdded());
        for (String filename : stage.getRemoved()) {
            blobID.remove(filename);
        }
        Commit curCommit = new Commit(message, parents, blobID);
        String curCommitSha = generateSerObjHash(curCommit);

        headCommit = curCommit;
        saveObject(curCommit);

        stage.clear();
        writeObject(STAGE_FILE, stage);

        writeContents(join(HEADS_DIR, curBranch), curCommitSha);
    }

    /** Saves a snapshot of tracked files in the current commit for merge. */
    public static void commit(String message, String secondParent) {
        loadMetadata();
        if (message == null || message.isBlank()) {
            throw error("Please enter a commit message.");
        }
        if (stage.isEmpty()) {
            throw error("No changes added to the commit.");
        }

        LinkedList<String> parents = new LinkedList<>();
        parents.add(generateSerObjHash(headCommit));
        if (secondParent != null) {
            parents.add(secondParent);
        }
        TreeMap<String, String> blobID = new TreeMap<>(headCommit.getBlobId());
        blobID.putAll(stage.getAdded());
        for (String filename : stage.getRemoved()) {
            blobID.remove(filename);
        }
        Commit curCommit = new Commit(message, parents, blobID);
        String curCommitSha = generateSerObjHash(curCommit);

        headCommit = curCommit;
        saveObject(curCommit);

        stage.clear();
        writeObject(STAGE_FILE, stage);

        writeContents(join(HEADS_DIR, curBranch), curCommitSha);
    }

    /** Unstage the file if it is currently staged for addition. If the file is tracked in
     * the current commit, stage it for removal and remove the file from the working directory。
     */
    public static void rm(String filename) {
        loadMetadata();
        File file = join(CWD, filename);

        if (stage.getAdded().containsKey(filename)) {
            stage.getAdded().remove(filename);
        } else if (headCommit.containsFile(filename)) {
            if (file.exists()) {
                stage.getRemoved().add(filename);
                Utils.restrictedDelete(file);
            } else {
                stage.getRemoved().add(filename);
            }
        } else {
            throw error("No reason to remove the file.");
        }

        writeObject(STAGE_FILE, stage);
    }

    /** Starting at the current head commit, display information about each commit backwards
     *  along the commit tree until the initial commit.
     */
    public static void log() {
        loadMetadata();
        if (!OBJECT_DIR.exists()) {
            return;
        }

        Commit curCommit = headCommit;
        while (curCommit != null) {
            LinkedList<String> parents = curCommit.getParentsSha();
            String curCommitSha = generateSerObjHash(curCommit);
            curCommit.logCommit(curCommitSha);

            if (parents.isEmpty()) {
                curCommit = null;
            } else {
                curCommit = readObject(shaToPath(parents.get(0)), Commit.class);
            }
        }
    }

    /** Like log, except displays information about all commits ever made. */
    public static void globalLog() {
        loadMetadata();
        if (!OBJECT_DIR.exists()) {
            return;
        }

        File[] subDirs = OBJECT_DIR.listFiles(File::isDirectory);
        if (subDirs == null) {
            return;
        }

        for (File subDir : subDirs) {
            if (subDir.getName().length() != 2) {
                continue;
            }
            List<String> fileNames = Utils.plainFilenamesIn(subDir);
            for (String fileName : fileNames) {
                String fullSha = subDir.getName() + fileName;
                Object obj = readObject(shaToPath(fullSha), Serializable.class);
                if (obj instanceof Commit) {
                    ((Commit) obj).logCommit(fullSha);
                }
            }
        }
    }

    /** Prints out the ids of all commits that have the given commit message. */
    public static void find(String message) {
        loadMetadata();
        boolean haveCommit = false;

        File[] subDirs = OBJECT_DIR.listFiles(File::isDirectory);
        if (subDirs == null) {
            throw error("Found no commit with that message.");
        }
        for (File subDir : subDirs) {
            if (subDir.getName().length() != 2) {
                continue;
            }
            List<String> fileNames = Utils.plainFilenamesIn(subDir);
            for (String fileName : fileNames) {
                String fullSha = subDir.getName() + fileName;
                Object obj = readObject(shaToPath(fullSha), Serializable.class);

                if (obj instanceof Commit) {
                    if (Objects.equals(((Commit) obj).getMessage(), message)) {
                        System.out.println(fullSha);
                        haveCommit = true;
                    }
                }
            }
        }
        if (!haveCommit) {
            throw error("Found no commit with that message.");
        }
    }

    /** Displays what branches currently exist, and marks the current branch with a *.
     *  Also displays what files have been staged for addition or removal.
     */
    public static void status() {
        loadMetadata();
        // branches
        List<String> branches = Utils.plainFilenamesIn(HEADS_DIR);
        System.out.println("=== Branches ===");
        for (String branch : branches) {
            if (Objects.equals(branch, curBranch)) {
                System.out.println("*" + branch);
            } else {
                System.out.println(branch);
            }
        }
        System.out.println();
        // staged files
        System.out.println("=== Staged Files ===");
        Set<String> added = stage.getAdded().keySet();
        for (String filename : added) {
            System.out.println(filename);
        }
        System.out.println();
        // removed files
        System.out.println("=== Removed Files ===");
        Set<String> removed = stage.getRemoved();
        for (String filename : removed) {
            System.out.println(filename);
        }
        System.out.println();
        // Modifications Not Staged For Commit
        System.out.println("=== Modifications Not Staged For Commit ===");
        TreeMap<String, String> headCommitBlob = headCommit.getBlobId();
        TreeSet<String> allFiles = new TreeSet<>(headCommitBlob.keySet());
        allFiles.addAll(stage.getAdded().keySet());

        for (String filename : allFiles) {
            File file = join(CWD, filename);
            String headHash = headCommitBlob.get(filename);
            String stageHash = stage.getAdded().get(filename);

            if (stage.getRemoved().contains(filename)) {
                // removed file not belong to this
                continue;
            }
            if (!file.exists()) {
                // file not exist
                // 1. in head commit but not in stage.removed
                // 2. in stage.added but remove
                System.out.println(filename + " (deleted)");
            } else {
                if (file.isFile()) {
                    String workingHash = Utils.sha1(Utils.readContents(file));
                    // file changed
                    if (stageHash != null) {
                        // in stage.added but change
                        if (!stageHash.equals(workingHash)) {
                            System.out.println(filename + " (modified)");
                        }
                    } else if (headHash != null) {
                        // in head commit changed not add
                        if (!headHash.equals(workingHash)) {
                            System.out.println(filename + " (modified)");
                        }
                    }
                }
            }
        }
        System.out.println();
        // Untracked Files
        System.out.println("=== Untracked Files ===");
        List<String> filenames = Utils.plainFilenamesIn(CWD);
        for (String filename : filenames) {
            if (!stage.getAdded().containsKey(filename)) {
                if (!headCommitBlob.containsKey(filename)) {
                    System.out.println(filename);
                }
            }
        }
        System.out.println();
    }

    /** Takes the version of the file as it exists in the head commit and
     *  puts it in the working directory, overwriting the version of the file
     *  that’s already there if there is one.
     */
    public static void checkoutFile(String filename) {
        loadMetadata();
        String headCommitSha = generateSerObjHash(headCommit);
        checkoutFileFromCommit(headCommitSha, filename);
    }

    /** Takes the version of the file as it exists in the commit with the given id,
     * and puts it in the working directory, overwriting the version of the file
     * that’s already there if there is one.
     */
    public static void checkoutFileFromCommit(String commitId, String filename) {
        loadMetadata();

        Commit commit = getCommitFromId(commitId, OBJECT_DIR);
        String fileSha = commit.getBlobHash(filename);
        if (fileSha == null) {
            throw error("File does not exist in that commit.");
        }

        File file = shaToPath(fileSha);
        Blob blob = readObject(file, Blob.class);
        writeContents(join(CWD, filename), blob.getContent());
    }

    /** find commit from commit id. */
    private static Commit getCommitFromId(String commitId, File objectDir) {
        String fullId = null;
        int commitCount = 0;

        if (commitId.length() == 40) {
            fullId = commitId;
        } else {
            if (commitId.length() < 2) {
                throw error("Commit id must be at least 3 characters.");
            }
            String dirName = commitId.substring(0, 2);
            String prefix = commitId.substring(2);
            File file = join(objectDir, dirName);

            if (file.isDirectory()) {
                List<String> fileNames = Utils.plainFilenamesIn(file);
                for (String fileName : fileNames) {
                    if (fileName.startsWith(prefix)) {
                        fullId = dirName + fileName;
                        commitCount++;
                    }
                }
            }
        }
        if (fullId == null) {
            throw error("No commit with that id exists.");
        }
        if (commitCount > 1) {
            throw error("Please enter a commit id with more digits");
        }
        File commitFile = shaToPath(fullId);
        if (commitFile == null || !commitFile.exists()) {
            throw error("No commit with that id exists.");
        }
        return readObject(commitFile, Commit.class);
    }

    /** Takes all files in the commit at the head of the given branch, and puts them
     * in the working directory, overwriting the versions of the files
     * that are already there if they exist.
     */
    public static void checkoutBranch(String branch) {
        loadMetadata();

        // check branch
        if (Objects.equals(branch, curBranch)) {
            throw error("No need to checkout the current branch.");
        }

        File branchFile = getBranchFile(branch);

        if (!branchFile.exists()) {
            throw error("No such branch exists.");
        }

        // check untracked files
        String branchCommitHash = readContentsAsString(branchFile);
        Commit branchCommit = readObject(shaToPath(branchCommitHash), Commit.class);
        checkUntrackedFile(branchCommit);

        // write from branch commit and delete unnecessary files
        restoreFromCommit(branchCommit);

        // pointer adjustment
        curBranch = branch;
        writeContents(HEAD_FILE, curBranch);
        headCommit = branchCommit;
        stage.clear();
        writeObject(STAGE_FILE, stage);
    }

    /** find branch file from branch name. */
    private static File getBranchFile(String branchName) {
        File local = join(HEADS_DIR, branchName);
        if (local.exists()) {
            return local;
        }

        if (branchName.contains("/")) {
            String[] parts = branchName.split("/");
            return join(REMOTES_DIR, parts[0], parts[1]);
        }
        return local;
    }

    /** restore to commit time. */
    private static void restoreFromCommit(Commit commit) {
        List<String> fileNames = Utils.plainFilenamesIn(CWD);
        // 1. delete files in cwd but not in commit
        for (String filename : fileNames) {
            if (!commit.getBlobId().containsKey(filename)) {
                Utils.restrictedDelete(join(CWD, filename));
            }
        }

        // 2. restore files in commit
        for (String filename : commit.getBlobId().keySet()) {
            String blobSha = commit.getBlobId().get(filename);
            Blob blob = readObject(shaToPath(blobSha), Blob.class);
            File file = join(CWD, filename);

            if (!file.isDirectory()) {
                writeContents(file, blob.getContent());
            }
        }
    }

    /** check untracked files. */
    private static void checkUntrackedFile(Commit commit) {
        loadMetadata();

        List<String> filenames = Utils.plainFilenamesIn(CWD);
        TreeMap<String, String> headCommitBlob = headCommit.getBlobId();
        for (String filename : filenames) {
            boolean contains = headCommitBlob.containsKey(filename);
            boolean isUntracked = !stage.getAdded().containsKey(filename) && !contains;
            if (isUntracked) {
                String cwdSha = sha1(readContents(join(CWD, filename)));
                String commitSha = commit.getBlobId().get(filename);
                // untracked and will change
                if (!Objects.equals(commitSha, cwdSha)) {
                    throw error("There is an untracked file in the way; "
                            + "delete it, or add and commit it first.");
                }
            }
        }
    }

    /** Creates a new branch with the given name, and points it at the current head commit. */
    public static void branch(String branch) {
        loadMetadata();
        String headCommitSha = readContentsAsString(join(HEADS_DIR, curBranch));
        createBranch(branch, headCommitSha);
    }

    /** Deletes the branch with the given name. */
    public static void rmBranch(String branch) {
        loadMetadata();
        if (Objects.equals(branch, curBranch)) {
            throw error("Cannot remove the current branch.");
        }
        File file = join(HEADS_DIR, branch);
        if (!file.exists()) {
            throw error("A branch with that name does not exist.");
        } else {
            if (!file.delete()) {
                throw error("Cannot delete the current branch.");
            }
        }
    }

    /** Checks out all the files tracked by the given commit. */
    public static void reset(String commitId) {
        loadMetadata();
        Commit commit = getCommitFromId(commitId, OBJECT_DIR);
        checkUntrackedFile(commit);
        restoreFromCommit(commit);

        File file = join(HEADS_DIR, curBranch);
        headCommit = commit;
        writeContents(file, generateSerObjHash(commit));
        stage.clear();
        writeObject(STAGE_FILE, stage);
    }


    /** create chain directory for commit and blob. */
    public static File generateChainDir(File dir, String sha) {
        String prefix = sha.substring(0, 2);
        String rest = sha.substring(2);

        File subDir = new File(dir, prefix);
        if (!subDir.exists()) {
            subDir.mkdirs();
        }
        return Utils.join(subDir, rest);
    }

    /** generate sha hash for Serializable object（commit, blob). */
    public static String generateSerObjHash(Serializable obj) {
        byte[] content;
        if (obj.getClass().equals(Blob.class)) {
            // only use blob's content to get hash
            content = ((Blob) obj).getContent();
        } else {
            content = Utils.serialize(obj);
        }
        return Utils.sha1(content);
    }

    /** save commit or blob to a file. */
    public static void saveObject(Serializable obj) {
        String objSha = generateSerObjHash(obj);
        File f = generateChainDir(OBJECT_DIR, objSha);
        Utils.writeObject(f, obj);
    }

    /** load commit or blob from a file. */
    public static File shaToPath(String sha) {
        String prefix = sha.substring(0, 2);
        String rest = sha.substring(2);
        File f = Utils.join(OBJECT_DIR, prefix, rest);
        return f;
    }

    /** load commit or blob from a file in other object directory. */
    public static File shaToPath(String sha, File objDir) {
        String prefix = sha.substring(0, 2);
        String rest = sha.substring(2);
        File f = Utils.join(objDir, prefix, rest);
        return f;
    }



    /** Merges files from the given branch into the current branch. */
    public static void merge(String otherBranch) {
        loadMetadata();

        // check failure
        if (!stage.isEmpty()) {
            throw error("You have uncommitted changes.");
        }

        File file;
        if (!otherBranch.contains("/")) {
            file = join(HEADS_DIR, otherBranch);
        } else {
            String[] parts = otherBranch.split("/");
            String rName = parts[0];
            String bName = parts[1];
            file = join(REMOTES_DIR, rName, bName);
        }
        if (!file.exists()) {
            throw error("A branch with that name does not exist.");
        }
        if (Objects.equals(otherBranch, curBranch)) {
            throw error("Cannot merge a branch with itself.");
        }

        String headCommitSha = readContentsAsString(join(HEADS_DIR, curBranch));
        String sha = readContentsAsString(file);
        Commit otherCommit = getCommitFromId(sha, OBJECT_DIR);
        Commit splitPoint = getSplitPoint(headCommitSha, sha);
        String splitPointSha = generateSerObjHash(splitPoint);

        if (Objects.equals(splitPointSha, headCommitSha)) {
            checkUntrackedFile(otherCommit);
            restoreFromCommit(otherCommit);
            System.out.println("Current branch fast-forwarded.");
            headCommit = otherCommit;
            writeContents(join(HEADS_DIR, curBranch), sha);
            return;
        }

        if (Objects.equals(splitPointSha, sha)) {
            throw error("Given branch is an ancestor of the current branch.");
        }

        // process all the files in three commits
        Map<String, String> splitBlobs = splitPoint.getBlobId();
        Map<String, String> headBlobs = headCommit.getBlobId();
        Map<String, String> otherBlobs = otherCommit.getBlobId();

        Set<String> allFiles = new HashSet<>(splitBlobs.keySet());
        allFiles.addAll(headBlobs.keySet());
        allFiles.addAll(otherBlobs.keySet());
        LinkedList<Boolean> isConflict = new LinkedList<>();

        checkUntrackedFile(otherCommit);
        for (String fileName : allFiles) {
            String s = splitBlobs.get(fileName);
            String h = headBlobs.get(fileName);
            String o = otherBlobs.get(fileName);

            isConflict.add(processFile(fileName, otherCommit, s, h, o));
        }
        if (isConflict.contains(true)) {
            System.out.println("Encountered a merge conflict.");
        }

        // new a merge commit
        String msg = "Merged " + otherBranch + " into " + curBranch + ".";
        commit(msg, sha);
    }

    /** get split point. */
    private static Commit getSplitPoint(String sha1, String sha2) {
        Map<String, Integer> sha1Ans = getAnsShaWithDist(sha1);
        Map<String, Integer> sha2Ans = getAnsShaWithDist(sha2);
        String minSha = null;
        int minDist = -1;

        for (String sha : sha1Ans.keySet()) {
            if (sha2Ans.containsKey(sha)) {
                if (minSha == null && minDist == -1) {
                    minSha = sha;
                    minDist = sha1Ans.get(sha);
                } else if (minDist > -1 && sha1Ans.get(sha) < minDist) {
                    minDist = sha1Ans.get(sha);
                    minSha = sha;
                } else if (sha1Ans.get(sha) == minDist) {
                    if (sha.compareTo(minSha) < 0) {
                        minSha = sha;
                    }
                }
            }
        }
        return getCommitFromId(minSha, OBJECT_DIR);
    }

    /** use bfs to find all the ancestors and its depth to start. */
    private static Map<String, Integer> getAnsShaWithDist(String startCommitSha) {
        HashSet<String> visited = new HashSet<>();
        Map<String, Integer> distances = new HashMap<>();
        Queue<String> queue = new LinkedList<>();

        queue.add(startCommitSha);
        visited.add(startCommitSha);
        distances.put(startCommitSha, 0);

        while (!queue.isEmpty()) {
            String curSha = queue.poll();
            int dist = distances.get(curSha);

            Commit commit = getCommitFromId(curSha, OBJECT_DIR);
            for (String p : commit.getParents()) {
                if (!visited.contains(p)) {
                    queue.add(p);
                    visited.add(p);
                    distances.put(p, dist + 1);
                }
            }
        }
        return distances;
    }

    /** addess eight conditions in merge. */
    private static boolean processFile(String name, Commit other, String s, String h, String o) {
        String otherCommitSha = generateSerObjHash(other);

        // modified in other
        if (!Objects.equals(s, o)) {
            // unmodified in head
            if (Objects.equals(s, h)) {
                // not present in other -> case 6 -> remove
                if (o == null) {
                    rm(name);
                // not in split (and head) -> case 5 -> other
                } else if (s == null) {
                    checkoutFileFromCommit(otherCommitSha, name);
                    add(name);
                // modified in other but not head -> case 1 -> other
                } else {
                    checkoutFileFromCommit(otherCommitSha, name);
                    add(name);
                }
                return false;
            // modified in head
            } else {
                if (Objects.equals(o, h)) {
                    // modified in same way -> case 3.1 -> same
                    return false;
                } else {
                    // modified in different way -> case 3.2 -> conflict
                    handleConflict(name, h, o);
                    return true;
                }
            }
        // unmodified in other
        } else {
            // modified in head
            // not present in head -> case 7 -> remain removed
            // not in split (and other) -> case 4 -> head
            // modified in head but not in other -> case 2 -> head
            return false;
        }
    }

    /** handle conflict. */
    private static void handleConflict(String fileName, String headBlobSha, String otherBlobSha) {
        loadMetadata();
        String headContent;
        String otherContent;
        if (headBlobSha == null) {
            headContent = "";
        } else {
            byte[] headContents = readObject(shaToPath(headBlobSha), Blob.class).getContent();
            headContent = new String(headContents, StandardCharsets.UTF_8);
        }
        if (otherBlobSha == null) {
            otherContent = "";
        } else {
            byte[] otherContents = readObject(shaToPath(otherBlobSha), Blob.class).getContent();
            otherContent = new String(otherContents, StandardCharsets.UTF_8);
        }

        String conflictContent = "<<<<<<< HEAD\n"
                + headContent
                + "=======\n"
                + otherContent
                + ">>>>>>>\n";
        writeContents(join(CWD, fileName), conflictContent);
        add(fileName);
    }

    /** Saves the given login information under the given remote name. */
    public static void addRemote(String remoteName, String remotePath) {
        if (!GITLET_DIR.exists()) {
            throw error("Not in an initialized Gitlet directory.");
        }

        TreeMap<String, String> remote;

        if (REMOTE_FILE.exists()) {
            @SuppressWarnings("unchecked")
            TreeMap<String, String> temp = readObject(REMOTE_FILE, TreeMap.class);
            remote = temp;
        } else {
            remote = new TreeMap<>();
        }

        if (remote.containsKey(remoteName)) {
            throw error("A remote with that name already exists.");
        }
        remotePath = normalizePath(remotePath);
        remote.put(remoteName, remotePath);

        writeObject(REMOTE_FILE, remote);
    }

    /** Remove information associated with the given remote name. */
    public static void rmRemote(String remoteName) {
        if (!GITLET_DIR.exists()) {
            throw error("Not in an initialized Gitlet directory.");
        }
        @SuppressWarnings("unchecked")
        TreeMap<String, String> remote = readObject(REMOTE_FILE, TreeMap.class);

        if (!remote.containsKey(remoteName)) {
            throw error("A remote with that name does not exist.");
        }
        remote.remove(remoteName);
        writeObject(REMOTE_FILE, remote);
    }

    /** Attempts to append the current branch’s commits
     *  to the end of the given branch at the given remote.
     */
    public static void push(String remoteName, String remoteBranchName) {
        loadMetadata();
        String headCommitSha = generateSerObjHash(headCommit);

        @SuppressWarnings("unchecked")
        TreeMap<String, String> remote = readObject(REMOTE_FILE, TreeMap.class);
        String remotePath = remote.get(remoteName);
        File remoteGitlet = new File(remotePath);
        if (!remoteGitlet.exists()) {
            throw error("Remote directory not found.");
        }

        File remoteBranchFile = join(remoteGitlet, "refs", "heads", remoteBranchName);
        if (!remoteBranchFile.exists()) {
            remoteBranchFile.mkdirs();
        }
        File remoteObjectDir = join(remoteGitlet, "objects");
        String remoteCommitSha = readContentsAsString(remoteBranchFile);

        // use bfs to judge if remote head commit is head commit's ancestor.
        if (!isAncestor(headCommitSha, remoteCommitSha, OBJECT_DIR)) {
            throw error("Please pull down remote changes before pushing.");
        } else {
            // copy objects to remote
            syncObjects(OBJECT_DIR, remoteObjectDir, headCommitSha, remoteCommitSha);
            writeContents(remoteBranchFile, headCommitSha);
        }
    }

    /** Brings down commits from the remote Gitlet repository into the local Gitlet repository. */
    public static void fetch(String remoteName, String remoteBranchName) {
        loadMetadata();
        String headCommitSha = generateSerObjHash(headCommit);

        @SuppressWarnings("unchecked")
        TreeMap<String, String> remote = readObject(REMOTE_FILE, TreeMap.class);
        String remotePath = remote.get(remoteName);
        File remoteGitlet = new File(remotePath);
        if (!remoteGitlet.exists()) {
            throw error("Remote directory not found.");
        }

        File remoteBranchFile = join(remoteGitlet, "refs", "heads", remoteBranchName);
        if (!remoteBranchFile.exists()) {
            throw error("That remote does not have that branch.");
        }
        File remoteObjectDir = join(remoteGitlet, "objects");
        String remoteCommitSha = readContentsAsString(remoteBranchFile);

        File originRemote = join(REMOTES_DIR, remoteName);
        if (!originRemote.exists()) {
            originRemote.mkdirs();
        }
        File originRemoteBranch = join(originRemote, remoteBranchName);

        syncObjects(remoteObjectDir, OBJECT_DIR, remoteCommitSha, headCommitSha);
        writeContents(originRemoteBranch, remoteCommitSha);
    }

    /** Fetches branch [remote name]/[remote branch name] as for the fetch command,
     * and then merges that fetch into the current branch.
     */
    public static void pull(String remoteName, String remoteBranchName) {
        fetch(remoteName, remoteBranchName);
        merge(remoteName + "/" + remoteBranchName);
    }

    /** translate "/", "\". */
    public static String normalizePath(String path) {
        return path.replace("/", File.separator);
    }

    /** judge use bfs to judge if remote head commit is head commit's ancestor
     *  and when arguments opposite the judgment opposite.
     */
    private static Boolean isAncestor(String headSha, String remoteCommitSha, File objectDir) {
        HashSet<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();

        queue.add(headSha);
        visited.add(headSha);

        while (!queue.isEmpty()) {
            String curSha = queue.poll();
            if (Objects.equals(curSha, remoteCommitSha)) {
                return true;
            }
            Commit commit = getCommitFromId(curSha, objectDir);
            for (String p : commit.getParents()) {
                if (!visited.contains(p)) {
                    queue.add(p);
                    visited.add(p);
                }
            }
        }
        return false;
    }

    /** 把 sourceDir 的 commit 和 blob 复制到 targetDir。使用dfs。
     *
     * @param sourceDir 读取对象的 object路径
     * @param targetDir 写入对象的 object 路径
     * @param startSha 遍历的起点
     * @param endSha 遍历的终点（遇到它停止）
     */
    public static void syncObjects(File sourceDir, File targetDir, String startSha, String endSha) {
        Deque<String> stack = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        stack.push(startSha);

        while (!stack.isEmpty()) {
            String curSha = stack.pop();

            if (Objects.equals(curSha, endSha) || visited.contains(curSha)) {
                continue;
            }
            visited.add(curSha);

            // copy object
            String prefix = curSha.substring(0, 2);
            String rest = curSha.substring(2);

            File targetShaDir = join(targetDir, prefix);
            if (!targetShaDir.exists()) {
                targetShaDir.mkdirs();
            }
            File targetObject = join(targetShaDir, rest);
            if (!targetObject.exists()) {
                File path = shaToPath(curSha, sourceDir);
                Serializable object = readObject(path, Serializable.class);
                writeObject(targetObject, object);
            }

            // if is commit, process its parents and blobs
            Serializable object = readObject(shaToPath(curSha, sourceDir), Serializable.class);
            if (object instanceof Commit) {
                Commit commit = (Commit) object;

                for (String blobSha : commit.getBlobId().values()) {
                    if (!visited.contains(blobSha)) {
                        stack.push(blobSha);
                    }
                }
                for (String parentSha : commit.getParentsSha()) {
                    if (!visited.contains(parentSha) && parentSha != null) {
                        stack.push(parentSha);
                    }
                }
            }
        }
    }
}
