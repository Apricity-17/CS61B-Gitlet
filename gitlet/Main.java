package gitlet;


import java.io.File;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author LiuShengxi
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     * Runs one of 20 commands (ARGS):
     * init -- Creates a new Gitlet version-control system in the current directory.
     *
     * add [file name] -- Adds a copy of the file as it currently exists to the staging area.
     *
     * commit [message] -- Saves a snapshot of tracked files in the current commit.
     *
     * rm [file name] -- Unstage the file if it is currently staged for addition. If the file is tracked in the current commit,
     *                    stage it for removal and remove the file from the working directory。
     *
     * log -- Starting at the current head commit, display information about each commit backwards along the commit tree until the initial commit.
     *
     * global-log -- Like log, except displays information about all commits ever made.
     *
     * find [commit message] -- Prints out the ids of all commits that have the given commit message.
     *
     * status -- Displays what branches currently exist, and marks the current branch with a *. Also displays what files have been staged for addition or removal.
     *
     * checkout -- [file name] -- Takes the version of the file as it exists in the head commit and puts it in the working directory, overwriting the version of the file that’s already there if there is one.
     *
     * checkout [commit id] -- [file name] -- Takes the version of the file as it exists in the commit with the given id, and puts it in the working directory,
     *                                        overwriting the version of the file that’s already there if there is one.
     *
     * checkout [branch name] -- Takes all files in the commit at the head of the given branch, and puts them in the working directory, overwriting the versions of the files that are already there if they exist.
     *
     * branch [branch name] -- Creates a new branch with the given name, and points it at the current head commit.
     *
     * rm-branch [branch name] -- Deletes the branch with the given name.
     *
     * reset [commit id] -- Checks out all the files tracked by the given commit.
     *
     * merge [branch name] -- Merges files from the given branch into the current branch.
     *
     * add-remote [remote name] [name of remote directory]/.gitlet -- Saves the given login information under the given remote name.
     *
     * rm-remote [remote name] -- Remove information associated with the given remote name.
     *
     * push [remote name] [remote branch name] -- Attempts to append the current branch’s commits to the end of the given branch at the given remote.
     *
     * fetch [remote name] [remote branch name] -- Brings down commits from the remote Gitlet repository into the local Gitlet repository.
     *
     * pull [remote name] [remote branch name] -- Fetches branch [remote name]/[remote branch name] as for the fetch command, and then merges that fetch into the current branch.
     *
     * All persistent data should be stored in a ".gitlet"
     * directory in the current working directory.
     *
     * MY PROGRAM SHOULD CREATE THESE FOLDERS/FILES*
     *
     *
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
    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                throw Utils.error("Please enter a command.");
            }

            String firstArg = args[0];
            if (!firstArg.equals("init") && !new File(".gitlet").exists()) {
                System.out.println("Not in an initialized Gitlet directory.");
                return;
            }
            switch (firstArg) {
                case "init":
                    if (args.length != 1) {
                        throw Utils.error("Incorrect operands.");
                    }
                    Repository.init();
                    break;
                case "add":
                    if (args.length != 2) {
                        throw Utils.error("Incorrect operands.");
                    }
                    Repository.add(args[1]);
                    break;
                case "commit":
                    if (args.length != 2) {
                        throw Utils.error("Please enter a commit message.");
                    }
                    Repository.commit(args[1]);
                    break;
                case "rm":
                    if (args.length != 2) {
                        throw Utils.error("Incorrect operands.");
                    }
                    Repository.rm(args[1]);
                    break;
                case "log":
                    if (args.length != 1) {
                        throw Utils.error("Incorrect operands.");
                    }
                    Repository.log();
                    break;
                case "global-log":
                    if (args.length != 1) {
                        throw Utils.error("Incorrect operands.");
                    }
                    Repository.globalLog();
                    break;
                case "find":
                    if (args.length != 2) {
                        throw Utils.error("Incorrect operands.");
                    }
                    Repository.find(args[1]);
                    break;
                case "status":
                    if (args.length != 1) {
                        throw Utils.error("Incorrect operands.");
                    }
                    Repository.status();
                    break;
                case "checkout":
                    if (args.length == 3 && args[1].equals("--")) {
                        // checkout -- [file name]
                        Repository.checkoutFile(args[2]);
                    } else if (args.length == 4 && args[2].equals("--")) {
                        // checkout [commit id] -- [file name]
                        Repository.checkoutFileFromCommit(args[1], args[3]);
                    } else if (args.length == 2) {
                        // checkout [branch name]
                        Repository.checkoutBranch(args[1]);
                    } else {
                        throw Utils.error("Incorrect operands.");
                    }
                    break;
                case "branch":
                    if (args.length != 2) {
                        throw Utils.error("Incorrect operands.");
                    }
                    Repository.branch(args[1]);
                    break;
                case "rm-branch":
                    if (args.length != 2) {
                        throw Utils.error("Incorrect operands.");
                    }
                    Repository.rmBranch(args[1]);
                    break;
                case "reset":
                    if (args.length != 2) {
                        throw Utils.error("Incorrect operands.");
                    }
                    Repository.reset(args[1]);
                    break;
                case "merge":
                    if (args.length != 2) {
                        throw Utils.error("Incorrect operands.");
                    }
                    Repository.merge(args[1]);
                    break;
                case "add-remote":
                    if (args.length != 3) {
                        throw Utils.error("Incorrect operands.");
                    }
                    Repository.addRemote(args[1], args[2]);
                    break;
                case "rm-remote":
                    if (args.length != 2) {
                        throw Utils.error("Incorrect operands.");
                    }
                    Repository.rmRemote(args[1]);
                    break;
                case "push":
                    if (args.length != 3) {
                        throw Utils.error("Incorrect operands.");
                    }
                    Repository.push(args[1], args[2]);
                    break;
                case "fetch":
                    if (args.length != 3) {
                        throw Utils.error("Incorrect operands.");
                    }
                    Repository.fetch(args[1], args[2]);
                    break;
                case "pull":
                    if (args.length != 3) {
                        throw Utils.error("Incorrect operands.");
                    }
                    Repository.pull(args[1], args[2]);
                    break;
                default:
                    System.out.print("No command with that name exists.\n");
                    System.out.flush();
                    System.exit(0);
            }
        } catch (GitletException e) {
            System.out.println(e.getMessage());
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
}
