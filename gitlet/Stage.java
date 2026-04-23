package gitlet;

import java.io.Serializable;
import java.util.TreeMap;
import java.util.TreeSet;

public class Stage implements Serializable {
    /** added files, name -> blob id. */
    private TreeMap<String, String> added = new TreeMap<>();
    /** removed files, name. */
    private TreeSet<String> removed = new TreeSet<>();

    /** new a stage. */
    public Stage() {
        return;
    }

    /** judge if stage is empty. */
    public boolean isEmpty() {
        return added.isEmpty() && removed.isEmpty();
    }

    /** clear stage. */
    public void clear() {
        added.clear();
        removed.clear();
    }

    /** get added. */
    public TreeMap<String, String> getAdded() {
        return added;
    }

    /** get removed. */
    public TreeSet<String> getRemoved() {
        return removed;
    }
}
